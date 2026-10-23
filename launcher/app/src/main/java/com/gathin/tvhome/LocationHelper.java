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
 * IP-based automatic geolocation helper for Android TV.
 * Uses ipapi.co free tier with fallback to SharedPreferences and Delhi coordinates.
 */
public class LocationHelper {
    private static final String PREFS_NAME = "location_cache";
    private static final String KEY_LAT = "cached_lat";
    private static final String KEY_LON = "cached_lon";

    public static final double DEFAULT_LAT = 28.5983;
    public static final double DEFAULT_LON = 77.1897;

    public interface LocationCallback {
        void onLocation(double lat, double lon);
        void onError();
    }

    public static void detectLocation(Context ctx, LocationCallback cb) {
        if (ctx == null) {
            cb.onError();
            return;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        double cachedLat = prefs.getFloat(KEY_LAT, (float) DEFAULT_LAT);
        double cachedLon = prefs.getFloat(KEY_LON, (float) DEFAULT_LON);

        new Thread(() -> {
            try {
                // ipapi.co free tier (~1000 req/day per TV IP) — no key required, returns lat/lon from TV's IP
                URL url = new URL("https://ipapi.co/json/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                double lat = json.getDouble("latitude");
                double lon = json.getDouble("longitude");

                prefs.edit()
                     .putFloat(KEY_LAT, (float) lat)
                     .putFloat(KEY_LON, (float) lon)
                     .apply();

                new Handler(Looper.getMainLooper()).post(() -> cb.onLocation(lat, lon));
            } catch (Exception e) {
                // Safe fallback to cached or default coordinates
                new Handler(Looper.getMainLooper()).post(() -> cb.onLocation(cachedLat, cachedLon));
            }
        }).start();
    }
}
