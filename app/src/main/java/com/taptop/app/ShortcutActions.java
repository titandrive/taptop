package com.taptop.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

final class ShortcutActions {
    static final String ACTION_TOGGLE = "com.taptop.app.action.TOGGLE";
    static final String ACTION_SCROLL_TO_TOP = "com.taptop.app.action.SCROLL_TO_TOP";

    static final String ACTION_TURN_ON = "com.taptop.app.action.TURN_ON";
    static final String ACTION_TURN_OFF = "com.taptop.app.action.TURN_OFF";

    private ShortcutActions() {}

    static void perform(Context context, String action) {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (ACTION_TOGGLE.equals(action) || ACTION_TURN_ON.equals(action) || ACTION_TURN_OFF.equals(action)) {
            boolean current = prefs.getBoolean("taptop_enabled", true);
            boolean enabled = ACTION_TOGGLE.equals(action) ? !current : ACTION_TURN_ON.equals(action);
            if (enabled && !TopService.connected) {
                Toast.makeText(context, "Enable TapTop in accessibility settings first", Toast.LENGTH_SHORT).show();
                return;
            }
            // Repeating ON/OFF must not toggle state or interrupt an active scroll.
            if (enabled == current) return;
            prefs.edit().putBoolean("taptop_enabled", enabled).apply();
            context.sendBroadcast(new Intent(TopService.ACTION_UPDATE).setPackage(context.getPackageName()));
            if (prefs.getBoolean("haptics", true)) Haptics.click(context);
        } else if (ACTION_SCROLL_TO_TOP.equals(action)) {
            if (!TopService.connected) {
                Toast.makeText(context, "Enable TapTop in accessibility settings first", Toast.LENGTH_SHORT).show();
            } else if (!prefs.getBoolean("taptop_enabled", true)) {
                Toast.makeText(context, "Turn TapTop on first", Toast.LENGTH_SHORT).show();
            } else {
                context.sendBroadcast(new Intent(TopService.ACTION_SCROLL_TO_TOP).setPackage(context.getPackageName()));
                if (prefs.getBoolean("haptics", true)) Haptics.click(context);
            }
        }
    }
}
