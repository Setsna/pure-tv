package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Opens the TV's Settings for a remote button the firmware turns into an intent of its own. Philips
 * (TP Vision) sends org.droidtv.leanbackcustomizer.PhilipsDrawer for the app-drawer key; with the
 * Philips drawer disabled, this answers it instead (see the intent filter in the manifest).
 */
public class SettingsKeyActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
