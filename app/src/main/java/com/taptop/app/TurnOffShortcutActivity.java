package com.taptop.app;

public final class TurnOffShortcutActivity extends ShortcutActivity {
    @Override protected String shortcutAction() { return ShortcutActions.ACTION_TURN_OFF; }
    @Override protected int shortcutLabel() { return R.string.shortcut_turn_off; }
    @Override protected int shortcutIcon() { return R.drawable.ic_stop; }
}
