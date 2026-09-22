# TipTop

**Back to the top with a tap.**

Tap your chosen area to scroll to the top. Touch the screen to stop.

Version **0.5.0** · Android **8.0+**

[Download APK](https://github.com/titandrive/tiptop/releases/latest/download/TipTop.apk) · [Add to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522com.tiptop.app%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252Ftitandrive%252Ftiptop%2522%252C%2522author%2522%253A%2522TitanDrive%2522%252C%2522name%2522%253A%2522TipTop%2522%257D)

## Get started

1. Install and open TipTop.
2. Open **Accessibility settings** and enable TipTop’s service.
3. Turn TipTop on, then tap the tap area while using another app.

## Make it yours

- **Five speeds:** Slowest, Slow, Medium, Fast, Maximum.
- **Adjustable tap area:** change its width, height, position, and top offset.
- **Optional visible bar:** choose a color and opacity, or hide it. The hidden area still works; a dotted outline helps you adjust it inside TipTop.
- **Light and dark themes:** Catppuccin Latte and Macchiato, with a system option.
- **Optional haptics:** feedback when tapping the area or the enable/disable button.

## Shortcuts

- **Quick Settings:** add the TipTop tile to toggle it on or off.
- **App shortcuts:** long-press the app icon for **Toggle TipTop** and **Scroll to top**. Compatible launchers and gesture apps can use them too.
- **Automation:** trigger either action from Tasker or MacroDroid.

The app’s **ⓘ button** includes a usage guide and copyable automation fields.

<details>
<summary>Tasker / MacroDroid setup</summary>

Use **Send Intent**. Set the target to **Broadcast Receiver** in Tasker or **Broadcast** in MacroDroid.

| Field | Value |
| --- | --- |
| Toggle action | `com.tiptop.app.action.TOGGLE` |
| Scroll action | `com.tiptop.app.action.SCROLL_TO_TOP` |
| Package | `com.tiptop.app` |
| Class | `com.tiptop.app.AutomationReceiver` |

Choose one action and leave the remaining fields empty. Scrolling requires TipTop and its accessibility service to be enabled.

</details>

## Privacy & compatibility

TipTop uses accessibility access to scroll the current app. It has no internet permission and does not save screen content. Scrolling behavior depends on the app; some apps may not respond.

<details>
<summary>Build from source</summary>

Use Android Studio, or JDK 17 with Android SDK 34:

```sh
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

For signed releases, see [release build instructions](docs/RELEASING.md).

</details>

## AI Disclaimer

TipTop was vibecoded using Codex. Use at your own discretion, and [report issues](https://github.com/titandrive/tiptop/issues) when something breaks.

## License

[MIT](LICENSE) © 2026 TitanDrive. See [third-party notices](THIRD_PARTY_NOTICES.md) for included assets.
