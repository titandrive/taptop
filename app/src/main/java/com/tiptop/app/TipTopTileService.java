package com.tiptop.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class TipTopTileService extends TileService {
    private SharedPreferences prefs;
    private boolean listening;
    private final Runnable connectionChanged = this::refreshTile;
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
            TopService.addConnectionListener(connectionChanged);
            listening = true;
        }
        refreshTile();
    }

    @Override public void onStopListening() {
        if (listening) {
            prefs.unregisterOnSharedPreferenceChangeListener(changed);
            TopService.removeConnectionListener(connectionChanged);
            listening = false;
        }
        super.onStopListening();
    }

    @Override public void onDestroy() {
        if (listening) prefs.unregisterOnSharedPreferenceChangeListener(changed);
        TopService.removeConnectionListener(connectionChanged);
        super.onDestroy();
    }

    @Override public void onClick() {
        super.onClick();
        if (!TopService.connected) { refreshTile(); return; }
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
        tile.setState(!TopService.connected ? Tile.STATE_UNAVAILABLE
                : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setContentDescription(!TopService.connected ? "TipTop: accessibility required"
                : enabled ? "TipTop on" : "TipTop off");
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(!TopService.connected ? "Accessibility required" : enabled ? "On" : "Off");
        tile.updateTile();
    }
}
