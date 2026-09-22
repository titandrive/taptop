# TipTop

A small Android accessibility utility for returning to the start of a list. Enable its accessibility service, then tap the blue bar near the top of another app. The fallback fling strength and the bar's size, position, distance from the screen top, and opacity can be adjusted in TipTop. Fast is the default fallback strength. The bar can also be turned off without disabling the service.

Lists that support AndroidX granular accessibility scrolling, or platform granular scrolling on Android 15 and newer, receive a single request to scroll to the beginning. Compatible RecyclerViews handle this with their native smooth animation; its speed is set by the target app, and touching the list can interrupt it. Lists without that support use their scroll-to-position action, which may jump to the top. Lists without either direct action use backward-scroll actions at a 16 ms target interval, renewing the app's animation before it settles. This avoids injecting finger-down events between swipes. The controller retains the original list, checks that it remains visible, and stops when the backward action is unavailable or rejected. It also stops after 1.5 seconds without scroll events or 60 seconds overall. While the bar is red, touching the screen stops further requests; this first touch is consumed, and the app's current animation may finish. If the initial native request fails, the optional fling fallback sends one gesture and may stop short of the top on long lists. Native animation behavior still depends on the target app; sustained motion and perceived smoothness need device validation. A short haptic click confirms the bar tap. Apps that do not expose scroll actions may not respond. The app has no internet permission and does not store screen content.

## Build

Open this folder in Android Studio and build the `app` module. Android SDK 34 and JDK 17 are required. The project uses Android Gradle Plugin 8.4.2. The minimum supported Android version is 8.0 (API 26).

## Use

1. Install and open TipTop.
2. Select **Open accessibility settings**, find TipTop, and enable the service.
3. Open a list in another app and tap the blue bar.

At zero offset TipTop positions its overlay window at the physical screen edge. Android may still intercept taps or obscure part of the bar in the status bar or camera cutout on some devices.
