package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new android.accessibilityservice.AccessibilityServiceInfo();
        }
        info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        android.util.Log.d("HomeKeyService", "HomeKeyService connected with FLAG_REQUEST_FILTER_KEY_EVENTS");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int scan = event.getScanCode();

        android.util.Log.d("HomeKeyService", "Intercepted key: code=" + code + ", scan=" + scan + ", action=" + event.getAction());

        boolean isSettingsKey = (code == KeyEvent.KEYCODE_SETTINGS || scan == 384);
        if (isSettingsKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            }
            return true;
        }

        boolean isLiveTvKey = (code == KeyEvent.KEYCODE_TV || code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_PROG_RED);
        if (isLiveTvKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.mitv.livetv", "com.google.android.tv");
            }
            return true;
        }

        boolean isHotstarKey = (code == KeyEvent.KEYCODE_BUTTON_5 || code == KeyEvent.KEYCODE_BUTTON_6 || code == 192 || code == 193 || code == 259 || code == 260 || code == 261 || scan == 406 || scan == 407 || scan == 416);
        if (isHotstarKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("in.startv.hotstar");
            }
            return true;
        }

        boolean isYouTubeKey = (code == KeyEvent.KEYCODE_BUTTON_4 || code == 191 || code == 258 || code == 229 || scan == 403 || scan == 404 || scan == 418);
        if (isYouTubeKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.google.android.youtube.tv", "com.google.android.youtube");
            }
            return true;
        }

        boolean isNetflixKey = (code == KeyEvent.KEYCODE_BUTTON_2 || code == 189 || scan == 402 || scan == 417);
        if (isNetflixKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.netflix.ninja", "com.netflix.mediaclient");
            }
            return true;
        }

        boolean isPrimeKey = (code == KeyEvent.KEYCODE_BUTTON_3 || code == 190 || scan == 405 || scan == 425);
        if (isPrimeKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.amazon.amazonvideo.livingroom");
            }
            return true;
        }

        boolean isHomeKey = (code == KeyEvent.KEYCODE_ALL_APPS || code == KeyEvent.KEYCODE_HOME || scan == 172);
        boolean isPatchWallKey = (code == KeyEvent.KEYCODE_F1 || code == KeyEvent.KEYCODE_BUTTON_1 || scan == 401);

        if (!isHomeKey && !isPatchWallKey) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) {
            // Apple TV long-press Home parity: force return straight to home, clearing foreground activity
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(home);
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    private void launchApp(String... packages) {
        for (String pkg : packages) {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) {
                i = getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (i != null) {
                // Single-focus RAM management: kill all other streaming apps before launching target
                ProcessManager.freeMemoryForFocusedApp(this, pkg);

                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
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
