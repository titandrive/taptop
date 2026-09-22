package com.taptop.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

final class ShortcutActions {
    static final String ACTION_TOGGLE = "com.taptop.app.action.TOGGLE";
    static final String ACTION_SCROLL_TO_TOP = "com.taptop.app.action.SCROLL_TO_TOP";

    private ShortcutActions() {}

    static void perform(Context context, String action) {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (ACTION_TOGGLE.equals(action)) {
            if (!TopService.connected) {
                Toast.makeText(context, "Enable TapTop in accessibility settings first", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean("taptop_enabled",
                    !prefs.getBoolean("taptop_enabled", true)).apply();
            context.sendBroadcast(new Intent(TopService.ACTION_UPDATE).setPackage(context.getPackageName()));
        } else if (ACTION_SCROLL_TO_TOP.equals(action)) {
            if (!TopService.connected) {
                Toast.makeText(context, "Enable TapTop in accessibility settings first", Toast.LENGTH_SHORT).show();
            } else if (!prefs.getBoolean("taptop_enabled", true)) {
                Toast.makeText(context, "Turn TapTop on first", Toast.LENGTH_SHORT).show();
            } else {
                context.sendBroadcast(new Intent(TopService.ACTION_SCROLL_TO_TOP).setPackage(context.getPackageName()));
            }
        }
    }
}
