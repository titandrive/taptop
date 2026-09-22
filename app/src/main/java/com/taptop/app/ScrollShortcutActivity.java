package com.taptop.app;

public final class ScrollShortcutActivity extends ShortcutActivity {
    @Override protected String shortcutAction() { return ShortcutActions.ACTION_SCROLL_TO_TOP; }
    @Override protected int shortcutLabel() { return R.string.shortcut_scroll; }
    @Override protected int shortcutIcon() { return R.drawable.ic_shortcut_scroll; }
}
