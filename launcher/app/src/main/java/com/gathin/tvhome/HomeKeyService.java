package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Global hardware remote key interceptor.
 * Directly launches the requested app (Hotstar, YouTube, Netflix, Prime Video, Home, Settings, Live TV)
 * with zero latency, even when any other app is actively playing video or running in the foreground.
 */
public class HomeKeyService extends AccessibilityService {

    private static final String TAG = "HomeKeyService";
    private static final int LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;

    private long lastActionTime = 0;
    private int lastActionCode = -1;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        android.util.Log.d(TAG, "HomeKeyService connected with FLAG_REQUEST_FILTER_KEY_EVENTS");
    }

    private boolean checkDebounce(int code) {
        long now = SystemClock.uptimeMillis();
        if (code == lastActionCode && (now - lastActionTime < 350)) {
            return false;
        }
        lastActionTime = now;
        lastActionCode = code;
        return true;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int scan = event.getScanCode();
        int action = event.getAction();

        android.util.Log.d(TAG, "onKeyEvent: code=" + code + ", scan=" + scan + ", action=" + action + ", repeat=" + event.getRepeatCount());

        boolean isSettingsKey = (code == KeyEvent.KEYCODE_SETTINGS || scan == 384);
        if (isSettingsKey) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (checkDebounce(code)) {
                    launchSettings();
                }
            }
            return true;
        }

        boolean isLiveTvKey = (code == KeyEvent.KEYCODE_TV
                || code == KeyEvent.KEYCODE_GUIDE
                || code == KeyEvent.KEYCODE_PROG_RED
                || code == KeyEvent.KEYCODE_TV_INPUT
                || code == KeyEvent.KEYCODE_CAPTIONS
                || scan == 377
                || scan == 398
                || scan == 400);
        if (isLiveTvKey) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (checkDebounce(code)) {
                    launchLiveTv();
                }
            }
            return true;
        }

        boolean isHotstarKey = (code == KeyEvent.KEYCODE_BUTTON_5
                || code == KeyEvent.KEYCODE_BUTTON_6
                || code == 192
                || code == 193
                || code == 259
                || code == 260
                || code == 261
                || scan == 406
                || scan == 407
                || scan == 416
                || scan == 435);
        if (isHotstarKey) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (checkDebounce(code)) {
                    launchHotstar();
                }
            }
            return true;
        }

        boolean isYouTubeKey = (code == KeyEvent.KEYCODE_BUTTON_4
                || code == 191
                || code == 258
                || code == 229
                || code == 230
                || scan == 403
                || scan == 404
                || scan == 418
                || scan == 440);
        if (isYouTubeKey) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (checkDebounce(code)) {
                    launchYouTube();
                }
            }
            return true;
        }

        boolean isNetflixKey = (code == KeyEvent.KEYCODE_BUTTON_2
                || code == 189
                || code == 256
                || scan == 402
                || scan == 417
                || scan == 438
                || scan == 442);
        if (isNetflixKey) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (checkDebounce(code)) {
                    launchNetflix();
                }
            }
            return true;
        }

        boolean isPrimeKey = (code == KeyEvent.KEYCODE_BUTTON_3
                || code == 190
                || code == 257
                || scan == 405
                || scan == 425
                || scan == 439);
        if (isPrimeKey) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (checkDebounce(code)) {
                    launchPrime();
                }
            }
            return true;
        }

        boolean isHomeKey = (code == KeyEvent.KEYCODE_ALL_APPS
                || code == KeyEvent.KEYCODE_HOME
                || scan == 172
                || scan == 102);
        boolean isPatchWallKey = (code == KeyEvent.KEYCODE_F1
                || code == KeyEvent.KEYCODE_BUTTON_1
                || code == KeyEvent.KEYCODE_APP_SWITCH
                || code == KeyEvent.KEYCODE_WINDOW
                || scan == 401
                || scan == 168
                || scan == 419);

        if (isHomeKey || isPatchWallKey) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (checkDebounce(code)) {
                    launchHome();
                }
            }
            return true;
        }

        return super.onKeyEvent(event);
    }

    private void launchHotstar() {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("in.startv.hotstar", "com.hotstar.MainActivity"))
                .addFlags(LAUNCH_FLAGS);
        try {
            startActivity(i);
            return;
        } catch (Exception e) {
            android.util.Log.w(TAG, "Direct Hotstar launch failed, falling back", e);
        }
        launchFallback("in.startv.hotstar");
    }

    private void launchYouTube() {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.google.android.youtube.tv", "com.google.android.apps.youtube.tv.activity.ShellActivity"))
                .addFlags(LAUNCH_FLAGS);
        try {
            startActivity(i);
            return;
        } catch (Exception e) {
            android.util.Log.w(TAG, "Direct YouTube launch failed, falling back", e);
        }
        launchFallback("com.google.android.youtube.tv", "com.google.android.youtube");
    }

    private void launchNetflix() {
        try {
            Intent keyIntent = new Intent("com.netflix.action.NETFLIX_KEY_START")
                    .setPackage("com.netflix.ninja")
                    .addFlags(LAUNCH_FLAGS);
            startActivity(keyIntent);
            return;
        } catch (Exception ignored) {
        }

        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.netflix.ninja", "com.netflix.ninja.MainActivity"))
                .addFlags(LAUNCH_FLAGS);
        try {
            startActivity(i);
            return;
        } catch (Exception e) {
            android.util.Log.w(TAG, "Direct Netflix launch failed, falling back", e);
        }
        launchFallback("com.netflix.ninja", "com.netflix.mediaclient");
    }

    private void launchPrime() {
        try {
            Intent buttonIntent = new Intent("com.amazon.amazonvideo.livingroom.AMAZON_BUTTON")
                    .setPackage("com.amazon.amazonvideo.livingroom")
                    .addFlags(LAUNCH_FLAGS);
            startActivity(buttonIntent);
            return;
        } catch (Exception ignored) {
        }

        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.amazon.amazonvideo.livingroom", "com.amazon.ignition.IgnitionActivity"))
                .addFlags(LAUNCH_FLAGS);
        try {
            startActivity(i);
            return;
        } catch (Exception e) {
            android.util.Log.w(TAG, "Direct Prime launch failed, falling back", e);
        }
        launchFallback("com.amazon.amazonvideo.livingroom");
    }

    private void launchLiveTv() {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.mitv.tvhome.mitvplus", "com.mitv.tvhome.mitvplus.app.BannerLogoFree"))
                .addFlags(LAUNCH_FLAGS);
        try {
            startActivity(i);
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName("com.mitv.livetv", "com.mitv.livetv.tv.MainActivity"))
                    .addFlags(LAUNCH_FLAGS));
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent("com.android.tv.action.VIEW_INPUTS").addFlags(LAUNCH_FLAGS));
            return;
        } catch (Exception ignored) {
        }
        launchFallback("com.mitv.livetv", "com.google.android.tv");
    }

    private void launchSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(LAUNCH_FLAGS));
        } catch (Exception ignored) {
        }
    }

    private void launchHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(LAUNCH_FLAGS);
            startActivity(home);
        } catch (Exception ignored) {
        }
    }

    private void launchFallback(String... packages) {
        for (String pkg : packages) {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) {
                i = getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (i != null) {
                i.addFlags(LAUNCH_FLAGS);
                try {
                    startActivity(i);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
