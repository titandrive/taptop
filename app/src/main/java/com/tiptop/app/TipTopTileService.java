package com.tiptop.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class TipTopTileService extends TileService {
    private SharedPreferences prefs;
    private boolean listening;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (preferences, key) -> {
        if ("tiptop_enabled".equals(key)) refreshTile();
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
    }

    @Override public void onStartListening() {
        super.onStartListening();
        if (!listening) {
            prefs.registerOnSharedPreferenceChangeListener(changed);
            listening = true;
        }
        refreshTile();
    }

    @Override public void onStopListening() {
        if (listening) {
            prefs.unregisterOnSharedPreferenceChangeListener(changed);
            listening = false;
        }
        super.onStopListening();
    }

    @Override public void onDestroy() {
        if (listening) prefs.unregisterOnSharedPreferenceChangeListener(changed);
        super.onDestroy();
    }

    @Override public void onClick() {
        super.onClick();
        boolean enabled = !prefs.getBoolean("tiptop_enabled", true);
        prefs.edit().putBoolean("tiptop_enabled", enabled).apply();
        sendBroadcast(new Intent(TopService.ACTION_UPDATE).setPackage(getPackageName()));
        refreshTile();
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = prefs.getBoolean("tiptop_enabled", true);
        tile.setLabel("TipTop");
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setContentDescription(enabled ? "TipTop on" : "TipTop off");
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(enabled ? "On" : "Off");
        tile.updateTile();
    }
}
