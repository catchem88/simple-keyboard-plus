# Goal

## Project

`simple-keyboard-plus` - a fork of [Simple Keyboard](https://github.com/rkkr/simple-keyboard), an
Android soft keyboard (IME) derived from AOSP LatinIME.

The fork is built to sit **alongside** upstream rather than replace it: application id
`rkr.simplekeyboard.inputmethod.plus`, app name "Simple Keyboard Plus". Both can be installed and
enabled at the same time, which makes it easy to compare against the original.

## Main goal

Keep Simple Keyboard's defining trait - a tiny, fast, dependency-free keyboard - while adding
carefully chosen features that the upstream project does not have.

The guiding rule for every addition: **it must cost nothing when it is not used.** No new
libraries, no background work, no per-keystroke allocations, no extra permissions.

## Core features (inherited from upstream)

- Small APK (under 1 MB), zero third-party dependencies
- Adjustable keyboard height and bottom offset
- Optional separate number row and special characters
- Swipe on space to move the cursor, swipe on delete to select-and-delete
- Custom theme colors, light/dark/system themes with optional key borders
- Many keyboard layouts and languages, selectable per language
- Minimal permissions (VIBRATE only), no ads, no analytics, no network access

Repository: https://github.com/catchem88/simple-keyboard-plus

## Default settings

The fork ships a different out-of-the-box configuration from upstream. Changing a default means
editing **two** places that must agree: the `android:defaultValue` in the preference XML (what the
settings screen shows) and the fallback in the matching `Settings.read*` method (what the keyboard
uses before the user touches anything). For the keypress options both sides read the same resource
bool, so editing the bool covers both.

| Setting | Default | Where |
|---|---|---|
| Show separate number row | on | `pref_show_number_row` |
| Show special characters | on | `pref_show_special_chars` |
| Show language switch key | off | `pref_show_language_switch_key` |
| Space swipe cursor move | on | `pref_space_swipe` |
| Vibrate on keypress | off | `config_default_vibration_enabled` |
| Sound on keypress | off | `config_default_sound_enabled` |
| Popup on keypress | on | `config_default_key_preview_popup` |
| Fullscreen keyboard in landscape | off | `pref_fullscreen_landscape` |
| Language | English (US) only | `SubtypeLocaleUtils.getDefaultSubtypes` |
| Auto-capitalization | on | `auto_cap`, unchanged from upstream |
| Delete swipe | off | `pref_delete_swipe`, unchanged from upstream |

## Features added by this fork

- **Auto-text** - user-defined keyword to expanded-text replacements. Typing the last character of
  a configured keyword (for example `/lipsum`) replaces the keyword in place with its expansion
  (for example `Lorem ipsum dolor sit amet`). Configured under Settings > Auto-text. Works in every
  field, including password fields.
- **Landscape fullscreen toggle** - upstream forces the extracted fullscreen text field on phones in
  landscape. Settings > Appearance now lets that be turned off. Defaults to off in this fork.
- **Leaner runtime** - the cached text before the cursor is capped instead of growing for the whole
  session, the background executor is shut down with the service, and the proximity grid is a quarter
  of upstream's size. See the performance section in `guide.md`.
- **Hideable launcher icon** - Settings > Preferences > Show app icon, on by default. A keyboard does
  not need a launcher entry once it is set up.

## Explicit non-goals

Same as upstream: no emoji picker, no GIFs, no spell checker, no swipe typing, no dictionary or
word suggestions. The suggestion/composing machinery was stripped from the AOSP base and stays out.

## Stance

### Security and privacy

- A keyboard sees everything the user types, so the app stays offline. No network permission, no
  telemetry, no crash reporting.
- Preferences are read and written through device-protected storage so the IME works before first
  unlock (`android:directBootAware="true"`). Never switch to
  `PreferenceManager.getDefaultSharedPreferences`.
- Never log user-entered text. Auto-text expansions are user content and must not appear in logcat.
- Managed-configuration restrictions (`res/xml/app_restrictions.xml`) may lock settings; respect the
  disabled state rather than working around it.

### Performance

- The per-keystroke path is the hot path. New logic there must be O(1)-ish, allocation-free, and
  must exit on the cheapest possible check when the feature is unused.
- Prefer reading `RichInputConnection`'s cached text over any `InputConnection` call, since the
  latter is IPC.
- Parse and precompute at settings-change time (`SettingsValues` construction), never while typing.

### Usability

- Settings stay shallow and self-explanatory; match the existing framework-preference look rather
  than introducing a new UI style.
- Features are discoverable but off by default. An empty configuration means the feature is inert,
  so no master on/off toggle is added just to have one.
- Editors and dialogs get real labels (`android:labelFor`, hints) so TalkBack works.
