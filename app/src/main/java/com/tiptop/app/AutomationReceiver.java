package com.tiptop.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Public, explicit broadcast entry point for user-configured automation apps. */
public final class AutomationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ShortcutActions.perform(context, intent.getAction());
    }
}
