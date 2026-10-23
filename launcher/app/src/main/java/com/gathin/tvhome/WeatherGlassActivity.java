package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Full-Screen Apple tvOS "Liquid Glass" Weather Experience for Android TV.
 * Features:
 * - Obsidian / Warm Ivory / Champagne Gold styling.
 * - Translucent glass HUD with top-edge specular highlight.
 * - Interactive Tab Row (Temperature, Precipitation, Wind, AQI).
 * - Continuous 5-stop EPA AQI scale bar with animated position indicator.
 * - 7-Day weather forecast cards with smooth TV focus states.
 */
public class WeatherGlassActivity extends Activity {

    private ImageView weatherBgWallpaper;
    private TextView weatherLocTitle, weatherDateSub;
    private TextView statAqi, statHumidity, statPrecip, statWind;
    private TextView primaryWeatherIcon, primaryTempNumber, conditionSummaryText;

    // Tabs
    private LinearLayout tabTemp, tabPrecip, tabWind, tabAqi;
    private TextView tabTempLabel, tabPrecipLabel, tabWindLabel, tabAqiLabel;
    private View tabTempIndicator, tabPrecipIndicator, tabWindIndicator, tabAqiIndicator;

    // Detail Panes
    private LinearLayout paneAqi, paneGeneric;
    private TextView aqiBigNumber, aqiCategoryWord, aqiAdvisoryText;
    private View aqiGradientBar, aqiIndicatorDot;
    private TextView genericPaneTitle, genericPaneDesc;

    // 7-Day Forecast Cards
    private final LinearLayout[] forecastCards = new LinearLayout[7];
    private final TextView[] fDates = new TextView[7];
    private final TextView[] fIcons = new TextView[7];
    private final TextView[] fTemps = new TextView[7];

    private int currentAqi = 120;
    private int activeTab = 3; // 0=Temp, 1=Precip, 2=Wind, 3=AQI (Matches Reference Screenshot)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_glass);

        initViews();
        setupAqiGradientBar();
        setupWallpaper();
        setupTabs();
        setupForecastCards();
        loadWeatherData();

        // Initial focus on AQI tab (matching reference screenshot)
        tabAqi.post(() -> {
            tabAqi.requestFocus();
            switchTab(3);
        });
    }

    private void initViews() {
        weatherBgWallpaper = findViewById(R.id.weatherBgWallpaper);
        weatherLocTitle = findViewById(R.id.weatherLocTitle);
        weatherDateSub = findViewById(R.id.weatherDateSub);

        statAqi = findViewById(R.id.statAqi);
        statHumidity = findViewById(R.id.statHumidity);
        statPrecip = findViewById(R.id.statPrecip);
        statWind = findViewById(R.id.statWind);

        primaryWeatherIcon = findViewById(R.id.primaryWeatherIcon);
        primaryTempNumber = findViewById(R.id.primaryTempNumber);
        conditionSummaryText = findViewById(R.id.conditionSummaryText);

        tabTemp = findViewById(R.id.tabTemp);
        tabPrecip = findViewById(R.id.tabPrecip);
        tabWind = findViewById(R.id.tabWind);
        tabAqi = findViewById(R.id.tabAqi);

        tabTempLabel = findViewById(R.id.tabTempLabel);
        tabPrecipLabel = findViewById(R.id.tabPrecipLabel);
        tabWindLabel = findViewById(R.id.tabWindLabel);
        tabAqiLabel = findViewById(R.id.tabAqiLabel);

        tabTempIndicator = findViewById(R.id.tabTempIndicator);
        tabPrecipIndicator = findViewById(R.id.tabPrecipIndicator);
        tabWindIndicator = findViewById(R.id.tabWindIndicator);
        tabAqiIndicator = findViewById(R.id.tabAqiIndicator);

        paneAqi = findViewById(R.id.paneAqi);
        paneGeneric = findViewById(R.id.paneGeneric);
        aqiBigNumber = findViewById(R.id.aqiBigNumber);
        aqiCategoryWord = findViewById(R.id.aqiCategoryWord);
        aqiAdvisoryText = findViewById(R.id.aqiAdvisoryText);
        aqiGradientBar = findViewById(R.id.aqiGradientBar);
        aqiIndicatorDot = findViewById(R.id.aqiIndicatorDot);

        genericPaneTitle = findViewById(R.id.genericPaneTitle);
        genericPaneDesc = findViewById(R.id.genericPaneDesc);

        int[] cardIds = {
            R.id.forecastDay0, R.id.forecastDay1, R.id.forecastDay2,
            R.id.forecastDay3, R.id.forecastDay4, R.id.forecastDay5, R.id.forecastDay6
        };
        int[] dateIds = {R.id.f0Date, R.id.f1Date, R.id.f2Date, R.id.f3Date, R.id.f4Date, R.id.f5Date, R.id.f6Date};
        int[] iconIds = {R.id.f0Icon, R.id.f1Icon, R.id.f2Icon, R.id.f3Icon, R.id.f4Icon, R.id.f5Icon, R.id.f6Icon};
        int[] tempIds = {R.id.f0Temp, R.id.f1Temp, R.id.f2Temp, R.id.f3Temp, R.id.f4Temp, R.id.f5Temp, R.id.f6Temp};

        for (int i = 0; i < 7; i++) {
            forecastCards[i] = findViewById(cardIds[i]);
            fDates[i] = findViewById(dateIds[i]);
            fIcons[i] = findViewById(iconIds[i]);
            fTemps[i] = findViewById(tempIds[i]);
        }

        // Live Header Date & Time
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, h:mm a", Locale.getDefault());
        weatherDateSub.setText(sdf.format(new Date()));
    }

    /**
     * Programmatic 5-stop continuous linear gradient for the EPA AQI Scale:
     * Green (#34C759) -> Yellow (#FFCC00) -> Orange (#FF9500) -> Red (#FF3B30) -> Purple (#AF52DE)
     */
    private void setupAqiGradientBar() {
        int[] aqiColors = {
            0xFF34C759, // Good (0-50)
            0xFFFFCC00, // Moderate (51-100)
            0xFFFF9500, // Unhealthy for Sensitive (101-150)
            0xFFFF3B30, // Unhealthy (151-200)
            0xFFAF52DE  // Very Unhealthy / Hazardous (201-300+)
        };
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, aqiColors);
        gradient.setCornerRadius(12f);
        aqiGradientBar.setBackground(gradient);
    }

    private void updateAqiPosition(int aqi) {
        currentAqi = Math.max(0, Math.min(aqi, 450));
        aqiBigNumber.setText(String.valueOf(currentAqi));

        // Category Classification & Advisories
        if (currentAqi <= 50) {
            aqiCategoryWord.setText("Good");
            aqiCategoryWord.setTextColor(0xFF34C759);
            aqiAdvisoryText.setText("Air quality is considered satisfactory, and air pollution poses little or no risk. Ideal conditions for outdoor activities.");
        } else if (currentAqi <= 100) {
            aqiCategoryWord.setText("Moderate");
            aqiCategoryWord.setTextColor(0xFFFFCC00);
            aqiAdvisoryText.setText("Air quality is acceptable; however, some pollutants may be a moderate health concern for a very small number of individuals.");
        } else if (currentAqi <= 150) {
            aqiCategoryWord.setText("Poor");
            aqiCategoryWord.setTextColor(0xFFFF9500);
            aqiAdvisoryText.setText("The air has reached a high level of pollution and is unhealthy for sensitive groups. Reduce time spent outside if you are feeling symptoms such as difficulty breathing or throat irritation.");
        } else if (currentAqi <= 200) {
            aqiCategoryWord.setText("Unhealthy");
            aqiCategoryWord.setTextColor(0xFFFF3B30);
            aqiAdvisoryText.setText("Everyone may begin to experience health effects; members of sensitive groups may experience more serious health effects.");
        } else {
            aqiCategoryWord.setText("Very Unhealthy");
            aqiCategoryWord.setTextColor(0xFFAF52DE);
            aqiAdvisoryText.setText("Health alert: The risk of health effects is increased for everyone. Avoid outdoor exertion and keep windows closed.");
        }

        statAqi.setText("AQI: " + currentAqi + " (" + aqiCategoryWord.getText() + ")");

        // Position Indicator Dot along the scale
        aqiGradientBar.post(() -> {
            int totalWidth = aqiGradientBar.getWidth() - aqiIndicatorDot.getWidth();
            if (totalWidth <= 0) return;
            // 0 -> 0px, 400+ -> totalWidth
            float fraction = Math.min(1.0f, currentAqi / 400.0f);
            float targetX = totalWidth * fraction;
            aqiIndicatorDot.animate().translationX(targetX).setDuration(400).start();
        });
    }

    private void setupWallpaper() {
        File dir = new File("/sdcard/Wallpapers");
        File[] files = dir.exists() ? dir.listFiles((d, n) -> n.endsWith(".jpg") || n.endsWith(".webp")) : null;
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2; // Memory safe downscale to 1080p
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bmp = BitmapFactory.decodeFile(files[0].getAbsolutePath(), opts);
            if (bmp != null) {
                weatherBgWallpaper.setImageBitmap(bmp);
            }
        }
    }

    private void setupTabs() {
        tabTemp.setOnClickListener(v -> switchTab(0));
        tabPrecip.setOnClickListener(v -> switchTab(1));
        tabWind.setOnClickListener(v -> switchTab(2));
        tabAqi.setOnClickListener(v -> switchTab(3));

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            float scale = hasFocus ? 1.04f : 1.0f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(160).start();
            if (hasFocus) {
                if (v == tabTemp) switchTab(0);
                else if (v == tabPrecip) switchTab(1);
                else if (v == tabWind) switchTab(2);
                else if (v == tabAqi) switchTab(3);
            }
        };

        tabTemp.setOnFocusChangeListener(focusListener);
        tabPrecip.setOnFocusChangeListener(focusListener);
        tabWind.setOnFocusChangeListener(focusListener);
        tabAqi.setOnFocusChangeListener(focusListener);

        View.OnKeyListener tabKeyHandler = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                int targetIndex = Math.min(activeTab, 6);
                if (forecastCards[targetIndex] != null) {
                    forecastCards[targetIndex].requestFocus();
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return true; // clamp at top
            }
            return false;
        };

        tabTemp.setOnKeyListener(tabKeyHandler);
        tabPrecip.setOnKeyListener(tabKeyHandler);
        tabWind.setOnKeyListener(tabKeyHandler);
        tabAqi.setOnKeyListener(tabKeyHandler);
    }

    private void switchTab(int index) {
        activeTab = index;

        // Reset Tab Labels & Indicators
        tabTempLabel.setTextColor(index == 0 ? 0xFFF5F2EB : 0x80F5F2EB);
        tabTempLabel.setTypeface(null, index == 0 ? Typeface.BOLD : Typeface.NORMAL);
        tabTempIndicator.setVisibility(index == 0 ? View.VISIBLE : View.INVISIBLE);

        tabPrecipLabel.setTextColor(index == 1 ? 0xFFF5F2EB : 0x80F5F2EB);
        tabPrecipLabel.setTypeface(null, index == 1 ? Typeface.BOLD : Typeface.NORMAL);
        tabPrecipIndicator.setVisibility(index == 1 ? View.VISIBLE : View.INVISIBLE);

        tabWindLabel.setTextColor(index == 2 ? 0xFFF5F2EB : 0x80F5F2EB);
        tabWindLabel.setTypeface(null, index == 2 ? Typeface.BOLD : Typeface.NORMAL);
        tabWindIndicator.setVisibility(index == 2 ? View.VISIBLE : View.INVISIBLE);

        tabAqiLabel.setTextColor(index == 3 ? 0xFFF5F2EB : 0x80F5F2EB);
        tabAqiLabel.setTypeface(null, index == 3 ? Typeface.BOLD : Typeface.NORMAL);
        tabAqiIndicator.setVisibility(index == 3 ? View.VISIBLE : View.INVISIBLE);

        // Switch Detail Pane
        if (index == 3) {
            paneAqi.setVisibility(View.VISIBLE);
            paneGeneric.setVisibility(View.GONE);
            updateAqiPosition(currentAqi);
        } else {
            paneAqi.setVisibility(View.GONE);
            paneGeneric.setVisibility(View.VISIBLE);
            if (index == 0) {
                genericPaneTitle.setText("Temperature & Comfort Outlook");
                genericPaneDesc.setText("Current temperature is " + primaryTempNumber.getText() + "°C. Expect afternoon highs reaching 30°C before cooling down to 22°C by midnight. UV index moderate.");
            } else if (index == 1) {
                genericPaneTitle.setText("Precipitation & Rain Forecast");
                genericPaneDesc.setText("Precipitation probability is currently 0%. Clear skies and dry weather expected across Delhi NCR with no rain projected over the next 24 hours.");
            } else if (index == 2) {
                genericPaneTitle.setText("Wind & Atmospheric Pressure");
                genericPaneDesc.setText("Gentle breeze of 9 km/h from the Northwest. Barometric pressure is stable at 1014 hPa with good visibility.");
            }
        }
    }

    private void setupForecastCards() {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE d", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        View.OnKeyListener forecastKeyHandler = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                View targetTab = tabAqi;
                if (activeTab == 0) targetTab = tabTemp;
                else if (activeTab == 1) targetTab = tabPrecip;
                else if (activeTab == 2) targetTab = tabWind;
                if (targetTab != null) targetTab.requestFocus();
                return true;
            }
            return false;
        };

        for (int i = 0; i < 7; i++) {
            final int pos = i;
            if (i == 0) {
                fDates[i].setText("Today");
            } else {
                cal.add(Calendar.DAY_OF_YEAR, 1);
                fDates[i].setText(dayFormat.format(cal.getTime()));
            }

            // TV Focus Scale & Animation
            forecastCards[i].setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.06f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(180).start();
                fDates[pos].setTextColor(hasFocus ? 0xFFC5A059 : 0xFFF5F2EB);
            });
            forecastCards[i].setOnKeyListener(forecastKeyHandler);
        }
    }

    private void loadWeatherData() {
        new WeatherHelper(this).fetchWeather(28.5983, 77.1897, new WeatherHelper.WeatherCallback() {
            @Override
            public void onResult(WeatherHelper.WeatherData data) {
                runOnUiThread(() -> {
                    if (data == null) return;
                    primaryWeatherIcon.setText(data.getWeatherIcon());
                    primaryTempNumber.setText(String.valueOf(Math.round(data.tempC)));
                    conditionSummaryText.setText("· " + data.getWeatherDescription());

                    statPrecip.setText("Feels like: " + Math.round(data.feelsLikeC) + "°");
                    statHumidity.setText("Humidity: " + data.humidity + "%");
                    statWind.setText("Wind: " + Math.round(data.windKmh) + " km/h");
                    updateAqiPosition(85);
                });
            }

            @Override
            public void onError() {
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
