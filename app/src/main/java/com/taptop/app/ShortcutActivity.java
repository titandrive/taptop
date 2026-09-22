package com.taptop.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** No window: gesture shortcuts leave the current app available to accessibility. */
public abstract class ShortcutActivity extends Activity {
    protected abstract String shortcutAction();
    protected abstract int shortcutLabel();
    protected abstract int shortcutIcon();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())) {
            // Older gesture pickers ask for a shortcut result instead of querying
            // the static shortcuts published for modern launchers.
            Intent result = new Intent()
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME,
                            getString(shortcutLabel()))
                    .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(
                            this, shortcutIcon()))
                    .putExtra(Intent.EXTRA_SHORTCUT_INTENT,
                            new Intent(this, getClass()).setAction(Intent.ACTION_VIEW));
            setResult(RESULT_OK, result);
        } else if (state == null) {
            ShortcutActions.perform(this, shortcutAction());
        }
        finish();
    }
}
