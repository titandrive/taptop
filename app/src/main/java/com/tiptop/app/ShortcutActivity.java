package com.tiptop.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** No window: gesture shortcuts leave the current app available to accessibility. */
public abstract class ShortcutActivity extends Activity {
    protected abstract boolean isToggle();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean toggle = isToggle();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())) {
            // Older gesture pickers ask for a shortcut result instead of querying
            // the static shortcuts published for modern launchers.
            Intent result = new Intent()
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME,
                            getString(toggle ? R.string.shortcut_toggle : R.string.shortcut_scroll))
                    .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(
                            this, toggle ? R.drawable.ic_shortcut_toggle : R.drawable.ic_shortcut_scroll))
                    .putExtra(Intent.EXTRA_SHORTCUT_INTENT,
                            new Intent(this, getClass()).setAction(Intent.ACTION_VIEW));
            setResult(RESULT_OK, result);
        } else if (state == null) {
            ShortcutActions.perform(this, toggle
                    ? ShortcutActions.ACTION_TOGGLE : ShortcutActions.ACTION_SCROLL_TO_TOP);
        }
        finish();
    }
}
