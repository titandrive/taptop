package com.taptop.app;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

final class Haptics {
    private Haptics() {}

    static void click(View view) {
        Context context = view.getContext();
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator() && Build.VERSION.SDK_INT >= 29)
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        else view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
