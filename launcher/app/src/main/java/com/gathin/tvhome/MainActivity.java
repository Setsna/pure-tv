package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and lightweight native focus animation.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    private String liveTvAction;
    private String inputAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private View ambient;
    private TextView heroName;
    private AmbientDrawable ambientDrawable;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private View btnLiveTv;
    private View btnInputs;
    private TextView btnWifi;
    private TextView btnSettings;
    private TextView brandMark;

    // 3-minute automatic app memory reaper & system speed optimization
    private static final long REAP_POLL_INTERVAL_MS = 30_000L; // Check every 30s
    private static final long LOW_MEM_THRESHOLD_MB = 150L;     // Act when available RAM < 150MB or lowMemory
    private static final Set<String> PROTECTED_PACKAGES = new HashSet<>(Arrays.asList(
        "com.google.android.apps.mediashell",
        "com.xiaomi.mitv.smartshare",
        "com.mitv.milinkservice",
        "com.xiaomi.wfdsinkhelperservice",
        "com.google.android.katniss",
        "com.google.android.tv.assistant",
        "com.mitv.livetv",
        "com.mitv.livetv.ci_mediatek",
        "com.google.android.inputmethod.latin"
    ));

    private final Handler memoryReaperHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> exitedAppsTimestamps = new HashMap<>();
    private String currentlyRunningPkg = null;

    private final Runnable memoryReaperTask = new Runnable() {
        @Override
        public void run() {
            // Snapshot on the main thread: the map is only ever touched here.
            final Map<String, Long> exited = new HashMap<>(exitedAppsTimestamps);
            final String inUse = currentlyRunningPkg;
            new Thread(() -> {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am == null) return;
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(info);
                long availMb = info.availMem / (1024 * 1024);

                if (info.lowMemory || availMb < LOW_MEM_THRESHOLD_MB) {
                    ProcessManager.trimIdleApps(MainActivity.this, exited, inUse);
                }
            }, "TvMemoryReaper").start();
            memoryReaperHandler.postDelayed(this, REAP_POLL_INTERVAL_MS);
        }
    };

    /** Under memory pressure, stop streaming apps left long ago; recently used ones stay warm. */
    private void trimIdleStreamingApps() {
        ProcessManager.trimIdleApps(this, exitedAppsTimestamps, currentlyRunningPkg);
    }
    private android.widget.TextClock clock;
    private android.widget.TextClock ampm;
    private android.widget.TextClock date;
    private View clockDivider;
    private boolean isDayMode = false;
    private DockDecoration dockDecoration;
    private TextView shamsiDate;
    private boolean shamsi;
    private String vpnApp;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;

    // --- SINGLE PERMANENT WALLPAPER & APPLE TV SCREENSAVER ---
    private ImageView wallpaper;
    private View wallpaperScrim;
    private View homeContent, screensaverOverlay, screensaverHud;
    private ScreensaverDashboard dashboard;
    private View topButtons;
    private View launcherContent;
    private boolean inScreensaver = false;
    public static Integer sTestHourOfDay = null;
    private String currentSsid = "";

    // Weather Views (Apple-widget-style)
    private View weatherWidget;
    private ImageView ivTodayIcon;
    private TextView tvTodayTemp;
    private TextView tvTodayCondition;
    private TextView tvRainChance;
    private View weatherNow;
    private TextView tvFeels, tvHumidity, tvWind, tvAqiValue, tvAqiLabel;
    private LinearLayout hourlyRow;
    private TextView exploreHint;

    // Apple TV Ambient Idle Watchdog (5 minutes of inactivity on home screen)
    private static final long AMBIENT_IDLE_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes exactly
    private static final long SCREENSAVER_TIMEOUT_MS = AMBIENT_IDLE_TIMEOUT_MS;
    private boolean isScreensaverActive = false;
    private boolean wakeUpKeyHandled = false;
    private final Runnable screensaverRunnable = () -> showScreensaver(true);

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reloadApps(false);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            updateDayNightTheme();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setBackgroundDrawable(null);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        COLUMNS = cfg.columns;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        inputAction = cfg.inputAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;
        ensureAccessibilityServiceEnabled();

        root = findViewById(R.id.root);
        root.setBackgroundColor(cfg.backgroundColor);
        if (cfg.wallpaper != null) showWallpaper(cfg.wallpaper);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientDrawable = new AmbientDrawable();
        ambient.setBackground(ambientDrawable);
        greeting = findViewById(R.id.greeting);
        clock = findViewById(R.id.clock);
        ampm = findViewById(R.id.ampm);
        date = findViewById(R.id.date);
        clockDivider = findViewById(R.id.clockDivider);
        btnWifi = findViewById(R.id.btnWifi);
        btnLiveTv = findViewById(R.id.btnLiveTv);
        btnInputs = findViewById(R.id.btnInputs);
        btnSettings = findViewById(R.id.btnSettings);
        brandMark = findViewById(R.id.brandMark);
        exploreHint = findViewById(R.id.exploreHint);
        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = false;
        if (shamsiDate != null) shamsiDate.setVisibility(View.GONE);
        if (greeting != null) greeting.setVisibility(View.GONE);
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        // Both rows always fit in the grid (see syncDockHeights), so it never scrolls; the dock
        // container's height alone decides whether one or two rows show.
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemViewCacheSize(16);
        grid.setItemAnimator(null);
        dockDecoration = new DockDecoration(getResources());
        grid.addItemDecoration(dockDecoration);
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);
        updateDayNightTheme();

        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) grid.post(this::syncDockHeights);
        });

        btnLiveTv = findViewById(R.id.btnLiveTv);
        btnInputs = findViewById(R.id.btnInputs);
        btnSettings = findViewById(R.id.btnSettings);
        if (btnLiveTv != null) {
            btnLiveTv.setOnClickListener(v -> openLiveTv());
            // Long press: pick which input the set-top box is on again.
            btnLiveTv.setOnLongClickListener(v -> {
                showInputs(true);
                return true;
            });
        }
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnWifi.setOnLongClickListener(v -> {
            open(new Intent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        btnSettings.setOnLongClickListener(v -> {
            if (!moduleCards) return false;   // off: a long press does nothing, as before the feature
            showModuleCards();
            return true;
        });

        // Keep focus on the top bar at its ends; the weather widget is display-only and never takes focus.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                // The left end is Live TV, or Inputs, or Wi-Fi on a box without inputs.
                if (btnLiveTv != null && btnLiveTv.getVisibility() == View.VISIBLE) {
                    return v.getId() == R.id.btnLiveTv;
                }
                return v.getId() == R.id.btnInputs
                        || (v.getId() == R.id.btnWifi && btnInputs.getVisibility() != View.VISIBLE);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings) return true;
            return false;
        };
        if (btnLiveTv != null) btnLiveTv.setOnKeyListener(pillKeys);
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Subtle 1.02x scale feedback on focused quick control pills (P3 polish)
        View.OnFocusChangeListener pillFocusScale = (v, focused) -> {
            float s = focused ? 1.02f : 1.0f;
            v.animate().scaleX(s).scaleY(s).setDuration(160).start();
        };
        if (btnLiveTv != null) btnLiveTv.setOnFocusChangeListener(pillFocusScale);
        btnInputs.setOnFocusChangeListener(pillFocusScale);
        btnWifi.setOnFocusChangeListener(pillFocusScale);
        btnSettings.setOnFocusChangeListener(pillFocusScale);

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }

        homeContent = findViewById(R.id.homeContent);
        launcherContent = homeContent;
        topButtons = findViewById(R.id.topButtons);
        isScreensaverActive = false;
        inScreensaver = false;

        initPermanentWallpaperAndScreensaver();

        initWeatherViews();
        updateDayNightTheme();
        resetInactivityTimer();
    }

    /**
     * A theme's full-screen picture behind everything. Decoded once, downsampled to the screen so a 4K
     * JPEG costs a 1080p bitmap at most (about 8 MB), which is the price of a wallpaper on a 1 GB TV.
     */
    private void showWallpaper(File file) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;   // no alpha needed, half the memory
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            if (bmp == null) return;
            android.widget.ImageView wall = new android.widget.ImageView(this);
            wall.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            wall.setImageBitmap(bmp);
            root.addView(wall, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception | OutOfMemoryError e) {
            // A bad or huge picture must never take the home screen down; the plain colour stays.
        }
    }

    private void ensureAccessibilityServiceEnabled() {
        try {
            String myService = getPackageName() + "/" + HomeKeyService.class.getName();
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || !enabled.contains(myService)) {
                String newServices = (enabled == null || enabled.isEmpty()) ? myService : enabled + ":" + myService;
                Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices);
                Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            }
        } catch (Throwable ignored) {
            // Requires WRITE_SECURE_SETTINGS or initial ADB grant; fails gracefully if ungranted
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            if (intent.hasExtra("test_hour")) {
                int h = intent.getIntExtra("test_hour", -1);
                sTestHourOfDay = (h >= 0) ? h : null;
                updateDayNightTheme();
            }
            if (!isScreensaverActive) refreshWeather();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureAccessibilityServiceEnabled();
        if (currentlyRunningPkg != null) {
            exitedAppsTimestamps.put(currentlyRunningPkg, System.currentTimeMillis());
            currentlyRunningPkg = null;
        }
        memoryReaperHandler.removeCallbacks(memoryReaperTask);
        memoryReaperHandler.postDelayed(memoryReaperTask, REAP_POLL_INTERVAL_MS);
        if (getIntent() != null) {
            if (getIntent().hasExtra("test_hour")) {
                int h = getIntent().getIntExtra("test_hour", -1);
                sTestHourOfDay = (h >= 0) ? h : null;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }
        tilesReady = false;
        promptsPaused = false;
        // Coming back from an app must be instant: rebuild the dock only when packages changed while
        // we were away (the package receiver is off then), and play the entrance animation only once.
        int dockRevision = DockPrefs.revision(this);
        if (packagesChangedSinceLastLoad() || dockRevision != loadedDockRevision || adapter.getItemCount() == 0) {
            reloadApps(!dockAnimated);
            dockAnimated = true;
            loadedDockRevision = dockRevision;
        } else {
            restoreFocus();
        }
        updateGreeting();
        updateWifiLabel();
        updateInputsButton();
        updateDayNightTheme();
        handler.postDelayed(minuteTick, MINUTE_MS);
        // Weather is checked every time Home opens, and only then: never while an app is in front,
        // never on a timer, never while the screensaver shows (leaving it with a key checks again).
        if (!isScreensaverActive) refreshWeather();
        if (!isScreensaverActive) {
            resetInactivityTimer();
        }
    }

    /** PackageManager change counter at the last dock build; -1 before the first. */
    private int packageSequence = -1;
    private boolean dockAnimated = false;
    /** DockPrefs revision the tiles were built from; a dock edit bumps it. */
    private int loadedDockRevision = -1;
    private long lastWeatherRequestMs = -WEATHER_DEBOUNCE_MS;
    /** One check per opening: onNewIntent and onResume can both fire for the same Home press. */
    private static final long WEATHER_DEBOUNCE_MS = 5_000L;

    /** True when any app was installed, removed or updated since the dock was last built. */
    private boolean packagesChangedSinceLastLoad() {
        android.content.pm.ChangedPackages changed =
                getPackageManager().getChangedPackages(Math.max(0, packageSequence));
        if (packageSequence < 0) {
            packageSequence = changed != null ? changed.getSequenceNumber() : 0;
            return true;
        }
        if (changed == null) return false;
        packageSequence = changed.getSequenceNumber();
        return true;
    }

    public void onAppLaunched(String pkg) {
        currentlyRunningPkg = pkg;
        exitedAppsTimestamps.remove(pkg); // Active app: clear exit timer
        memoryReaperHandler.removeCallbacks(memoryReaperTask);
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = animate || grid.hasFocus();
        if (restoreFocus) {
            heroShown = false;
            heroName.setAlpha(0f);
            ambient.setAlpha(0f);
        }
        reloadTiles();
        if (animate) grid.scheduleLayoutAnimation();

        if (restoreFocus) {
            // Follow the same app, or the nearest remaining slot when it was removed.
            restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.getItemCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            showCardPrompt();
        }
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    private void reloadTiles() {
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        cards = moduleCards ? CardFeature.run(() -> Cards.discover(this, hiddenApps, Cards.load(Cards.storeFile(this))), none) : none;
        adapter.reload(cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none));
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        if (inScreensaver || isScreensaverActive) {
            tilesReady = true;
            return;
        }
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            showCardPrompt();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        memoryReaperHandler.removeCallbacks(memoryReaperTask);
        handler.removeCallbacks(screensaverRunnable);
        unregisterReceiver(packageReceiver);
        handler.removeCallbacks(minuteTick);
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) trimIdleStreamingApps();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showCardPrompt();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        memoryReaperHandler.removeCallbacks(memoryReaperTask);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    @Override
    public void onBackPressed() {
        if (launcherContent != null && launcherContent.getVisibility() == View.VISIBLE) {
            if (grid != null && lastFocused >= COLUMNS) {
                grid.smoothScrollToPosition(0);
                RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(0);
                if (vh != null) {
                    View card = vh.itemView.findViewById(R.id.card);
                    if (card != null) card.requestFocus();
                }
                if (exploreHint != null) {
                    exploreHint.setText("▾  More Apps");
                    exploreHint.setTextColor(0x66FFFFFF);
                }
                return;
            }
            toggleLauncherApps();
            return;
        }
        // Home screen: Back goes nowhere.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (inScreensaver || isScreensaverActive) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                wakeUpKeyHandled = true;
                exitScreensaver();
            }
            return true; // Consume any remote key to return directly to homescreen
        }
        if (wakeUpKeyHandled) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                wakeUpKeyHandled = false;
                return true; // Consume the trailing ACTION_UP of the wake-up button press
            }
            wakeUpKeyHandled = false;
        }
        resetInactivityTimer();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                toggleLauncherApps();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_TV_INPUT) {
                showInputs();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (inScreensaver || isScreensaverActive) {
            if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                exitScreensaver();
            }
            return true;
        }
        resetInactivityTimer();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent ev) {
        if (inScreensaver || isScreensaverActive) {
            exitScreensaver();
            return true;
        }
        resetInactivityTimer();
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (!inScreensaver && !isScreensaverActive) {
            resetInactivityTimer();
        }
    }

    private void toggleLauncherApps() {
        if (inScreensaver || isScreensaverActive) {
            exitScreensaver();
        } else {
            enterScreensaver();
        }
    }

    private void initPermanentWallpaperAndScreensaver() {
        wallpaper = findViewById(R.id.wallpaper);
        wallpaperScrim = findViewById(R.id.wallpaperScrim);
        homeContent = findViewById(R.id.homeContent);
        screensaverOverlay = findViewById(R.id.screensaverOverlay);
        screensaverHud = findViewById(R.id.screensaverHud);
        dashboard = new ScreensaverDashboard(this);

        // Ensure permanent wallpaper is loaded dynamically from TV storage
        if (wallpaper != null) {
            WallpaperHelper.loadPermanentWallpaper(this, wallpaper);
        }
    }

    private void enterScreensaver() {
        if (inScreensaver) return;
        inScreensaver = true;
        isScreensaverActive = true;
        if (homeContent != null) {
            homeContent.animate().alpha(0f).setDuration(600)
                    .withEndAction(() -> homeContent.setVisibility(View.INVISIBLE)).start();
        }

        if (screensaverOverlay != null && screensaverHud != null) {
            screensaverOverlay.setClickable(true);
            screensaverOverlay.setFocusable(true);
            screensaverOverlay.setOnClickListener(v -> exitScreensaver());
            screensaverOverlay.setVisibility(View.VISIBLE);
            if (dashboard != null) dashboard.onShow();

            screensaverOverlay.animate().alpha(1f).setDuration(800).start();
        }
        // No weather check here: the screensaver shows the last known weather.
    }

    private void exitScreensaver() {
        if (!inScreensaver && !isScreensaverActive) return;
        if (dashboard != null) dashboard.onHide();
        inScreensaver = false;
        isScreensaverActive = false;
        if (screensaverOverlay != null) {
            screensaverOverlay.setClickable(false);
            screensaverOverlay.setFocusable(false);
            screensaverOverlay.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> screensaverOverlay.setVisibility(View.GONE)).start();
        }
        if (homeContent != null) {
            homeContent.setVisibility(View.VISIBLE);
            homeContent.animate().alpha(1f).setDuration(250).start();
        }
        restoreFocus();
        resetInactivityTimer();
        refreshWeather(); // a key press woke Home up: check the weather now
    }

    private void showScreensaver(boolean show) {
        if (show) enterScreensaver();
        else exitScreensaver();
    }

    private void initWeatherViews() {
        weatherWidget = findViewById(R.id.weatherWidget);
        ivTodayIcon = findViewById(R.id.ivTodayIcon);
        tvTodayTemp = findViewById(R.id.tvTodayTemp);
        tvTodayCondition = findViewById(R.id.tvTodayCondition);
        tvRainChance = findViewById(R.id.tvRainChance);
        weatherNow = findViewById(R.id.weatherNow);
        tvFeels = findViewById(R.id.tvFeels);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWind = findViewById(R.id.tvWind);
        tvAqiValue = findViewById(R.id.tvAqiValue);
        tvAqiLabel = findViewById(R.id.tvAqiLabel);
        hourlyRow = findViewById(R.id.hourlyRow);
    }

    private void renderWeather(WeatherHelper.WeatherData data) {
        if (data == null || weatherWidget == null) return;
        long now = System.currentTimeMillis();

        weatherWidget.setVisibility(View.VISIBLE);
        weatherNow.setVisibility(View.VISIBLE);
        tvRainChance.setVisibility(View.VISIBLE);

        // Mirror of the clock: icon + temperature, and a subtitle like the date:
        // "Chanakyapuri  ·  Clear  ·  H 31°  L 21°"
        tvTodayTemp.setText(Math.round(data.currentTempC) + "°");
        ivTodayIcon.setImageResource(iconForWeatherCode(data.currentWeatherCode, data.isDay));
        String city = LocationHelper.cityName(this);
        int comma = city.indexOf(',');
        if (comma > 0) city = city.substring(0, comma);
        StringBuilder sub = new StringBuilder();
        if (!city.isEmpty()) sub.append(city).append("  ·  ");
        sub.append(WeatherHelper.describe(data.currentWeatherCode));
        if (data.daily != null && data.daily.length > 0) {
            sub.append("  ·  H ").append(Math.round(data.daily[0].maxC)).append("°  L ")
               .append(Math.round(data.daily[0].minC)).append('°');
        }
        tvTodayCondition.setText(sub);
        showUpdated(findViewById(R.id.tvWeatherUpdated), data.fetchedAtMs);
        tvTodayCondition.setCompoundDrawablesRelativeWithIntrinsicBounds(
                city.isEmpty() ? 0 : R.drawable.ic_location_arrow, 0, 0, 0);
        tvTodayCondition.setCompoundDrawableTintList(
                android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary)));

        // Next six hours
        hourlyRow.removeAllViews();
        java.text.SimpleDateFormat hourFmt = new java.text.SimpleDateFormat("h a", Locale.getDefault());
        boolean first = true;
        for (WeatherHelper.WeatherData.Hourly h : data.upcomingHours(now, 6)) {
            View cell = LayoutInflater.from(this).inflate(R.layout.weather_hour_item, hourlyRow, false);
            ((TextView) cell.findViewById(R.id.tvHourLabel)).setText(hourFmt.format(new java.util.Date(h.timeMs)).toUpperCase(Locale.getDefault()));
            ((ImageView) cell.findViewById(R.id.ivHourIcon)).setImageResource(iconForWeatherCode(h.weatherCode, h.isDay));
            ((TextView) cell.findViewById(R.id.tvHourTemp)).setText(Math.round(h.tempC) + "°");
            TextView rain = cell.findViewById(R.id.tvHourRain);
            if (h.rainChancePercent >= 20) {
                rain.setText(h.rainChancePercent + "%");
                rain.setVisibility(View.VISIBLE);
            }
            hourlyRow.addView(cell);
            first = false;
        }
        hourlyRow.setVisibility(first ? View.GONE : View.VISIBLE);

        // Rain in the next 24 hours: when, and how likely. Only future hours count.
        WeatherHelper.WeatherData.Hourly rain = data.rainAlert(now);
        if (rain != null) {
            java.util.Calendar at = java.util.Calendar.getInstance();
            at.setTimeInMillis(rain.timeMs);
            java.util.Calendar today = java.util.Calendar.getInstance();
            boolean tomorrow = at.get(java.util.Calendar.DAY_OF_YEAR) != today.get(java.util.Calendar.DAY_OF_YEAR);
            String when = (tomorrow ? "tomorrow around " : "likely around ") + hourFmt.format(new java.util.Date(rain.timeMs)).toUpperCase(Locale.getDefault());
            tvRainChance.setText("Rain " + when + "  ·  " + rain.rainChancePercent + "%");
            tvRainChance.setTextColor(getColor(R.color.rain_blue));
            tvRainChance.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.rain_blue)));
        } else {
            tvRainChance.setText(R.string.weather_no_rain);
            tvRainChance.setTextColor(getColor(R.color.text_secondary));
            tvRainChance.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary)));
        }

        // Stats: icon + label above, value below; AQI value takes its category colour.
        tvFeels.setText(Math.round(data.feelsLikeC) + "°");
        tvHumidity.setText(data.humidity >= 0 ? data.humidity + "%" : "–");
        tvWind.setText(data.windKmh >= 0 ? Math.round(data.windKmh) + " km/h" : "–");
        if (data.aqi >= 0) {
            tvAqiValue.setText(String.valueOf(data.aqi));
            tvAqiValue.setTextColor(aqiColor(data.aqi));
            tvAqiLabel.setText(WeatherHelper.aqiLabel(data.aqi));
        } else {
            tvAqiValue.setText("–");
            tvAqiValue.setTextColor(getColor(R.color.text_primary));
            tvAqiLabel.setText("");
        }

        if (dashboard != null) dashboard.render(data);
    }

    private int iconForWeatherCode(int code) {
        return iconForWeatherCode(code, true);
    }

    static int iconForWeatherCode(int code, boolean day) {
        if (code <= 1) return day ? R.drawable.ic_sunny : R.drawable.ic_moon;
        if (code <= 2) return day ? R.drawable.ic_partly_cloudy : R.drawable.ic_partly_cloudy_night;
        if (code <= 48) return R.drawable.ic_cloudy;
        if (code <= 67 || (code >= 80 && code <= 82)) return R.drawable.ic_rain;
        if (code >= 95) return R.drawable.ic_storm;
        return R.drawable.ic_cloudy;
    }

    /** "Updated 9:41 AM" under the weather; orange when the data is over two hours old. */
    static void showUpdated(TextView view, long fetchedAtMs) {
        if (view == null) return;
        if (fetchedAtMs <= 0) {
            view.setVisibility(View.GONE);
            return;
        }
        long now = System.currentTimeMillis();
        view.setText(WeatherHelper.updatedLabel(fetchedAtMs, now, Locale.getDefault()));
        boolean stale = now - fetchedAtMs > WeatherHelper.STALE_AFTER_MS;
        view.setTextColor(view.getContext().getColor(stale ? R.color.aqi_orange : R.color.text_tertiary));
        view.setVisibility(View.VISIBLE);
    }

    private int aqiColor(int aqi) {
        return aqiColor(this, aqi);
    }

    static int aqiColor(Context ctx, int aqi) {
        if (aqi <= 50) return ctx.getColor(R.color.aqi_green);
        if (aqi <= 100) return ctx.getColor(R.color.aqi_yellow);
        if (aqi <= 150) return ctx.getColor(R.color.aqi_orange);
        if (aqi <= 200) return ctx.getColor(R.color.aqi_red);
        return ctx.getColor(R.color.aqi_purple);
    }

    private String conditionLabel(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mainly Clear";
        if (code == 2) return "Partly Cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Foggy";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "Partly Cloudy";
    }

    private void refreshWeather() {
        if (SystemClock.elapsedRealtime() - lastWeatherRequestMs < WEATHER_DEBOUNCE_MS) return;
        lastWeatherRequestMs = SystemClock.elapsedRealtime();
        LocationHelper.detectLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onLocation(double lat, double lon) {
                if (isFinishing() || isDestroyed()) return;
                new WeatherHelper(MainActivity.this).fetchWeather(lat, lon, true, new WeatherHelper.WeatherCallback() {
                    @Override
                    public void onResult(WeatherHelper.WeatherData data) {
                        if (isFinishing() || isDestroyed()) return;
                        renderWeather(data);
                    }

                    @Override
                    public void onError() {
                        // Handled gracefully via cache
                    }
                });
            }

            @Override
            public void onError() {
                // No location known at all (first boot offline): leave the weather hidden until one is.
            }
        });
    }

    private void resetInactivityTimer() {
        handler.removeCallbacks(screensaverRunnable);
        if (!inScreensaver && homeContent != null && homeContent.getVisibility() == View.VISIBLE) {
            handler.postDelayed(screensaverRunnable, AMBIENT_IDLE_TIMEOUT_MS);
        }
    }

    // ---- tile focus ------------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        if (position < COLUMNS) {
            // Dock: smoothly return to top offset so main 4 apps sit cleanly in frame
            int offset = grid.computeVerticalScrollOffset();
            if (offset > 0) {
                grid.smoothScrollBy(0, -offset);
            }
            showHero(position, card.getTop());
            if (exploreHint != null) {
                exploreHint.setText("▾  More Apps");
                exploreHint.setTextColor(0x66FFFFFF);
            }
        } else {
            hideHero();
            View item = (View) card.getParent();
            if (item != null) {
                Rect want = new Rect(0, 0, item.getWidth(), item.getHeight());
                grid.requestChildRectangleOnScreen(item, want, false);
            }
            if (exploreHint != null) {
                exploreHint.setText("▲  Main Apps");
                exploreHint.setTextColor(0x99FFFFFF);
            }
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (heroName != null) {
            heroName.setVisibility(View.GONE);
            heroName.setAlpha(0f);
        }
        if (!heroShown) {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (changed) {
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        if (ambientDrawable != null) {
            ambientDrawable.updateChromaticField(color, 0, isDayMode);
        }
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    // ---- navigation & collapsible dock -------------------------------------------------------------

    private View dockContainer;
    private ValueAnimator dockHeightAnimator;

    /** Height of one dock row for the current grid width (tile + row padding). */
    private int dockRowHeightPx() {
        int inner = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        int tileW = AppAdapter.tileWidthPx(inner, COLUMNS, density);
        return AppAdapter.rowHeightPx(AppAdapter.tileHeightPx(tileW), getResources());
    }

    /**
     * Gives the grid room for exactly two rows and the container one row (two when expanded), so
     * rows are never scrolled half out of view and no stray shelf edge shows above the dock.
     */
    private void syncDockHeights() {
        if (dockContainer == null) dockContainer = findViewById(R.id.dockContainer);
        if (dockContainer == null || grid == null) return;
        int row = dockRowHeightPx();
        ViewGroup.LayoutParams glp = grid.getLayoutParams();
        if (glp.height != 2 * row) {
            glp.height = 2 * row;
            grid.setLayoutParams(glp);
        }
        if (dockHeightAnimator != null && dockHeightAnimator.isRunning()) return;
        ViewGroup.LayoutParams clp = dockContainer.getLayoutParams();
        int want = adapter != null && adapter.isExpanded() ? 2 * row : row;
        if (clp.height != want) {
            clp.height = want;
            dockContainer.setLayoutParams(clp);
        }
    }

    private void animateDock(boolean expand, Runnable onEnd) {
        if (dockContainer == null) {
            dockContainer = findViewById(R.id.dockContainer);
        }
        if (dockContainer == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        int collapsedH = dockRowHeightPx();
        int expandedH = 2 * collapsedH;

        // The second row grows into the space the top area uses, so fade that area out while open.
        View topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            topBar.animate().alpha(expand ? 0f : 1f)
                    .translationY(expand ? -24 * density : 0f)
                    .setDuration(220).start();
        }

        if (dockHeightAnimator != null && dockHeightAnimator.isRunning()) {
            dockHeightAnimator.cancel();
        }
        int from = dockContainer.getHeight() > 0 ? dockContainer.getHeight() : (expand ? collapsedH : expandedH);
        int to = expand ? expandedH : collapsedH;

        dockHeightAnimator = ValueAnimator.ofInt(from, to);
        dockHeightAnimator.setDuration(200);
        dockHeightAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        dockHeightAnimator.addUpdateListener(anim -> {
            int val = (int) anim.getAnimatedValue();
            ViewGroup.LayoutParams lp = dockContainer.getLayoutParams();
            if (lp != null) {
                lp.height = val;
                dockContainer.setLayoutParams(lp);
            }
        });
        dockHeightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (onEnd != null) onEnd.run();
            }
        });
        dockHeightAnimator.start();
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return position % COLUMNS == 0;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return position % COLUMNS == COLUMNS - 1 || position == count - 1;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (position < COLUMNS) {
                // On Row 1: reveal Row 2 with upward slide & fade, then move focus to matching column
                if (!adapter.isExpanded()) {
                    adapter.setExpanded(true);
                    animateDock(true, null);
                }
                int target = position + COLUMNS;
                grid.post(() -> focusTile(target));
                return true;
            } else {
                // On Row 2: Down does nothing so focus stays in the dock
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position >= COLUMNS) {
                // On Row 2: Up returns focus to Row 1 and collapses Row 2
                int target = position - COLUMNS;
                focusTile(target);
                animateDock(false, () -> adapter.setExpanded(false));
                return true;
            } else {
                // On Row 1: Up moves focus to top buttons
                if (adapter.isExpanded()) {
                    animateDock(false, () -> adapter.setExpanded(false));
                }
                if (btnLiveTv != null) {
                    btnLiveTv.requestFocus();
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) {
            btnWifi.requestFocus();
            return;
        }
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    private void updateDayNightTheme() {
        boolean forceObsidian = getResources().getBoolean(R.bool.force_obsidian_theme);
        int hour = (sTestHourOfDay != null) ? sTestHourOfDay : Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean day = !forceObsidian && (hour >= 6 && hour < 18);
        isDayMode = day;

        // Visual tokens (Cinema Quiet Luxury — OLED / Midnight Glass)
        // High contrast against photographic wallpaper, zero milky or whitish haze
        // All values come from colors.xml / dimens.xml so layouts and code can't drift apart.
        int bgColor = getColor(R.color.night_bg);
        int primaryText = getColor(R.color.text_primary);
        int secondaryText = getColor(R.color.text_secondary);
        int tertiaryText = getColor(R.color.text_tertiary);
        int warmAccent = getColor(R.color.accent_warm);
        int glassCard = getColor(R.color.night_glass_surface);
        int glassBorder = getColor(R.color.night_glass_border);
        float radiusCard = getResources().getDimension(R.dimen.radius_card);

        root.setBackgroundColor(bgColor);
        if (wallpaperScrim != null) {
            wallpaperScrim.setBackgroundResource(R.drawable.apple_tv_vignette);
            wallpaperScrim.animate().alpha(1.0f).setDuration(300).start();
        }
        if (clock != null) clock.setTextColor(primaryText);
        if (ampm != null) ampm.setTextColor(secondaryText);
        if (date != null) date.setTextColor(secondaryText);
        if (greeting != null) greeting.setTextColor(warmAccent);
        if (clockDivider != null) clockDivider.setBackgroundColor(glassBorder);
        if (heroName != null) heroName.setTextColor(primaryText);
        if (ambientDrawable != null && ambientColor != 0) {
            ambientDrawable.updateChromaticField(ambientColor, 0, false);
        }
        if (brandMark != null) brandMark.setTextColor(getColor(R.color.text_quiet));
        if (exploreHint != null) exploreHint.setVisibility(View.GONE);

        // Clock, weather and the control pills draw their own looks from XML (text on the photo,
        // frosted pills, weather focus plate), so nothing is painted over them here.

    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            } else if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) {
                        text = ssid;
                        currentSsid = ssid;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        boolean vpnDown = !vpn && online && !vpnApp.isEmpty();
        if (vpn || vpnDown) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            String badge = getString(vpn ? R.string.vpn : R.string.vpn_off);
            VpnBadge span = new VpnBadge(vpn ? getDrawable(R.drawable.ic_lock) : null, density);
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The total paddings
            // include the Wi-Fi icon.
            TextPaint paint = btnWifi.getPaint();
            float room = btnWifi.getMaxWidth() - btnWifi.getTotalPaddingStart() - btnWifi.getTotalPaddingEnd()
                    - span.getSize(paint, badge, 0, badge.length(), null) - paint.measureText(" ") - 2 * density;
            text = TextUtils.ellipsize(text, paint, Math.max(0, room), TextUtils.TruncateAt.END).toString();
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(span, text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnWifi.setText(label);
        } else {
            // Ellipsize the SSID (variable) in code, not the static "Wi-Fi" label in XML.
            // The Wi-Fi label and Ethernet / Offline strings are short and must never be clipped.
            if (!text.equals(getString(R.string.wifi))
                    && !text.equals(getString(R.string.ethernet))
                    && !text.equals(getString(R.string.offline))) {
                TextPaint paint = btnWifi.getPaint();
                // Allow up to 120dp worth of space for a long SSID.
                float maxPx = 120 * density;
                text = TextUtils.ellipsize(text, paint, maxPx, TextUtils.TruncateAt.END).toString();
            }
            btnWifi.setText(text);
        }
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
    }

    /** The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. */
    private void openNetworkPill() {
        if (currentSsid != null && !currentSsid.isEmpty() && !currentSsid.equals(getString(R.string.wifi))) {
            Toast.makeText(this, "Wi-Fi: " + currentSsid, Toast.LENGTH_SHORT).show();
        }
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        open(i != null ? i : new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    private static final String PREFS_TV_BOX = "tv_box";
    private static final String KEY_TV_BOX_INPUT = "input_id";

    /**
     * The set-top box button: switches to the HDMI input the viewer chose for their box. The first
     * press (or a long press) shows the input list in "choose" mode and remembers the pick.
     */
    private void openLiveTv() {
        String saved = getSharedPreferences(PREFS_TV_BOX, MODE_PRIVATE).getString(KEY_TV_BOX_INPUT, null);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (saved != null && tim != null && tim.getTvInputInfo(saved) != null) {
            onAppLaunched("com.mitv.livetv");
            openInput(saved);
            return;
        }
        showInputs(true);
    }

    private void rememberTvBoxInput(String inputId) {
        getSharedPreferences(PREFS_TV_BOX, MODE_PRIVATE).edit().putString(KEY_TV_BOX_INPUT, inputId).apply();
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }


    /** Switches to a passthrough input (HDMI, AV). The standard way is ACTION_VIEW on its channel URI,
     *  which the Live TV app handles; where nothing takes that, the configured `input_action` is sent
     *  with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (liveTvPkg != null && !liveTvPkg.isEmpty()) {
            view.setPackage(liveTvPkg);
        }
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            view.setPackage(null);
            try {
                startActivity(view);
                return;
            } catch (ActivityNotFoundException ex) {
                if (inputAction.isEmpty()) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        showInputs(false);
    }

    /** @param chooseTvBox list HDMI/AV only; the picked input becomes the set-top box button's target */
    private void showInputs(boolean chooseTvBox) {
        View anchor = findViewById(chooseTvBox ? R.id.btnLiveTv : R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = glassPopup(panel, width);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state, () -> {
                    if (chooseTvBox) rememberTvBoxInput(id);
                    openInput(id);
                });
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = chooseTvBox ? null : liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        dropIn(popup, panel, anchor, Gravity.START);
        rows.getChildAt(0).requestFocus();
    }

    private PopupWindow glassPopup(View panel, int width) {
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ImageView iconView = row.findViewById(R.id.icon);
        TextView titleView = row.findViewById(R.id.title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);

        // Intelligent peripheral identification (PS5, Xbox, Switch, Apple TV)
        String upper = title.toUpperCase(Locale.ROOT);
        if (upper.contains("PS5") || upper.contains("PLAYSTATION")) {
            title = title + " · PlayStation";
        } else if (upper.contains("XBOX")) {
            title = title + " · Xbox";
        } else if (upper.contains("SWITCH")) {
            title = title + " · Switch";
        } else if (upper.contains("APPLE TV")) {
            title = title + " · Apple TV";
        }

        iconView.setImageResource(iconRes);
        titleView.setText(title);

        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
            dot.setBackgroundTintList(ColorStateList.valueOf(0xFF00E676));
        } else if (state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY) {
            status.setText(R.string.standby);
            dot.setVisibility(View.VISIBLE);
            dot.setBackgroundTintList(ColorStateList.valueOf(0xFFFFB300));
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- module cards ----------------------------------------------------------------------------

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }
}
