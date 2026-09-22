package com.taptop.app;

import android.content.SharedPreferences;
import android.content.res.Configuration;

final class ThemeColors {
    private ThemeColors() {}

    static boolean isDark(SharedPreferences prefs, Configuration configuration) {
        String theme = prefs.getString("theme", "System");
        return theme.equals("Dark") || (theme.equals("System")
                && (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
    }

    static int accent(boolean dark) { return dark ? 0xffc6a0f6 : 0xff8839ef; }
    static int bar(SharedPreferences prefs, boolean dark) {
        return prefs.getInt("bar_color", accent(dark)) | 0xff000000;
    }
    static int scrolling(boolean dark) { return dark ? 0xffed8796 : 0xffd20f39; }
}
