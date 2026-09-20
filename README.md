# TipTop

A small Android accessibility utility for returning to the start of a list. Enable its accessibility service, then tap the blue bar near the top of another app. The fallback swipe speed and the bar's size, position, distance from the screen top, and opacity can be adjusted in TipTop. Fast is the default fallback speed. The bar can also be turned off without disabling the service.

On Android 15 and newer, lists that support granular accessibility scrolling receive a single request to scroll to the beginning. Compatible RecyclerViews handle this with their native smooth animation; its speed is set by the target app, and touching the list can interrupt it. Other lists use repeated downward swipes until they reach the top or 200 swipes. A short haptic click confirms the bar tap. During fallback swiping, touching the screen stops the sequence; that first touch is used to stop and is not passed to the app underneath. The bar turns red during fallback swiping and can also be tapped to stop. Apps that do not expose scroll actions may not respond. The app has no internet permission and does not store screen content.

## Build

Open this folder in Android Studio and build the `app` module. Android SDK 34 and JDK 17 are required. The project uses Android Gradle Plugin 8.4.2. The minimum supported Android version is 8.0 (API 26).

## Use

1. Install and open TipTop.
2. Select **Open accessibility settings**, find TipTop, and enable the service.
3. Open a list in another app and tap the blue bar.

At zero offset TipTop positions its overlay window at the physical screen edge. Android may still intercept taps or obscure part of the bar in the status bar or camera cutout on some devices.
