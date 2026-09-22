package com.tiptop.app;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.Set;

final class AppFilter {
    static final String MODE = "app_filter_mode";
    static final String ALL = "All";
    static final String BLACKLIST = "Blacklist";
    static final String WHITELIST = "Whitelist";

    private AppFilter() {}

    static String mode(SharedPreferences prefs) {
        String mode = prefs.getString(MODE, ALL);
        return BLACKLIST.equals(mode) || WHITELIST.equals(mode) ? mode : ALL;
    }

    static String listKey(String mode) {
        return WHITELIST.equals(mode) ? "allowed_apps" : "blocked_apps";
    }

    static boolean allows(SharedPreferences prefs, CharSequence packageName) {
        String mode = mode(prefs);
        return allows(mode, prefs.getStringSet(listKey(mode), Collections.emptySet()),
                packageName == null ? null : packageName.toString());
    }

    static boolean allows(String mode, Set<String> selected, String packageName) {
        if (!BLACKLIST.equals(mode) && !WHITELIST.equals(mode)) return true;
        // During window transitions, wait for a known app before enabling the area.
        if (packageName == null || packageName.isEmpty()) return false;
        return WHITELIST.equals(mode) ? selected.contains(packageName) : !selected.contains(packageName);
    }
}
