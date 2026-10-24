package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WeatherHelperTest {

    @Test
    public void weatherData_fromJson_parsesCurrentAndDailyForecast() throws Exception {
        String jsonStr = "{"
            + "\"current\": {\"temperature_2m\": 27.4, \"weather_code\": 1},"
            + "\"daily\": {"
            + "  \"time\": [\"2026-10-23\", \"2026-10-24\", \"2026-10-25\", \"2026-10-26\", \"2026-10-27\"],"
            + "  \"temperature_2m_max\": [31.5, 30.2, 29.8, 28.0, 27.5],"
            + "  \"temperature_2m_min\": [20.1, 19.5, 18.2, 17.0, 16.5],"
            + "  \"weather_code\": [1, 2, 3, 61, 0],"
            + "  \"precipitation_probability_max\": [10, 45, 60, 80, 0]"
            + "}"
            + "}";

        JSONObject json = new JSONObject(jsonStr);
        WeatherHelper.WeatherData data = WeatherHelper.WeatherData.fromJson(json);

        assertNotNull(data);
        assertEquals(27.4, data.currentTempC, 0.01);
        assertEquals(1, data.currentWeatherCode);
        assertEquals(27.4, data.tempC, 0.01);
        assertEquals(27.4, data.feelsLikeC, 0.01);

        assertNotNull(data.daily);
        assertEquals(5, data.daily.length);

        // Day 0: Today
        assertEquals("Today", data.daily[0].dayLabel);
        assertEquals(31.5, data.daily[0].maxC, 0.01);
        assertEquals(20.1, data.daily[0].minC, 0.01);
        assertEquals(1, data.daily[0].weatherCode);
        assertEquals(10, data.daily[0].rainChancePercent);

        // Day 1: Tomorrow
        assertEquals("Tomorrow", data.daily[1].dayLabel);
        assertEquals(30.2, data.daily[1].maxC, 0.01);
        assertEquals(45, data.daily[1].rainChancePercent);

        // Day 2+: formatted day of week (non-empty)
        assertNotNull(data.daily[2].dayLabel);
        assertTrue(!data.daily[2].dayLabel.isEmpty());
    }

    @Test
    public void locationHelper_override_parsesLatLonAndCity() {
        double[] ll = LocationHelper.parseOverride("19.076, 72.8777, Mumbai");
        assertNotNull(ll);
        assertEquals(19.076, ll[0], 0.0001);
        assertEquals(72.8777, ll[1], 0.0001);
        assertEquals("Mumbai", LocationHelper.overrideCity("19.076, 72.8777, Mumbai"));
        assertEquals(null, LocationHelper.parseOverride(""));
        assertEquals(null, LocationHelper.parseOverride("abc,def"));
        assertEquals(null, LocationHelper.parseOverride("95,10"));
    }

    @Test
    public void locationHelper_nullContext_reportsError() {

        boolean[] errorCalled = new boolean[]{false};
        LocationHelper.detectLocation(null, new LocationHelper.LocationCallback() {
            @Override
            public void onLocation(double lat, double lon) {}

            @Override
            public void onError() {
                errorCalled[0] = true;
            }
        });
        assertTrue(errorCalled[0]);
    }

    private static final String HOURLY_JSON = "{"
        + "\"utc_offset_seconds\": 19800,"
        + "\"aqi_us\": 190,"
        + "\"current\": {\"temperature_2m\": 22.2, \"apparent_temperature\": 24.2, \"relative_humidity_2m\": 75,"
        + "  \"wind_speed_10m\": 4.1, \"weather_code\": 0, \"is_day\": 0},"
        + "\"hourly\": {"
        + "  \"time\": [\"2026-10-24T22:00\", \"2026-10-24T23:00\", \"2026-10-25T00:00\", \"2026-10-25T17:00\", \"2026-10-25T23:00\"],"
        + "  \"temperature_2m\": [22.5, 22.2, 21.8, 29.0, 22.0],"
        + "  \"weather_code\": [0, 0, 0, 61, 63],"
        + "  \"precipitation_probability\": [80, 0, 10, 60, 90],"
        + "  \"is_day\": [0, 0, 0, 1, 0]"
        + "},"
        + "\"daily\": {\"time\": [\"2026-10-24\"], \"temperature_2m_max\": [30.9], \"temperature_2m_min\": [20.1],"
        + "  \"weather_code\": [0], \"precipitation_probability_max\": [10]}"
        + "}";

    /** 2026-10-24 22:30 in New Delhi (UTC+5:30) as epoch millis. */
    private static long delhi(int day, int hour, int minute) throws Exception {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        return f.parse(String.format(java.util.Locale.US, "2026-10-%02d %02d:%02d", day, hour, minute)).getTime();
    }

    @Test
    public void weatherData_parsesCurrentDetailsAqiAndHourlyInLocationTime() throws Exception {
        WeatherHelper.WeatherData d = WeatherHelper.WeatherData.fromJson(new JSONObject(HOURLY_JSON));
        assertEquals(24.2, d.feelsLikeC, 0.01);
        assertEquals(75, d.humidity);
        assertEquals(4.1, d.windKmh, 0.01);
        assertEquals(190, d.aqi);
        assertTrue(!d.isDay);
        assertEquals(5, d.hourly.length);
        assertEquals(delhi(24, 22, 0), d.hourly[0].timeMs);
        assertEquals(delhi(25, 17, 0), d.hourly[3].timeMs);
    }

    @Test
    public void rainAlert_skipsPastHours_andFindsFirstLikelyHourWithin24h() throws Exception {
        WeatherHelper.WeatherData d = WeatherHelper.WeatherData.fromJson(new JSONObject(HOURLY_JSON));
        // 22:00 had 80% but is already past at 22:30; 17:00 tomorrow (60%) is the first future match.
        WeatherHelper.WeatherData.Hourly alert = d.rainAlert(delhi(24, 22, 30));
        assertNotNull(alert);
        assertEquals(delhi(25, 17, 0), alert.timeMs);
        assertEquals(60, alert.rainChancePercent);
    }

    @Test
    public void rainAlert_ignoresRainBeyond24Hours() throws Exception {
        WeatherHelper.WeatherData d = WeatherHelper.WeatherData.fromJson(new JSONObject(HOURLY_JSON));
        // From 18:00 on the 25th, only 23:00 (90%) is ahead and it is within 24h.
        assertEquals(delhi(25, 23, 0), d.rainAlert(delhi(25, 18, 0)).timeMs);
        // From noon on the 23rd the next 24 hours end before any forecast hour: no warning.
        assertEquals(null, d.rainAlert(delhi(23, 12, 0)));
    }

    @Test
    public void upcomingHours_returnsOnlyFutureHours() throws Exception {
        WeatherHelper.WeatherData d = WeatherHelper.WeatherData.fromJson(new JSONObject(HOURLY_JSON));
        java.util.List<WeatherHelper.WeatherData.Hourly> next = d.upcomingHours(delhi(24, 22, 30), 3);
        assertEquals(3, next.size());
        assertEquals(delhi(24, 23, 0), next.get(0).timeMs);
    }

    @Test
    public void aqiLabels_followUsScale() {
        assertEquals("Good", WeatherHelper.aqiLabel(40));
        assertEquals("Moderate", WeatherHelper.aqiLabel(84));
        assertEquals("Unhealthy", WeatherHelper.aqiLabel(190));
        assertEquals("", WeatherHelper.aqiLabel(-1));
    }
}
