package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * The screensaver's glass dashboard: clock and date (TextClocks tick on their own), a quote of the
 * day and the next festival on the left, weather cards on the right. It only shows the forecast Home
 * last fetched ({@link #render}); it never fetches anything itself. While visible, the whole dashboard
 * drifts a few pixels each minute so nothing sits on the same pixels for hours.
 */
final class ScreensaverDashboard {

    private static final long DRIFT_MS = 60_000L;
    /** Festivals further away than this aren't worth a countdown yet. */
    private static final int FESTIVAL_HORIZON_DAYS = 45;

    private final Activity activity;
    private final View hud;
    private final View weather;
    private final ImageView icon;
    private final TextView temp, desc, highLow, location, rain;
    private final LinearLayout hourly;
    private final TextView feels, humidity, wind, aqi, aqiLabel;
    private final TextView quote, quoteBy, festival;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final float density;

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            float range = 14 * density;
            hud.animate()
                    .translationX((random.nextFloat() * 2 - 1) * range)
                    .translationY((random.nextFloat() * 2 - 1) * range)
                    .setDuration(4000).start();
            handler.postDelayed(this, DRIFT_MS);
        }
    };

    ScreensaverDashboard(Activity activity) {
        this.activity = activity;
        density = activity.getResources().getDisplayMetrics().density;
        hud = activity.findViewById(R.id.screensaverHud);
        weather = activity.findViewById(R.id.ssWeather);
        icon = activity.findViewById(R.id.ssWeatherIcon);
        temp = activity.findViewById(R.id.ssWeatherTemp);
        desc = activity.findViewById(R.id.ssWeatherDesc);
        highLow = activity.findViewById(R.id.ssHighLow);
        location = activity.findViewById(R.id.ssLocation);
        rain = activity.findViewById(R.id.ssRain);
        hourly = activity.findViewById(R.id.ssHourly);
        feels = activity.findViewById(R.id.ssFeels);
        humidity = activity.findViewById(R.id.ssHumidity);
        wind = activity.findViewById(R.id.ssWind);
        aqi = activity.findViewById(R.id.ssAqi);
        aqiLabel = activity.findViewById(R.id.ssAqiLabel);
        quote = activity.findViewById(R.id.ssQuote);
        quoteBy = activity.findViewById(R.id.ssQuoteBy);
        festival = activity.findViewById(R.id.ssFestival);
    }

    /** Screensaver shown: today's quote and festival, start drifting. */
    void onShow() {
        showQuote();
        showFestival();
        handler.removeCallbacks(drift);
        handler.postDelayed(drift, DRIFT_MS);
    }

    void onHide() {
        handler.removeCallbacks(drift);
        hud.animate().cancel();
        hud.setTranslationX(0f);
        hud.setTranslationY(0f);
    }

    /** Fill the weather cards from the forecast Home fetched. */
    void render(WeatherHelper.WeatherData data) {
        if (data == null) return;
        long now = System.currentTimeMillis();
        weather.setVisibility(View.VISIBLE);

        icon.setImageResource(MainActivity.iconForWeatherCode(data.currentWeatherCode, data.isDay));
        temp.setText(Math.round(data.currentTempC) + "°");
        desc.setText(WeatherHelper.describe(data.currentWeatherCode));
        if (data.daily != null && data.daily.length > 0) {
            highLow.setText("H " + Math.round(data.daily[0].maxC) + "°   L " + Math.round(data.daily[0].minC) + "°");
        }
        String city = LocationHelper.cityName(activity);
        location.setText(city);
        location.setVisibility(city.isEmpty() ? View.GONE : View.VISIBLE);

        SimpleDateFormat hourFmt = new SimpleDateFormat("h a", Locale.getDefault());
        WeatherHelper.WeatherData.Hourly next = data.rainAlert(now);
        int rainColor = activity.getColor(next != null ? R.color.rain_blue : R.color.text_secondary);
        if (next != null) {
            Calendar at = Calendar.getInstance();
            at.setTimeInMillis(next.timeMs);
            boolean tomorrow = at.get(Calendar.DAY_OF_YEAR) != Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            rain.setText("Rain " + (tomorrow ? "tomorrow around " : "likely around ")
                    + hourFmt.format(new Date(next.timeMs)).toUpperCase(Locale.getDefault())
                    + "  ·  " + next.rainChancePercent + "%");
        } else {
            rain.setText(R.string.weather_no_rain);
        }
        rain.setTextColor(rainColor);
        rain.setCompoundDrawableTintList(ColorStateList.valueOf(rainColor));
        MainActivity.showUpdated(activity.findViewById(R.id.ssUpdated), data.fetchedAtMs);

        hourly.removeAllViews();
        for (WeatherHelper.WeatherData.Hourly h : data.upcomingHours(now, 5)) {
            View cell = LayoutInflater.from(activity).inflate(R.layout.weather_hour_item, hourly, false);
            ((TextView) cell.findViewById(R.id.tvHourLabel))
                    .setText(hourFmt.format(new Date(h.timeMs)).toUpperCase(Locale.getDefault()));
            ((ImageView) cell.findViewById(R.id.ivHourIcon))
                    .setImageResource(MainActivity.iconForWeatherCode(h.weatherCode, h.isDay));
            ((TextView) cell.findViewById(R.id.tvHourTemp)).setText(Math.round(h.tempC) + "°");
            if (h.rainChancePercent >= 20) {
                TextView r = cell.findViewById(R.id.tvHourRain);
                r.setText(h.rainChancePercent + "%");
                r.setVisibility(View.VISIBLE);
            }
            hourly.addView(cell);
        }

        feels.setText(Math.round(data.feelsLikeC) + "°");
        humidity.setText(data.humidity >= 0 ? data.humidity + "%" : "–");
        wind.setText(data.windKmh >= 0 ? Math.round(data.windKmh) + " km/h" : "–");
        if (data.aqi >= 0) {
            aqi.setText(String.valueOf(data.aqi));
            aqi.setTextColor(MainActivity.aqiColor(activity, data.aqi));
            aqiLabel.setText(WeatherHelper.aqiLabel(data.aqi));
        } else {
            aqi.setText("–");
            aqiLabel.setText("");
        }
    }

    private void showQuote() {
        List<String[]> quotes = readPairs(R.raw.quotes);
        if (quotes.isEmpty()) {
            quote.setVisibility(View.GONE);
            quoteBy.setVisibility(View.GONE);
            return;
        }
        String[] q = quotes.get(Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % quotes.size());
        quote.setText("“" + q[0] + "”");
        quoteBy.setText(q[1]);
    }

    private void showFestival() {
        String text = nextFestivalText(readPairs(R.raw.festivals), Calendar.getInstance());
        festival.setText(text);
        festival.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    /** "Today · Diwali", "Tomorrow · Diwali" or "Diwali · in 15 days" for the next festival, or null. */
    private String nextFestivalText(List<String[]> festivals, Calendar today) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar day = (Calendar) today.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        String best = null;
        long bestDays = Long.MAX_VALUE;
        for (String[] f : festivals) {
            try {
                Date d = iso.parse(f[0]);
                long days = Math.round((d.getTime() - day.getTimeInMillis()) / (double) TimeUnit.DAYS.toMillis(1));
                if (days >= 0 && days <= FESTIVAL_HORIZON_DAYS && days < bestDays) {
                    bestDays = days;
                    best = f[1];
                }
            } catch (Exception ignored) {
            }
        }
        if (best == null) return null;
        if (bestDays == 0) return activity.getString(R.string.ss_festival_today, best);
        if (bestDays == 1) return activity.getString(R.string.ss_festival_tomorrow, best);
        return activity.getString(R.string.ss_festival_in_days, best, (int) bestDays);
    }

    /** "a|b" lines from a raw resource; blank lines and # comments skipped. */
    private List<String[]> readPairs(int rawRes) {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                activity.getResources().openRawResource(rawRes), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int bar = line.indexOf('|');
                if (bar > 0) out.add(new String[] {line.substring(0, bar).trim(), line.substring(bar + 1).trim()});
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
