package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Weather from Open-Meteo over HTTPS (no key): current conditions, the next 24 hours, five days, and
 * US AQI from Open-Meteo's air-quality API. The raw JSON is cached per location so the widget is never
 * blank and never shows another place's forecast.
 */
public class WeatherHelper {
    private static final String PREFS_NAME = "weather_cache";
    private static final String KEY_PAYLOAD = "cached_payload";
    private static final String KEY_TIMESTAMP = "cached_timestamp";
    private static final String KEY_LOCATION = "cached_location";
    private static final long CACHE_TTL_MS = 20 * 60 * 1000;

    private final Context ctx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface WeatherCallback {
        void onResult(WeatherData data);
        void onError();
    }

    public static class WeatherData {
        public double currentTempC;
        public int currentWeatherCode;
        public boolean isDay = true;
        public DailyForecast[] daily; // index 0 = today, 1-4 = upcoming days
        public Hourly[] hourly = new Hourly[0];
        /** US AQI, or -1 when unknown. */
        public int aqi = -1;

        // Also read by the screensaver and the full weather screen
        public double tempC;
        public double feelsLikeC;
        public int humidity = -1;
        public double windKmh = -1;

        public static class DailyForecast {
            public double maxC, minC;
            public int weatherCode;
            public int rainChancePercent;
            public String dayLabel; // "Today", "Tomorrow", "Wed", etc.
        }

        public static class Hourly {
            public long timeMs;
            public double tempC;
            public int weatherCode;
            public int rainChancePercent;
            public boolean isDay;
        }

        public static WeatherData fromJson(JSONObject json) throws Exception {
            WeatherData d = new WeatherData();
            JSONObject current = json.getJSONObject("current");
            d.currentTempC = current.getDouble("temperature_2m");
            d.currentWeatherCode = current.getInt("weather_code");
            d.isDay = current.optInt("is_day", 1) == 1;
            d.tempC = d.currentTempC;
            d.feelsLikeC = current.optDouble("apparent_temperature", d.currentTempC);
            d.humidity = current.optInt("relative_humidity_2m", -1);
            d.windKmh = current.optDouble("wind_speed_10m", -1);
            d.aqi = json.optInt("aqi_us", -1);

            JSONObject dailyJson = json.getJSONObject("daily");
            JSONArray maxArr = dailyJson.getJSONArray("temperature_2m_max");
            JSONArray minArr = dailyJson.getJSONArray("temperature_2m_min");
            JSONArray codeArr = dailyJson.getJSONArray("weather_code");
            JSONArray rainArr = dailyJson.getJSONArray("precipitation_probability_max");
            JSONArray dateArr = dailyJson.getJSONArray("time");

            int count = Math.min(5, maxArr.length());
            d.daily = new DailyForecast[count];
            for (int i = 0; i < count; i++) {
                DailyForecast f = new DailyForecast();
                f.maxC = maxArr.getDouble(i);
                f.minC = minArr.getDouble(i);
                f.weatherCode = codeArr.getInt(i);
                f.rainChancePercent = rainArr.optInt(i, 0);
                f.dayLabel = labelForDay(i, dateArr.getString(i));
                d.daily[i] = f;
            }

            JSONObject hourlyJson = json.optJSONObject("hourly");
            if (hourlyJson != null) {
                // Times are local to the forecast location; utc_offset_seconds says which zone that is.
                int offsetSec = json.optInt("utc_offset_seconds", TimeZone.getDefault().getRawOffset() / 1000);
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                JSONArray time = hourlyJson.getJSONArray("time");
                JSONArray temp = hourlyJson.getJSONArray("temperature_2m");
                JSONArray code = hourlyJson.getJSONArray("weather_code");
                JSONArray rain = hourlyJson.optJSONArray("precipitation_probability");
                JSONArray day = hourlyJson.optJSONArray("is_day");
                d.hourly = new Hourly[time.length()];
                for (int i = 0; i < time.length(); i++) {
                    Hourly h = new Hourly();
                    h.timeMs = fmt.parse(time.getString(i)).getTime() - offsetSec * 1000L;
                    h.tempC = temp.getDouble(i);
                    h.weatherCode = code.getInt(i);
                    h.rainChancePercent = rain != null ? rain.optInt(i, 0) : 0;
                    h.isDay = day == null || day.optInt(i, 1) == 1;
                    d.hourly[i] = h;
                }
            }
            return d;
        }

        /** The next {@code n} whole hours after {@code nowMs}. */
        public List<Hourly> upcomingHours(long nowMs, int n) {
            List<Hourly> out = new ArrayList<>();
            for (Hourly h : hourly) {
                if (h.timeMs > nowMs && out.size() < n) out.add(h);
            }
            return out;
        }

        /**
         * The first hour within the next 24 hours where rain is likely (at least 40%), or null.
         * Only future hours count, so a shower that already passed never raises a warning.
         */
        public Hourly rainAlert(long nowMs) {
            for (Hourly h : hourly) {
                if (h.timeMs <= nowMs || h.timeMs > nowMs + 24 * 3600_000L) continue;
                if (h.rainChancePercent >= 40) return h;
            }
            return null;
        }

        private static String labelForDay(int index, String isoDate) {
            if (index == 0) return "Today";
            if (index == 1) return "Tomorrow";
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("EEE", Locale.US);
            try {
                return out.format(in.parse(isoDate));
            } catch (Exception e) {
                return "";
            }
        }

        public String getWeatherIcon() {
            if (currentWeatherCode <= 1) return isDay ? "☀️" : "🌙";
            if (currentWeatherCode <= 3) return "⛅";
            if (currentWeatherCode <= 48) return "🌫️";
            if (currentWeatherCode <= 67) return "🌧️";
            if (currentWeatherCode <= 77) return "❄️";
            if (currentWeatherCode <= 82) return "🌦️";
            if (currentWeatherCode <= 99) return "⛈️";
            return "☀️";
        }

        public String getWeatherDescription() {
            return describe(currentWeatherCode);
        }
    }

    /** Short condition name for a WMO weather code. */
    public static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mainly Clear";
        if (code == 2) return "Partly Cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Foggy";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 99) return "Thunderstorm";
        return "Clear";
    }

    /** US AQI category name. */
    public static String aqiLabel(int aqi) {
        if (aqi < 0) return "";
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for some";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    public WeatherHelper(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void fetchWeather(double lat, double lon, WeatherCallback cb) {
        fetchWeather(lat, lon, false, cb);
    }

    /**
     * @param alwaysRefresh go to the network even when the cache is fresh (Home checks on every
     *                      opening); the cached forecast is still shown first so the widget is never blank
     */
    public void fetchWeather(double lat, double lon, boolean alwaysRefresh, WeatherCallback cb) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String locationKey = String.format(Locale.US, "%.3f,%.3f", lat, lon);
        boolean sameLocation = locationKey.equals(prefs.getString(KEY_LOCATION, null));
        long cachedAt = prefs.getLong(KEY_TIMESTAMP, 0);
        String cachedPayload = sameLocation ? prefs.getString(KEY_PAYLOAD, null) : null;

        // 1. Fresh cache for this location: no network at all
        if (!alwaysRefresh && cachedPayload != null && (System.currentTimeMillis() - cachedAt) < CACHE_TTL_MS) {
            try {
                cb.onResult(WeatherData.fromJson(new JSONObject(cachedPayload)));
                return;
            } catch (Exception ignored) { /* fall through to network */ }
        }

        // 2. Stale cache: show it now so the widget is never blank, then refresh
        if (cachedPayload != null) {
            try {
                cb.onResult(WeatherData.fromJson(new JSONObject(cachedPayload)));
            } catch (Exception ignored) {}
        }

        final String fallback = cachedPayload;
        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject(get(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                    + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day"
                    + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day&forecast_hours=26"
                    + "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max"
                    + "&timezone=auto&forecast_days=5",
                    lat, lon)));
                try {
                    JSONObject air = new JSONObject(get(String.format(Locale.US,
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=us_aqi",
                        lat, lon)));
                    json.put("aqi_us", air.getJSONObject("current").getInt("us_aqi"));
                } catch (Exception ignored) {
                    // Air quality is a nice-to-have; the forecast stands on its own.
                }
                WeatherData data = WeatherData.fromJson(json);
                prefs.edit()
                     .putString(KEY_PAYLOAD, json.toString())
                     .putString(KEY_LOCATION, locationKey)
                     .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                     .apply();
                mainHandler.post(() -> cb.onResult(data));
            } catch (Exception e) {
                if (fallback == null) mainHandler.post(cb::onError);
            }
        });
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
