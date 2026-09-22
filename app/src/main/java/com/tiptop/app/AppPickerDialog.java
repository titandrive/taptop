package com.tiptop.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Selection is saved only on Done; searches never discard hidden selections. */
final class AppPickerDialog {
    static void show(Activity activity, SharedPreferences prefs, String mode,
            int card, int ink, int muted, int accent, Runnable onSaved) {
        String key = AppFilter.listKey(mode);
        Set<String> selected = new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
        List<Entry> all = new ArrayList<>();
        List<Entry> visible = new ArrayList<>();
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 20);
        body.setPadding(padding, 0, padding, dp(activity, 8));
        body.setBackgroundColor(card);
        if (Build.VERSION.SDK_INT >= 29) body.setForceDarkAllowed(false);
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setContentDescription("Search apps by name or package");
        search.setTextColor(ink);
        search.setHintTextColor(muted);
        search.setBackgroundTintList(ColorStateList.valueOf(accent));
        body.addView(search, new LinearLayout.LayoutParams(-1, -2));
        TextView count = new TextView(activity);
        count.setTextColor(muted);
        count.setTextSize(13);
        count.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
        count.setText("Loading apps…");
        body.addView(count);
        ListView list = new ListView(activity);
        list.setDivider(null);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Entry getItem(int position) { return visible.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                CheckedTextView row = recycled instanceof CheckedTextView
                        ? (CheckedTextView) recycled : new CheckedTextView(activity);
                Entry app = getItem(position);
                row.setText(app.label + "\n" + app.packageName);
                row.setTextSize(14);
                row.setTextColor(ink);
                row.setPadding(dp(activity, 4), dp(activity, 12), dp(activity, 4), dp(activity, 12));
                row.setMinHeight(dp(activity, 64));
                row.setGravity(Gravity.CENTER_VERTICAL);
                boolean checked = selected.contains(app.packageName);
                row.setChecked(checked);
                row.setCheckMarkDrawable(checked ? R.drawable.ic_app_checked : R.drawable.ic_app_unchecked);
                row.setCheckMarkTintList(ColorStateList.valueOf(checked ? accent : muted));
                row.jumpDrawablesToCurrentState();
                return row;
            }
        };
        list.setAdapter(adapter);
        Runnable refresh = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (Entry app : all) {
                if (app.label.toLowerCase(Locale.ROOT).contains(query)
                        || app.packageName.toLowerCase(Locale.ROOT).contains(query)) visible.add(app);
            }
            adapter.notifyDataSetChanged();
            count.setText(selected.size() + " selected" + (visible.isEmpty() ? " · No matching apps" : ""));
        };
        list.setOnItemClickListener((parent, view, position, id) -> {
            String name = visible.get(position).packageName;
            if (!selected.remove(name)) selected.add(name);
            refresh.run();
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            public void afterTextChanged(Editable s) {}
        });
        TextView title = new TextView(activity);
        title.setText(mode.equals(AppFilter.WHITELIST) ? "Allowed apps" : "Blocked apps");
        title.setTextSize(20);
        title.setTextColor(ink);
        title.setPadding(padding, padding, padding, dp(activity, 12));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(title).setView(body)
                .setPositiveButton("Done", (d, which) -> {
                    prefs.edit().putStringSet(key, new HashSet<>(selected)).apply();
                    onSaved.run();
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", null).create();
        dialog.show();
        GradientDrawable background = new GradientDrawable();
        background.setColor(card);
        background.setCornerRadius(dp(activity, 24));
        dialog.getWindow().setBackgroundDrawable(background);
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * .8f);
        dialog.getWindow().setLayout(-1, height);
        for (int button : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL})
            dialog.getButton(button).setTextColor(accent);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            selected.clear();
            refresh.run();
        });
        PackageManager packages = activity.getApplicationContext().getPackageManager();
        // Package labels can involve disk/binder work; keep it off the UI thread.
        new Thread(() -> {
            Map<String, Entry> byPackage = new HashMap<>();
            for (String category : new String[]{Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_HOME}) {
                Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(category);
                for (ResolveInfo app : packages.queryIntentActivities(launcher, 0)) {
                    String name = app.activityInfo.packageName;
                    byPackage.put(name, new Entry(name, app.loadLabel(packages).toString()));
                }
            }
            // Keep previously selected packages visible even after uninstalling an app.
            for (String name : prefs.getStringSet(key, Collections.emptySet()))
                if (!byPackage.containsKey(name)) byPackage.put(name, new Entry(name, name));
            List<Entry> loaded = new ArrayList<>(byPackage.values());
            Collator order = Collator.getInstance();
            loaded.sort((a, b) -> order.compare(a.label, b.label));
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed() || !dialog.isShowing()) return;
                all.addAll(loaded);
                refresh.run();
            });
        }, "TipTop-app-picker").start();
    }

    private static final class Entry {
        final String packageName, label;
        Entry(String packageName, String label) { this.packageName = packageName; this.label = label; }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
