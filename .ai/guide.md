# Guide

## Stack

- **Language**: Java only (no Kotlin)
- **Platform**: Android IME. `compileSdk 37`, `minSdkVersion 24`, `targetSdkVersion 37`
- **Build**: Gradle with AGP `9.3.1`, wrapper included (`gradlew` / `gradlew.bat`)
- **Libraries**: none. `app/build.gradle` has an empty `dependencies { }` block.
- **UI toolkit**: framework classes only - `android.preference.*`, `android.app.AlertDialog`,
  `android.app.ActionBar`. There is **no AndroidX**, no support library, no Material components, no
  RecyclerView. Do not add any.

## Package identity

Two separate identifiers, and the distinction matters:

- **`applicationId` = `rkr.simplekeyboard.inputmethod.plus`** - the installed app identity. Differs
  from upstream so both keyboards can be installed and enabled together.
- **`namespace` = `rkr.simplekeyboard.inputmethod`** - unchanged. This is the Java package and the
  package of the generated `R` class. Leaving it alone means no Java file, no `proguard-rules.pro`
  entry and none of the fully-qualified class names in `res/xml/*.xml` or `res/layout/*.xml` need to
  change.

AGP expands relative manifest names (`.latin.LatinIME`) against the **namespace**, then stamps the
**applicationId** into the manifest `package` attribute. Verified in the built APK: `package` is
`...plus` while the service resolves to `rkr.simplekeyboard.inputmethod.latin.LatinIME`. The IME id
the system stores is therefore
`rkr.simplekeyboard.inputmethod.plus/rkr.simplekeyboard.inputmethod.latin.LatinIME`.

The app name lives in `res/values/strings-appname.xml` (`english_ime_name`,
`english_ime_settings`), is `translatable="false"` and has no `values-*` overrides, so renaming it
there covers every locale. Distinct names are required in practice, otherwise the two keyboards are
indistinguishable in the launcher, the IME picker and Languages & input.

## Core requirements

- Stay dependency-free and under ~1 MB.
- Only permission is `android.permission.VIBRATE`.
- Preferences must go through `PreferenceManagerCompat.getDeviceSharedPreferences(context)`
  (device-protected storage) because the service is `directBootAware`.
- Release builds run R8 (`minifyEnabled true`) with `app/proguard-rules.pro`.

## Get started

### Toolchain (verified working)

| Piece | Version | Location |
|---|---|---|
| JDK | Temurin 21.0.12.1 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |
| Gradle | 9.6.0 | `%LOCALAPPDATA%\Gradle\gradle-9.6.0` |
| Android SDK | platform `android-37.0`, `build-tools/37.0.0`, `platform-tools` | `%LOCALAPPDATA%\Android\Sdk` |
| SDK CLI | cmdline-tools 23.0 (`android.exe`) | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin` |

AGP 9.3.1 needs JDK 17+, so the system JDK 8 / JDK 11 will not work. `JAVA_HOME` must point at the
JDK 21 install for every build.

`local.properties` holds `sdk.dir` and is gitignored, so it has to be recreated on a fresh clone.

### Building

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug          # debug APK
.\gradlew.bat installDebug           # build and install on a connected device
.\gradlew.bat assembleRelease        # minified, signed release APK
```

Gradle output lands in `app/build/outputs/apk/<variant>/`. Copies are kept in `build/` for
convenience, but note that `gradle clean` wipes `build/` because it is the root project's build
directory. Debug is about 810 KB and takes roughly 2.5 minutes from clean; release is about 630 KB
after R8 and resource shrinking.

### Release signing

`app/build.gradle` reads `keystore.properties` from the project root:

```
storeFile=keystore/simple-keyboard-plus.jks
storePassword=...
keyAlias=simple-keyboard-plus
keyPassword=...
```

Both `keystore.properties` and `keystore/` are gitignored. If the file is absent the release build
still succeeds but produces an **unsigned** APK rather than failing, so the project keeps building for
anyone without the key. The key is RSA 4096, PKCS12, valid until 2056.

Verify a release APK with
`%ANDROID_HOME%\build-tools\37.0.0\apksigner.bat verify --verbose --print-certs <apk>`.

**Debug and release are signed with different keys, so one cannot be installed over the other.**
Uninstall first when switching between them, which also clears saved preferences.

Setup scripts that recreate the whole toolchain from nothing live in `.ai/tmp/build/`
(`01-fetch-tools.ps1` downloads and verifies cmdline-tools plus Gradle, `06-install.bat` installs the
SDK packages, `07-build.bat` regenerates the wrapper and builds, `11`/`12`/`13` verify the APK).

After installing, the keyboard must be enabled in the system Languages & Input settings before it can
be selected; the settings activity nags about this in `SettingsActivity.onStart()`.

There are no unit or instrumentation tests in the project.

## Application lifecycle

1. `AndroidManifest.xml` declares three components: the `LatinIME` service, the `SettingsActivity`
   launcher activity, and a `SystemBroadcastReceiver` for locale changes.
2. `LatinIME.onCreate()` initialises the singletons: `Settings.init()`, `RichInputMethodManager`,
   `AudioAndHapticFeedbackManager`, `KeyboardSwitcher`. `Settings.onCreate` registers a
   `SharedPreferences` listener and a receiver for managed-restriction changes.
3. `LatinIME.onStartInput` / `onStartInputView` build `InputAttributes` from the `EditorInfo`, call
   `Settings.loadSettings(...)` to snapshot preferences into a `SettingsValues`, reload the text
   cache, and ask `KeyboardSwitcher` to load the keyboard.
4. Touches land in `MainKeyboardView` -> `PointerTracker` -> `KeyboardActionListener`, implemented by
   `LatinIME`.
5. `LatinIME.onCodeInput` wraps the code point in an `Event` and calls `onEvent`, which runs
   `mInputLogic.onCodeInput(mSettings.getCurrent(), event)` and then applies the resulting
   `InputTransaction` (shift-state refresh) and updates the keyboard state.
6. `InputLogic` dispatches: consumed -> functional (`handleFunctionalEvent`, keyed on
   `event.mKeyCode`) -> non-functional (`handleNonFunctionalEvent` -> `handleNonSpecialCharacterEvent`).
   Text reaches the editor through `RichInputConnection`.
7. Any preference change fires `Settings.onSharedPreferenceChanged`, which rebuilds `SettingsValues`
   under a `ReentrantLock`. That is the only place preferences are parsed for runtime use.

### Typing path detail

This fork has **no composing text and no suggestions**. `setComposingText` / `finishComposingText`
are never called. A typed character goes straight to `RichInputConnection.commitText`, except digits
0-9 which go through `sendDownUpKeyEvent` -> `RichInputConnection.sendKeyEvent` for backward
compatibility.

## Data storage structure

- `SharedPreferences` - the only persistent store, in device-protected storage. Accessed via
  `PreferenceManagerCompat.getDeviceSharedPreferences`. Keys are declared as constants in
  `Settings.java`.
  - `auto_cap`, `vibrate_on`, `sound_on`, `popup_on` - legacy AOSP keys, no prefix
  - `pref_show_number_row`, `pref_show_special_chars`, `pref_show_language_switch_key`,
    `pref_use_on_screen`, `pref_enable_ime_switch`, `pref_space_swipe`, `pref_delete_swipe` - booleans
  - `pref_keypress_sound_volume`, `pref_keyboard_height` - floats, `-1` means "use default"
  - `pref_key_longpress_timeout`, `pref_bottom_offset_portrait`, `pref_keyboard_color` - ints
  - `pref_enabled_subtypes` - the enabled layouts serialised into one string
  - `pref_auto_text` - the whole auto-text table serialised into one string
  - `pref_fullscreen_landscape` - boolean, default true, allows the extracted fullscreen text field
  - `theme_key` (`KeyboardTheme.KEYBOARD_THEME_KEY`) - selected theme id
  - `active_restrictions` - string set of keys currently locked by managed configuration
- `res/xml/app_restrictions.xml` - managed-configuration schema. `Settings.loadRestrictions` copies
  admin-pushed values into `SharedPreferences` and records which keys are locked.
- Android Backup - `android:allowBackup="true"` with `fullBackupOnly`; `SubScreenFragment` pokes
  `BackupManager.dataChanged()` on every preference change.

## Features and the files behind them

- `IME service and input pipeline` - turns touches into text
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java` - the InputMethodService, KeyboardActionListener implementation, entry point for every key press
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java` - decides what a code point does; commits text, handles backspace, recapitalisation, editor actions, auto-text
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java` - wrapper over InputConnection with a cached copy of the text around the cursor and predicted selection offsets
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/event/Event.java` - immutable key press / text event
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/event/InputTransaction.java` - what the IME must refresh after an event (shift state)
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/common/Constants.java` - code point and key code constants
- `Auto-text` - replaces a configured keyword with its expansion the moment the keyword's last character is typed
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/AutoText.java` - immutable keyword table, serialisation to/from the single string preference, and the allocation-free match
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/AutoTextSettingsFragment.java` - the Auto-text settings screen: entry list, add button, add/edit/remove dialog
  - `app/src/main/res/layout/auto_text_dialog.xml` - the two-field dialog body (keyword, expanded text)
  - `app/src/main/res/menu/add_auto_text.xml` - action bar add button
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java` - `textBeforeCursorEndsWith` does the cache-only suffix test
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java` - `performAutoTextExpansion`, called from `handleNonSpecialCharacterEvent` before the trigger character is committed
- `Landscape fullscreen toggle` - lets the user turn off the extracted fullscreen text field that replaces the app in landscape
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java` - `onEvaluateFullscreenMode` checks the preference before the resource
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/Settings.java` - `PREF_FULLSCREEN_LANDSCAPE`, `readFullscreenLandscape`
  - `app/src/main/res/xml/prefs_screen_appearance.xml` - the switch, defaults to on so existing behaviour is unchanged
  - `app/src/main/res/values-land/config.xml` - `config_use_fullscreen_mode` is only true here, which is why the preference is framed as landscape only
- `Keyboard rendering and layouts` - drawing keys and building layouts from XML
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/MainKeyboardView.java` - the visible keyboard, key previews, more-keys panels
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/KeyboardView.java` - base drawing
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/KeyboardSwitcher.java` - owns the current keyboard and swaps alphabet/symbol/shift states
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/KeyboardLayoutSet.java` - builds a keyboard for an EditorInfo plus settings; `onKeyboardThemeChanged()` clears its cache
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java` - parses the layout XML
  - `app/src/main/res/xml/` - keyboard layout definitions (plus `xml-land`, `xml-sw600dp`, `xml-sw600dp-land` variants)
- `Touch handling` - press, repeat, long press, sliding, swipes
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/PointerTracker.java` - gesture state machine, also gates the space-swipe and delete-swipe features
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/TimerHandler.java` - key repeat and long press timers
- `Settings UI` - the launcher activity and its sub screens
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/SettingsActivity.java` - PreferenceActivity host; `isValidFragment` delegates to `FragmentUtils`
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/SettingsFragment.java` - root screen, inflates `res/xml/prefs.xml`
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/SubScreenFragment.java` - base class for sub screens; device-protected `getSharedPreferences()`, change listener, restriction-driven disabling
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/PreferencesSettingsFragment.java` - input behaviour toggles
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/KeyPressSettingsFragment.java` - sound, vibrate, popup, long press delay
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/AppearanceSettingsFragment.java` - theme link, colour, height, bottom offset
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/ThemeSettingsFragment.java` - radio list built at runtime
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/FragmentUtils.java` - whitelist of fragments the activity is allowed to open
  - `app/src/main/res/xml/prefs.xml` - top-level screen listing the sub screens
  - `app/src/main/res/xml/prefs_screen_*.xml` - one per sub screen; `empty_settings.xml` for runtime-built lists
- `Preference widgets` - custom controls used by the settings screens
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/SeekBarDialogPreference.java` - slider dialog driven by a `ValueProxy` supplied by the fragment
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/ColorDialogPreference.java` - RGB slider dialog
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/RadioButtonPreference.java` - row with a custom widget layout
  - `app/src/main/res/values/attrs.xml` - `declare-styleable` entries for the custom preferences
- `Settings model` - reading preferences and snapshotting them for the runtime
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/Settings.java` - singleton, key constants, static `read*`/`write*` helpers, managed restrictions
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/SettingsValues.java` - immutable snapshot consumed by the input pipeline
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/SpacingAndPunctuations.java` - word separators and punctuation from resources
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/compat/PreferenceManagerCompat.java` - device-protected SharedPreferences accessor
- `Languages and layouts` - which subtypes are enabled
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputMethodManager.java` - enabled subtype set, IME switching
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/Subtype.java` - a locale plus layout pair
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/LanguagesSettingsFragment.java` - the dynamic language list, the model for any runtime-built settings screen
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/SingleLanguageSettingsFragment.java` - per-language layout list, shows the Bundle-argument navigation pattern
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/SubtypeLocaleUtils.java` - supported locales and default subtypes
- `Theming` - colours and key styling
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/KeyboardTheme.java` - theme ids, stores the selection under `theme_key`
  - `app/src/main/res/values/themes-*.xml`, `values-night/`, `values-v31/` - theme definitions

## GOTCHAs

- **`FragmentUtils` is a hard whitelist.** A new settings fragment must be added to
  `sLatinImeFragments` or `PreferenceActivity.isValidFragment` throws `IllegalArgumentException` when
  the row is tapped. Easy to forget.
- **Digits bypass `commitText`.** `InputLogic.sendKeyCodePoint` sends 0-9 via `sendDownUpKeyEvent`,
  which is asynchronous and ignores batch edits. Never follow a digit commit with a
  delete-then-insert; do the replacement *before* the trigger character is sent instead. This is why
  `performAutoTextExpansion` runs first and swallows the trigger.
- **`RichInputConnection.deleteTextBeforeCursor` uses `mIC` without a null check.** Wrap calls in
  `beginBatchEdit()` (which refreshes `mIC`) and verify `isConnected()` first.
- **`RichInputConnection`'s cache is a prediction, not truth.** `commitText` and `sendKeyEvent`
  append to `mTextBeforeCursor` synchronously, but `reloadTextCache()` runs on a background executor,
  so right after an arbitrary cursor move the cache may be stale. It is authoritative immediately
  after typing.
- **`mTextBeforeCursor` grows unbounded** during a session and is only trimmed back to
  `Constants.EDITOR_CONTENTS_CACHE_SIZE` (1024) on reload. Match against its tail, never scan it.
- **`RichInputConnection.replaceText` replaces text *after* the cursor**, despite the name. It exists
  for recapitalisation. For backward replacement use `deleteTextBeforeCursor` + `commitText`.
- **Sub-screen action bar titles come from `res/xml/prefs.xml`**, not from the fragment. A screen that
  inflates `empty_settings.xml` still gets its title from the `<PreferenceScreen android:title>` row
  that opened it.
- **`Constants.CODE_SHIFT` is `-1`, the same value as `Constants.NOT_A_CODE`.** `Event` distinguishes
  them by `mCodePoint == NOT_A_CODE_POINT`, not by key code.
- **`handleFunctionalEvent` throws on unknown key codes** (`default:` branch). A new negative key code
  needs a case there.
- **Enter is intercepted** in `handleNonFunctionalEvent` when the field declares an editor action, so
  it never reaches `handleNonSpecialCharacterEvent` in search boxes and similar fields.
- **`Settings.loadRestrictions` logs an error for unhandled keys.** Adding a key to
  `res/xml/app_restrictions.xml` requires a matching `case` in that switch.
- **Deprecated `android.preference.*` is intentional.** Migrating to `androidx.preference` would mean
  rewriting every fragment and adding the first dependency; don't do it casually.
- **Never call `PreferenceManager.getDefaultSharedPreferences`.** Direct-boot support requires
  `PreferenceManagerCompat.getDeviceSharedPreferences`.
- **`values-*` translation folders are Crowdin-managed.** Add new strings only to
  `res/values/strings.xml`; leave the translated copies alone.
- **A default lives in two places.** The `android:defaultValue` in the preference XML and the fallback
  in the matching `Settings.read*` method. Change only one and the settings screen disagrees with what
  the keyboard actually does. The keypress options avoid this by having both sides read the same
  resource bool.
- **The keypress default bools are per form factor.** `config_default_sound_enabled` and
  `config_default_key_preview_popup` exist in `values/`, `values-sw430dp/`, `values-sw600dp/` and
  `values-sw768dp/config-per-form-factor.xml`; `config_default_vibration_enabled` is only in
  `values/config-common.xml`. Changing one bucket only affects that screen size.
- **`build/` is the root project's Gradle build dir**, so anything kept there (like the APK copies) is
  deleted by `gradle clean`.
- **Release builds rename `res/` file paths** via resource shrinking, so
  `aapt2 dump xmltree --file res/xml/foo.xml` fails against a release APK. Inspect the debug APK for
  resource-level checks.
- **`Resources.getIdentifier`'s package argument is the applicationId, not the R class package.**
  Since `applicationId` and `namespace` now differ, deriving it from `R.class.getPackage().getName()`
  returns the wrong package and every lookup silently returns 0. Always use
  `res.getResourcePackageName(someResId)`, as `LocaleResourceUtils`, `KeyboardTextsSet` and
  `KeyboardLayoutSet` do. This actually bit `LocaleResourceUtils` and was fixed; the symptom would
  have been missing display names for the exceptional locales (en_GB, en_US, es_US, hi_ZZ, sr_ZZ).
- **Classes named only from preference XML survive R8 automatically.** aapt2 generates keep rules for
  them, which is why `AutoTextSettingsFragment` needs no `proguard-rules.pro` entry. The explicit
  rules in that file are for fragments set from code (`SettingsFragment`, `LanguagesSettingsFragment`,
  `SingleLanguageSettingsFragment`). Verified against a real release build.

## Size and memory notes

### Where the APK size goes

Measured on the 628 KB release APK:

- `resources.arsc` - 238,920 bytes, **38% of the whole APK**. Dominated by the ~73 `values-*`
  translation folders (~263 KB of source XML).
- `classes*.dex` - ~178 KB after R8.
- `res/` files - the remainder, spread over ~480 small XML entries (keyboard layouts, drawables).

So the resource table, not the code, is the biggest single thing in the APK.

### Deliberately NOT done: `shrinkResources`

`shrinkResources true` is the obvious next size lever and it is **intentionally left off**. The app
resolves resources by name at runtime in three places, all invisible to the shrinker:

- `KeyboardLayoutSet.getXmlId` - `getIdentifier(layoutSetName, "xml", ...)`. Every keyboard layout
  except `keyboard_layout_set_qwerty` is reached only by dynamic name, so shrinking could strip all of
  them and break every non-QWERTY layout.
- `KeyboardTextsSet.expandReference` - `getIdentifier(name, "string", ...)` for `!string/` references
  inside layout XML.
- `LocaleResourceUtils.initLocked` - `getIdentifier` for `locale_name_*`.

AGP's shrinker has a conservative mode when it detects `getIdentifier`, but betting the keyboard's core
layouts on that heuristic is not worth ~50-100 KB, especially since the breakage would only appear at
runtime. If it is ever enabled it needs a `res/raw/keep.xml` with explicit `tools:keep` patterns for
`xml/keyboard_layout_set_*`, the keyboard label strings and `string/locale_name_*`, plus a real device
test of a non-QWERTY layout.

### Open size lever: `resConfigs`

Restricting `resConfigs` to English would cut most of that 239 KB `resources.arsc`, plausibly 150-180 KB
off a 628 KB APK. It affects **only the settings UI language** - keyboard layouts live in `res/xml` and
their key labels come from `KeyboardTextsTable`, which is Java, so all keyboard languages would remain
available. The cost is that the settings screen becomes English everywhere. This is a product decision,
not a technical one, so it has not been applied.

### Memory changes made

- `RichInputConnection.appendTextBeforeCursor` caps the before-cursor cache at twice
  `Constants.EDITOR_CONTENTS_CACHE_SIZE`, trimming back to one cache size. Before this the string grew
  for the entire session and, because `+=` copies, the cost of one key press grew with everything typed
  before it. Trimming from the front is safe because every reader works backwards from the end
  (`getCodePointBeforeCursor`, `textBeforeCursorEndsWith`, `CapsModeUtils.getCapsMode`), and
  `setSelection` derives its origin from the current length. This also makes `setSelection`'s
  whole-cache concatenation cheap, which matters because a space swipe calls it once per 10 dp of
  finger travel.
- `RichInputConnection.onDestroy` shuts down the single-thread executor, reached from
  `InputLogic.onDestroy` and `LatinIME.onDestroy`. Its thread is non-daemon, so it previously outlived
  the service and kept it reachable.
- `Settings.readKeyboardColor` resolves its default lazily. Passing `readKeyboardDefaultColor` as the
  default argument evaluated it on every call, reading two resource `int[]`s and the theme, on a path
  that runs on every keyboard swap.
- `KeyDetector.detectHitKey` indexes instead of using for-each. The list is an unmodifiable wrapper, so
  a for-each allocated two objects per call, and this runs on every touch move event.
- `config_keyboard_grid_width`/`height` reduced from 32x16 to 16x8. The grid is only a spatial index
  for hit testing and candidates are still exact tested by `Key.isOnKey`, so results are unchanged.
  Cuts the retained neighbour lists and the `gridSize * keyCount` scratch buffer by four, across the
  four keyboards pinned by `KeyboardLayoutSet.sForcibleKeyboardCache`.

### Considered and rejected

- Clearing `PointerTracker`'s static `sDrawingProxy`/`sTimerProxy`/`sListener` on view detach. It is
  constant retention rather than growth, and `PointerTracker` dereferences `sDrawingProxy` unguarded,
  so nulling it risks an NPE for a modest gain.
- Caching the spacebar language string, which is rebuilt via ICU on every spacebar redraw. Only affects
  users with multiple languages enabled, and this fork defaults to one.
- The three small objects per keystroke (`Event`, `InputTransaction`, the committed `String`). About
  30 short-lived objects per second at human typing speed - irrelevant to the GC.
- `SettingsValues`/`SpacingAndPunctuations` rebuilding per focused field. Tens of microseconds.

## Auto-text implementation notes

Storage: one string preference, `pref_auto_text`. Entries are separated by `U+001E` and the keyword
is separated from the expansion by `U+001F` - control characters the keyboard cannot produce, so no
escaping is needed. `AutoText.strip()` removes them from user input as a safety net.

Matching (hot path, per keystroke):

1. `SettingsValues.mAutoText` holds the parsed table plus, per entry, the keyword's last code point
   (`mTriggers`) and the length of the keyword without it (`mPrefixLengths`).
2. `InputLogic.performAutoTextExpansion` returns immediately if the table is empty - one array-length
   comparison for users who never configure anything.
3. Otherwise it compares the incoming code point against each `mTriggers` entry. An int comparison
   rejects nearly every key press without touching strings.
4. On a trigger hit, `RichInputConnection.textBeforeCursorEndsWith` does a `regionMatches` against the
   cached before-cursor text - no IPC, no allocation.
5. On a full match, inside one batch edit: `deleteTextBeforeCursor(prefixLength)` then
   `commitText(expansion)`. The trigger character itself is never sent to the editor.

Matching is a plain suffix match with no word-boundary requirement, which keeps it predictable and
lets keywords end in any character (including a space, if the user wants a space-triggered
expansion). Expansion is deliberately **not** suppressed in password fields.
