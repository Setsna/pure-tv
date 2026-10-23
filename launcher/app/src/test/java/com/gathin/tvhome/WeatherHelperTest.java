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
    public void locationHelper_defaultCoordinates_areValid() {
        assertEquals(28.5983, LocationHelper.DEFAULT_LAT, 0.001);
        assertEquals(77.1897, LocationHelper.DEFAULT_LON, 0.001);

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
}
