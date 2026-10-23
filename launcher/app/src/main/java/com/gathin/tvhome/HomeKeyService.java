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
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int scan = event.getScanCode();

        boolean isYouTubeKey = (code == KeyEvent.KEYCODE_BUTTON_4 || code == 258 || code == 229 || scan == 403 || scan == 404);
        if (isYouTubeKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.google.android.youtube.tv");
            }
            return true;
        }

        boolean isNetflixKey = (code == KeyEvent.KEYCODE_BUTTON_2 || scan == 402);
        if (isNetflixKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.netflix.ninja");
            }
            return true;
        }

        boolean isPrimeKey = (code == KeyEvent.KEYCODE_BUTTON_3 || scan == 405);
        if (isPrimeKey) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchApp("com.amazon.amazonvideo.livingroom");
            }
            return true;
        }

        boolean isHomeKey = (code == KeyEvent.KEYCODE_ALL_APPS);
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

    private void launchApp(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) {
            i = getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                startActivity(i);
            } catch (Exception ignored) {
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
