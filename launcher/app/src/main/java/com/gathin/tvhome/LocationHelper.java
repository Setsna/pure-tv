package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Where the weather is for. In order: the {@code weather_location} override in config.xml
 * ("lat,lon,City" for viewers whose ISP makes IP lookup land in the wrong city), a location found
 * within the last day, a fresh IP lookup (ipapi.co, or ipwho.is when that fails; no key), any older cached location. With none of
 * these the callback gets {@code onError} and the weather simply isn't shown.
 */
public class LocationHelper {
    private static final String PREFS_NAME = "location_cache";
    private static final String KEY_LAT = "cached_lat";
    private static final String KEY_LON = "cached_lon";
    private static final String KEY_CITY = "cached_city";
    private static final String KEY_TIME = "cached_at";
    private static final long FRESH_MS = 24 * 60 * 60 * 1000L;

    public interface LocationCallback {
        void onLocation(double lat, double lon);
        void onError();
    }

    /** The configured or last detected city name, or "" when unknown. */
    public static String cityName(Context ctx) {
        double[] override = parseOverride(ctx.getString(R.string.weather_location));
        if (override != null) return overrideCity(ctx.getString(R.string.weather_location));
        return prefs(ctx).getString(KEY_CITY, "");
    }

    public static void detectLocation(Context ctx, LocationCallback cb) {
        if (ctx == null) {
            cb.onError();
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        double[] override = parseOverride(ctx.getString(R.string.weather_location));
        if (override != null) {
            main.post(() -> cb.onLocation(override[0], override[1]));
            return;
        }

        SharedPreferences prefs = prefs(ctx);
        boolean hasCache = prefs.contains(KEY_LAT) && prefs.contains(KEY_LON);
        double cachedLat = prefs.getFloat(KEY_LAT, 0f);
        double cachedLon = prefs.getFloat(KEY_LON, 0f);
        if (hasCache && System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0L) < FRESH_MS) {
            main.post(() -> cb.onLocation(cachedLat, cachedLon));
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json;
                try {
                    json = fetchJson("https://ipapi.co/json/");
                } catch (Exception e) {
                    // ipapi.co answers 429 to whole ISPs at times; ipwho.is is the same kind of lookup.
                    json = fetchJson("https://ipwho.is/");
                    if (!json.optBoolean("success", false)) throw new Exception("ipwho.is: no location");
                }
                double lat = json.getDouble("latitude");
                double lon = json.getDouble("longitude");
                prefs.edit()
                     .putFloat(KEY_LAT, (float) lat)
                     .putFloat(KEY_LON, (float) lon)
                     .putString(KEY_CITY, json.optString("city", ""))
                     .putLong(KEY_TIME, System.currentTimeMillis())
                     .apply();
                main.post(() -> cb.onLocation(lat, lon));
            } catch (Exception e) {
                if (hasCache) main.post(() -> cb.onLocation(cachedLat, cachedLon));
                else main.post(cb::onError);
            }
        }).start();
    }

    private static JSONObject fetchJson(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return new JSONObject(sb.toString());
    }

    /** "lat,lon[,City]" → {lat, lon}, or null when empty or malformed. */
    static double[] parseOverride(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String[] parts = value.split(",");
        if (parts.length < 2) return null;
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            return new double[] {lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String overrideCity(String value) {
        String[] parts = value.split(",", 3);
        return parts.length == 3 ? parts[2].trim() : "";
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
