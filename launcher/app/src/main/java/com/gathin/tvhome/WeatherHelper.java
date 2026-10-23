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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lean, single-provider weather helper powered by Open-Meteo with local SharedPreferences caching.
 * Designed for 0ms cold-start display and low memory overhead on Android TV.
 */
public class WeatherHelper {
    private static final String PREFS_NAME = "weather_cache";
    private static final String KEY_PAYLOAD = "cached_payload";
    private static final String KEY_TIMESTAMP = "cached_timestamp";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes

    private final Context ctx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface WeatherCallback {
        void onResult(WeatherData data);
        void onError();
    }

    public static class WeatherData {
        public double tempC, feelsLikeC, windKmh;
        public int humidity, weatherCode;

        public static WeatherData fromJson(JSONObject json) throws Exception {
            JSONObject current = json.getJSONObject("current");
            WeatherData d = new WeatherData();
            d.tempC = current.getDouble("temperature_2m");
            d.feelsLikeC = current.getDouble("apparent_temperature");
            d.windKmh = current.getDouble("wind_speed_10m");
            d.humidity = current.getInt("relative_humidity_2m");
            d.weatherCode = current.getInt("weather_code");
            return d;
        }

        public String toJsonString() {
            return "{\"tempC\":" + tempC + ",\"feelsLikeC\":" + feelsLikeC +
                   ",\"windKmh\":" + windKmh + ",\"humidity\":" + humidity +
                   ",\"weatherCode\":" + weatherCode + "}";
        }

        public static WeatherData fromCacheString(String s) throws Exception {
            return fromJson(new JSONObject("{\"current\":" + s + "}"));
        }

        public String getWeatherIcon() {
            if (weatherCode == 0) return "☀️";
            if (weatherCode <= 3) return "⛅";
            if (weatherCode <= 48) return "🌫️";
            if (weatherCode <= 67) return "🌧️";
            if (weatherCode <= 77) return "❄️";
            if (weatherCode <= 82) return "🌦️";
            if (weatherCode <= 99) return "⛈️";
            return "☀️";
        }

        public String getWeatherDescription() {
            if (weatherCode == 0) return "Clear";
            if (weatherCode == 1) return "Mainly Clear";
            if (weatherCode == 2) return "Partly Cloudy";
            if (weatherCode == 3) return "Overcast";
            if (weatherCode <= 48) return "Foggy";
            if (weatherCode <= 67) return "Rain";
            if (weatherCode <= 77) return "Snow";
            if (weatherCode <= 82) return "Showers";
            if (weatherCode <= 99) return "Thunderstorm";
            return "Clear";
        }
    }

    public WeatherHelper(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void fetchWeather(double lat, double lon, WeatherCallback cb) {
        // 1. Serve cache instantly if fresh (0ms perceived latency)
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long cachedAt = prefs.getLong(KEY_TIMESTAMP, 0);
        String cachedPayload = prefs.getString(KEY_PAYLOAD, null);

        if (cachedPayload != null && (System.currentTimeMillis() - cachedAt) < CACHE_TTL_MS) {
            try {
                WeatherData cached = WeatherData.fromCacheString(cachedPayload);
                cb.onResult(cached);
                return; // fresh cache, skip network entirely
            } catch (Exception ignored) { /* fall through to network */ }
        }

        // 2. Stale or missing cache — show stale cache immediately if present so UI is never blank
        if (cachedPayload != null) {
            try {
                WeatherData cached = WeatherData.fromCacheString(cachedPayload);
                cb.onResult(cached);
            } catch (Exception ignored) {}
        }

        executor.execute(() -> {
            try {
                String url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m",
                    lat, lon);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                WeatherData data = WeatherData.fromJson(json);

                prefs.edit()
                     .putString(KEY_PAYLOAD, data.toJsonString())
                     .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                     .apply();

                mainHandler.post(() -> cb.onResult(data));
            } catch (Exception e) {
                if (cachedPayload == null) {
                    mainHandler.post(cb::onError);
                }
            }
        });
    }
}
