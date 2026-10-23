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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lean weather helper with Open-Meteo multi-day daily forecast and raw JSON cache.
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
        public double currentTempC;
        public int currentWeatherCode;
        public DailyForecast[] daily; // index 0 = today, 1-4 = upcoming days

        // Backwards compatibility aliases for screensaver and secondary activities
        public double tempC;
        public double feelsLikeC;
        public int humidity = 0;
        public double windKmh = 0;

        public static class DailyForecast {
            public double maxC, minC;
            public int weatherCode;
            public int rainChancePercent;
            public String dayLabel; // "Today", "Tomorrow", "Wed", etc.
        }

        public static WeatherData fromJson(JSONObject json) throws Exception {
            WeatherData d = new WeatherData();
            JSONObject current = json.getJSONObject("current");
            d.currentTempC = current.getDouble("temperature_2m");
            d.currentWeatherCode = current.getInt("weather_code");
            d.tempC = d.currentTempC;
            d.feelsLikeC = d.currentTempC;

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
                f.rainChancePercent = rainArr.getInt(i);
                f.dayLabel = labelForDay(i, dateArr.getString(i));
                d.daily[i] = f;
            }
            return d;
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
            if (currentWeatherCode == 0) return "☀️";
            if (currentWeatherCode <= 3) return "⛅";
            if (currentWeatherCode <= 48) return "🌫️";
            if (currentWeatherCode <= 67) return "🌧️";
            if (currentWeatherCode <= 77) return "❄️";
            if (currentWeatherCode <= 82) return "🌦️";
            if (currentWeatherCode <= 99) return "⛈️";
            return "☀️";
        }

        public String getWeatherDescription() {
            if (currentWeatherCode == 0) return "Clear";
            if (currentWeatherCode == 1) return "Mainly Clear";
            if (currentWeatherCode == 2) return "Partly Cloudy";
            if (currentWeatherCode == 3) return "Overcast";
            if (currentWeatherCode <= 48) return "Foggy";
            if (currentWeatherCode <= 67) return "Rain";
            if (currentWeatherCode <= 77) return "Snow";
            if (currentWeatherCode <= 82) return "Showers";
            if (currentWeatherCode <= 99) return "Thunderstorm";
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
                WeatherData cached = WeatherData.fromJson(new JSONObject(cachedPayload));
                cb.onResult(cached);
                return; // fresh cache, skip network entirely
            } catch (Exception ignored) { /* fall through to network */ }
        }

        // 2. Stale cache — show immediately so UI is never blank
        if (cachedPayload != null) {
            try {
                WeatherData cached = WeatherData.fromJson(new JSONObject(cachedPayload));
                cb.onResult(cached);
            } catch (Exception ignored) {}
        }

        executor.execute(() -> {
            try {
                String url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=5",
                    lat, lon);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String rawJson = sb.toString();
                JSONObject json = new JSONObject(rawJson);
                WeatherData data = WeatherData.fromJson(json);

                prefs.edit()
                     .putString(KEY_PAYLOAD, rawJson)
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
