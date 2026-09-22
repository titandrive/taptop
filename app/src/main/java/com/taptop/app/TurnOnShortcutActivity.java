package com.taptop.app;

public final class TurnOnShortcutActivity extends ShortcutActivity {
    @Override protected String shortcutAction() { return ShortcutActions.ACTION_TURN_ON; }
    @Override protected int shortcutLabel() { return R.string.shortcut_turn_on; }
    @Override protected int shortcutIcon() { return R.drawable.ic_start; }
}
