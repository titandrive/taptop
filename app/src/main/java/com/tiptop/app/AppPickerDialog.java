package com.tiptop.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
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
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int position) {
                return getItem(position).packageName == null ? 1 : 0;
            }
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) { return getItemViewType(position) == 0; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                Entry app = getItem(position);
                if (app.packageName == null) {
                    TextView header = recycled instanceof TextView
                            ? (TextView) recycled : new TextView(activity);
                    header.setText(app.label);
                    header.setTextSize(13);
                    header.setTextColor(accent);
                    header.setTypeface(null, android.graphics.Typeface.BOLD);
                    header.setPadding(dp(activity, 4), dp(activity, 16), dp(activity, 4), dp(activity, 8));
                    if (Build.VERSION.SDK_INT >= 28) header.setAccessibilityHeading(true);
                    return header;
                }
                CheckedTextView row = recycled instanceof CheckedTextView
                        ? (CheckedTextView) recycled : new CheckedTextView(activity);
                row.setText(app.label);
                int iconSize = dp(activity, 36);
                app.icon.setBounds(0, 0, iconSize, iconSize);
                row.setCompoundDrawablesRelative(app.icon, null, null, null);
                row.setCompoundDrawablePadding(dp(activity, 12));
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
            // Stable sort keeps alphabetical order within each group.
            visible.sort((a, b) -> Boolean.compare(
                    selected.contains(b.packageName), selected.contains(a.packageName)));
            int selectedCount = 0;
            while (selectedCount < visible.size()
                    && selected.contains(visible.get(selectedCount).packageName)) selectedCount++;
            if (selectedCount < visible.size())
                visible.add(selectedCount, new Entry(null, "Apps", null));
            if (selectedCount > 0)
                visible.add(0, new Entry(null, "Selected apps", null));
            adapter.notifyDataSetChanged();
            count.setText(selected.size() + " selected" + (visible.isEmpty() ? " · No matching apps" : ""));
        };
        list.setOnItemClickListener((parent, view, position, id) -> {
            String name = visible.get(position).packageName;
            if (name == null) return;
            if (!selected.remove(name)) selected.add(name);
            refresh.run();
        });
        LinearLayout selectionActions = new LinearLayout(activity);
        selectionActions.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(selectionActions, new LinearLayout.LayoutParams(-1, -2));
        Button selectAll = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        selectAll.setText("Select all");
        selectAll.setTextColor(accent);
        selectAll.setEnabled(false);
        Button clear = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        clear.setText("Clear");
        clear.setTextColor(accent);
        Button cancel = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        cancel.setText("Cancel");
        Button done = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        done.setText("Done");
        for (Button button : new Button[]{selectAll, clear, cancel, done}) {
            button.setTextColor(accent);
            button.setAllCaps(false);
            button.setSingleLine(true);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(dp(activity, 48));
            button.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
            button.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
            selectionActions.addView(button, new LinearLayout.LayoutParams(
                    0, dp(activity, 48), button == selectAll ? 1.3f : 1f));
        }
        selectAll.setOnClickListener(v -> {
            for (Entry app : all) selected.add(app.packageName);
            refresh.run();
        });
        clear.setOnClickListener(v -> {
            selected.clear();
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
                .setCustomTitle(title).setView(body).create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        done.setOnClickListener(v -> {
            prefs.edit().putStringSet(key, new HashSet<>(selected)).apply();
            onSaved.run();
            dialog.dismiss();
        });
        dialog.show();
        GradientDrawable background = new GradientDrawable();
        background.setColor(card);
        background.setCornerRadius(dp(activity, 24));
        dialog.getWindow().setBackgroundDrawable(background);
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * .8f);
        dialog.getWindow().setLayout(-1, height);
        PackageManager packages = activity.getApplicationContext().getPackageManager();
        // Package labels and icons can involve disk/binder work; keep it off the UI thread.
        new Thread(() -> {
            Map<String, Entry> byPackage = new HashMap<>();
            for (String category : new String[]{Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_HOME}) {
                Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(category);
                for (ResolveInfo app : packages.queryIntentActivities(launcher, 0)) {
                    String name = app.activityInfo.packageName;
                    if (!byPackage.containsKey(name))
                        byPackage.put(name, new Entry(name, app.loadLabel(packages).toString(),
                                app.loadIcon(packages)));
                }
            }
            // Keep previously selected packages visible even after uninstalling an app.
            for (String name : prefs.getStringSet(key, Collections.emptySet())) {
                if (byPackage.containsKey(name)) continue;
                try {
                    ApplicationInfo app = packages.getApplicationInfo(name, 0);
                    byPackage.put(name, new Entry(name, app.loadLabel(packages).toString(),
                            app.loadIcon(packages)));
                } catch (PackageManager.NameNotFoundException e) {
                    byPackage.put(name, new Entry(name, "Unavailable app", packages.getDefaultActivityIcon()));
                }
            }
            List<Entry> loaded = new ArrayList<>(byPackage.values());
            Collator order = Collator.getInstance();
            loaded.sort((a, b) -> order.compare(a.label, b.label));
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed() || !dialog.isShowing()) return;
                all.addAll(loaded);
                selectAll.setEnabled(true);
                refresh.run();
            });
        }, "TipTop-app-picker").start();
    }

    private static final class Entry {
        final String packageName, label;
        final Drawable icon;
        Entry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
