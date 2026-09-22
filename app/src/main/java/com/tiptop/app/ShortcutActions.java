package com.tiptop.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

final class ShortcutActions {
    static final String ACTION_TOGGLE = "com.tiptop.app.action.TOGGLE";
    static final String ACTION_SCROLL_TO_TOP = "com.tiptop.app.action.SCROLL_TO_TOP";

    private ShortcutActions() {}

    static void perform(Context context, String action) {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (ACTION_TOGGLE.equals(action)) {
            if (!TopService.connected) {
                Toast.makeText(context, "Enable TipTop in accessibility settings first", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean("tiptop_enabled",
                    !prefs.getBoolean("tiptop_enabled", true)).apply();
            context.sendBroadcast(new Intent(TopService.ACTION_UPDATE).setPackage(context.getPackageName()));
        } else if (ACTION_SCROLL_TO_TOP.equals(action)) {
            if (!TopService.connected) {
                Toast.makeText(context, "Enable TipTop in accessibility settings first", Toast.LENGTH_SHORT).show();
            } else if (!prefs.getBoolean("tiptop_enabled", true)) {
                Toast.makeText(context, "Turn TipTop on first", Toast.LENGTH_SHORT).show();
            } else {
                context.sendBroadcast(new Intent(TopService.ACTION_SCROLL_TO_TOP).setPackage(context.getPackageName()));
            }
        }
    }
}
