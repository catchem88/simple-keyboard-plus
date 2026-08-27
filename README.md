# Simple Keyboard Plus

<img src="images/screenshot-0.png"
      alt="closeup"
      width="500"/>

## About

A fork of [Simple Keyboard](https://github.com/rkkr/simple-keyboard) that adds a few features while
keeping the original small, fast and dependency-free.

It uses a separate application id (`rkr.simplekeyboard.inputmethod.plus`), so it can be installed and
enabled alongside the original Simple Keyboard.

## Added Features / Changes

### New features

- **Auto-text** - define your own keyword and expanded text pairs. The keyword is replaced the moment
  you type its last character, so `/lipsum` becomes `Lorem ipsum dolor sit amet` as soon as you finish
  the `m`. Manage entries under **Settings > Auto-text**: tap the `+` in the action bar to add one, tap
  a row to edit or remove it.
  - Works in every text field, including password fields.
  - Keywords can end in any character, including a space, so `brb ` can expand on the space.
  - Costs nothing when unused. Typing only pays for one integer comparison per configured entry, and
    nothing at all if you have no entries.
- **Fullscreen keyboard in landscape can be turned off** - the original always hides the app behind an
  enlarged text field in landscape. **Settings > Appearance > Fullscreen keyboard in landscape** now
  lets you keep the app visible. Off by default in this fork.
- **The app icon can be hidden** - a keyboard does not really need a launcher entry. Turn off
  **Settings > Preferences > Show app icon** to remove it from the app drawer. Shown by default so a new
  install is still easy to find. With the icon hidden, settings remain reachable by long-pressing the
  comma key, or through system Settings > Languages & input.

### Changed defaults

Configured for a clean typing experience out of the box. All of these are still adjustable in Settings.

| Setting | Original | This fork |
|---|---|---|
| Show separate number row | off | **on** |
| Show special characters | on | on |
| Show language switch key | on | **off** |
| Space swipe cursor move | off | **on** |
| Vibrate on keypress | on | **off** |
| Sound on keypress | varies by screen size | **off** |
| Popup on keypress | varies by screen size | **on** |
| Fullscreen keyboard in landscape | on | **off** |

Sound and popup previously differed between phones and tablets. They are now the same on every device.

### Other changes

- **Installs alongside the original.** Separate application id and app name, so you can run both and
  compare.
- **Lower memory use while typing.** The cached copy of the text before the cursor is now capped
  instead of growing for the whole session. Previously every key press copied everything typed before
  it, so a long session made each keystroke progressively more expensive.
- **Fixed a leaked background thread** that kept the keyboard service reachable after it was destroyed.
- **Fewer allocations on the touch and keyboard-switch paths**, including removing two resource array
  reads that happened on every keyboard swap.
- Links now point at this repository rather than upstream.

### Inherited features

- Small size (under 1 MB)
- Adjustable keyboard height for more screen space
- Number row
- Swipe space to move pointer
- Delete swipe
- Custom theme colors
- Minimal permissions (only Vibrate)
- Ads-free

### Features it does not have and probably never will

- Emojis
- GIFs
- Spell checker
- Swipe typing

## Get started

Download the APK from this repository, install it, then:

1. Open "Simple Keyboard Plus" from your launcher.
2. Enable it in the system Languages & Input settings.
3. Switch to it from your current keyboard, usually by long-pressing space.
4. To change settings, long-press `,` on the keyboard or open Settings > Languages & Input >
   Simple Keyboard Plus.

Once set up you can hide the launcher icon under Settings > Preferences > Show app icon. Long-pressing
`,` still opens settings afterwards.

### Building from source

Requires JDK 17 or newer and the Android SDK with API 37.

```
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK
```

A release build is signed only if a `keystore.properties` file is present in the project root, in the
following format. Without it the release APK is produced unsigned.

```
storeFile=keystore/your-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Credits

Licensed under Apache License Version 2.

Based on [Simple Keyboard](https://github.com/rkkr/simple-keyboard) by rkkr, which is in turn based on
the AOSP LatinIME keyboard. The original source is available at
https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
