package com.taptop.app;

public final class ToggleShortcutActivity extends ShortcutActivity {
    @Override protected String shortcutAction() { return ShortcutActions.ACTION_TOGGLE; }
    @Override protected int shortcutLabel() { return R.string.shortcut_toggle; }
    @Override protected int shortcutIcon() { return R.drawable.ic_shortcut_toggle; }
}
