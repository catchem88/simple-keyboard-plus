# State

Last updated: session that added Auto-text, the landscape fullscreen toggle, and a working build.

## Status

Feature complete for what has been asked so far. The user has confirmed the **debug** build works well
on a device. A signed release APK is the deliverable.

- `build/simple-keyboard-plus-release.apk` - 628,427 bytes, signed (APK Signature Scheme v2).
  **This is the one to distribute.**
- The debug APK was removed from `build/`. Regenerate with `gradlew assembleDebug` when a resource-level
  inspection is needed; note that incremental dexing inflates it (observed 813 KB clean, growing past
  1.1 MB across incremental builds), so never quote debug size as the app size.

applicationId `rkr.simplekeyboard.inputmethod.plus`, label "Simple Keyboard Plus", versionCode 147,
versionName 6.6, compileSdk 37, minSdk 24, VIBRATE the only permission.

**The two build types use different signing keys, so neither installs over the other.** Uninstall the
debug build the user tested before installing the release one. That also clears preferences, so the
changed defaults take effect.

**The two APKs use different signing keys, so neither can be installed over the other.** The debug
build the user already tested has to be uninstalled before the release one will install. That also
clears preferences, so the new defaults take effect on first run.

## Behaviour notes for the assistant

- No shortcuts. Finish work end to end, including a successful build.
- Read before writing. This is a 100-file AOSP-derived codebase; patterns matter more than cleverness.
- Do not add dependencies. `app/build.gradle` has an empty `dependencies` block and it stays that way.
- Do not touch `values-*` translation folders (Crowdin-managed).
- The per-keystroke path is sacred: no allocations, no IPC, cheapest-possible early exit.
- Coding style for new code is in `.ai/style.md` and differs from the inherited AOSP style. Do not
  reformat inherited code.

### Environment gotchas learned the hard way

- **The agent shell is unreliable.** It frequently returns before the command runs, reports stdout one
  command late, echoes commands character by character, and sometimes silently drops a command
  entirely. Do not trust inline output.
- **Working pattern**: put the work in a `.bat` under `.ai/tmp/build/`, have it redirect everything to
  a log file with `STEP`/`DONE` markers, invoke it with an **absolute** path
  (`cmd /c "d:\...\07-build.bat"`), then read the log with the file-reading tool. Relative paths and
  the `cwd` parameter are not reliable.
- PowerShell mangles `-D` JVM flags unless quoted: `java "-Dfile.encoding=UTF-8"`.
- `Out-File` defaults to UTF-16 on this shell, which reads back as spaced-out garbage. Use
  `-Encoding utf8`, or write logs from `cmd` instead.
- `sdkmanager` is deprecated and its shim hangs when piped. Use `android.exe` (cmdline-tools 23):
  `android sdk list --all <pattern>` and `android sdk install <package>[@<version>]`. It bootstraps
  itself on first run. Package names are paths now (`platforms/android-37.0`), not `platforms;android-37`.
- `apkanalyzer.bat` breaks on the space in the user profile path. Use `aapt2.exe` from
  `build-tools/37.0.0` instead.
- Debug builds are multidex; a single class can appear in several `classesN.dex`.

## Done this session

- Removed the password-field suppression from `InputLogic.performAutoTextExpansion`. Auto-text now
  fires in every field, as requested.
- Added the landscape fullscreen toggle:
  - `Settings.PREF_FULLSCREEN_LANDSCAPE` = `pref_fullscreen_landscape`, `readFullscreenLandscape`
    defaulting to **true** so upstream behaviour is preserved until the user opts out.
  - `SettingsValues.mFullscreenLandscape`.
  - `LatinIME.onEvaluateFullscreenMode` returns false early when the preference is off, with a null
    guard because the framework can call it before `loadSettings`.
  - `SwitchPreference` in `res/xml/prefs_screen_appearance.xml`, strings `fullscreen_landscape` and
    `fullscreen_landscape_summary`.
- Installed the toolchain: Temurin JDK 21 (winget), Gradle 9.6.0 and Android cmdline-tools 23
  (direct download, cmdline-tools SHA1 verified), SDK `platform-tools` + `platforms/android-37.0` +
  `build-tools/37.0.0`, and wrote `local.properties`.
- Regenerated `gradle/wrapper/gradle-wrapper.jar` via `gradle wrapper` (it is gitignored so absent
  from a clone). `gradlew.bat` now works.
- `gradle assembleDebug` BUILD SUCCESSFUL. This is the first time the Android-facing code was
  compiler-checked; it compiled with no errors and no warnings attributable to the new code.
- Copied the APK to `build/simple-keyboard-plus-debug.apk`.
- Made the fork installable alongside upstream:
  - `applicationId` -> `rkr.simplekeyboard.inputmethod.plus` in `app/build.gradle`. `namespace` left
    at `rkr.simplekeyboard.inputmethod` so no Java, proguard or XML class references had to change.
  - App name -> "Simple Keyboard Plus" / "Simple Keyboard Plus Settings" in `strings-appname.xml`.
    Needed, otherwise the two are indistinguishable in the launcher and IME picker.
  - Fixed `LocaleResourceUtils`: it derived the `getIdentifier` package from
    `R.class.getPackage().getName()` (the namespace), but the resource table package is the
    applicationId. Confirmed via `aapt2 dump resources` that the table package is now `...plus`, so
    the old code would have returned 0 for every exceptional locale name. Now uses
    `res.getResourcePackageName(R.string.english_ime_name)`, matching `KeyboardTextsSet` and
    `KeyboardLayoutSet`.
- Ran `assembleRelease` to check the R8 path. Succeeds, and R8 keeps every class that is only named
  from preference XML, so no `proguard-rules.pro` change is needed for `AutoTextSettingsFragment`.
- Changed the shipped defaults (see the table in `goal.md`). Each one was changed in **both** the
  preference XML `defaultValue` and the matching `Settings.read*` fallback. The three keypress options
  were changed via their resource bools instead, since both sides read those:
  - `config_default_vibration_enabled` false in `values/config-common.xml`
  - `config_default_sound_enabled` false and `config_default_key_preview_popup` true in all four
    `config-per-form-factor.xml` buckets (`values`, `sw430dp`, `sw600dp`, `sw768dp`) so the default no
    longer varies by screen size
- Language default is now English (US) only: `SubtypeLocaleUtils.getDefaultSubtypes` returns just the
  en_US subtype instead of matching the system locales. Removed the now-unused `Locale`, `HashSet` and
  `LocaleUtils` imports.
- Set up release signing: generated `keystore/simple-keyboard-plus.jks` (RSA 4096, PKCS12, alias
  `simple-keyboard-plus`, valid to 2056), added `keystore.properties`, wired both into
  `app/build.gradle` behind an existence check so a missing key yields an unsigned build rather than a
  failure, and added `keystore/`, `keystore.properties` and `*.p12` to `.gitignore`.
- Repository URL changed to `https://github.com/catchem88/simple-keyboard-plus` in
  `privacy_policy_url` and `license_url`. Rewrote `README.md` around the fork: added features, the
  side-by-side install note, the new defaults, build and signing instructions, and credit to upstream.
- Size and memory pass (details and rejected options in `guide.md`):
  - `RichInputConnection.appendTextBeforeCursor` caps the before-cursor cache, removing unbounded growth
    and the per-keystroke O(n) copy. Biggest win by far.
  - `RichInputConnection.onDestroy` shuts down the executor, via `InputLogic.onDestroy` from
    `LatinIME.onDestroy`. Fixes a real non-daemon thread leak that kept the service reachable.
  - `Settings.readKeyboardColor` resolves its default lazily instead of on every call.
  - `KeyDetector.detectHitKey` indexes instead of for-each, removing two allocations per touch move.
  - Proximity grid 32x16 -> 16x8 in `config-common.xml`.
  - Measured `resources.arsc` at 238,920 bytes, 38% of the APK, dominated by ~73 translation folders.
  - Added an "Added Features / Changes" section to `README.md` covering new features, a
    before/after defaults table, and the other changes.

## Verification performed

- `AutoText` logic: 61 assertions via `.ai/tmp/autotext/AutoTextCheck.java`, all pass. Compiles the
  real source against a two-method stub of `RichInputConnection`.
- Gradle build succeeds from clean.
- `aapt2 dump resources` confirms in the APK: `string/settings_screen_auto_text`,
  `auto_text_add/edit/keyword/expansion/empty/*_hint`, `layout/auto_text_dialog`,
  `menu/add_auto_text`, `id/action_add_auto_text`, `id/auto_text_dialog_keyword|expansion`,
  `string/fullscreen_landscape`, `string/fullscreen_landscape_summary`.
- `aapt2 dump xmltree` confirms the Auto-text `<PreferenceScreen>` row in `prefs.xml` points at
  `AutoTextSettingsFragment`, and the `pref_fullscreen_landscape` switch is in the appearance screen
  with `defaultValue=true`.
- Dex scan confirms `AutoTextSettingsFragment`, `settings/AutoText;`, `pref_auto_text`,
  `pref_fullscreen_landscape`, `textBeforeCursorEndsWith`, `performAutoTextExpansion` and
  `readFullscreenLandscape` are all present.
- `aapt2 dump badging`: `package: name='rkr.simplekeyboard.inputmethod.plus'`,
  `application-label:'Simple Keyboard Plus'`, single VIBRATE permission, `provides-component:'ime'`.
- `aapt2 dump xmltree --file AndroidManifest.xml`: manifest `package` is `...plus`, while all three
  components resolved against the namespace -
  `rkr.simplekeyboard.inputmethod.latin.LatinIME`,
  `...latin.settings.SettingsActivity`, `...latin.SystemBroadcastReceiver`. `method.xml`'s
  `settingsActivity` matches the real activity class.
- `aapt2 dump resources`: `Package name=rkr.simplekeyboard.inputmethod.plus id=7f`.
- Release dex scan: `AutoTextSettingsFragment`, `PreferencesSettingsFragment`,
  `KeyPressSettingsFragment`, `AppearanceSettingsFragment`, `ThemeSettingsFragment`,
  `ColorDialogPreference`, `SeekBarDialogPreference` all KEPT by R8.
- `apksigner verify --verbose --print-certs` on the release APK: Verifies, v2 scheme true, one signer,
  `CN=Simple Keyboard Plus, OU=simple-keyboard-plus, O=catchem88, C=ID`.
- Defaults confirmed in the compiled debug APK via `aapt2 dump xmltree`: `pref_show_number_row` true,
  `pref_show_special_chars` true, `pref_show_language_switch_key` false, `pref_space_swipe` true,
  `pref_fullscreen_landscape` false, and unchanged `auto_cap` true / `pref_delete_swipe` false.
- Keypress bools confirmed in the resource table: `config_default_key_preview_popup` true,
  `config_default_sound_enabled` false, `config_default_vibration_enabled` false, each with only a
  default-config entry. Neighbouring bools like `config_key_selection_by_dragging_finger` still show a
  `(sw600dp)` variant, which proves the collapse is real dedup and not a truncated dump - so the three
  keypress defaults are uniform across every screen size.
- New repository URL confirmed in the release APK strings.
- After the size/memory pass: `assembleRelease` and `assembleDebug` both BUILD SUCCESSFUL, release APK
  628,427 bytes. IDE diagnostics clean on all five modified Java files.

- Final 628,427-byte APK re-verified after every change (`.ai/tmp/build/25-final-verify.bat`):
  - `apksigner verify`: Verifies, v2 scheme true, one signer,
    `CN=Simple Keyboard Plus, OU=simple-keyboard-plus, O=catchem88, C=ID`, RSA 4096.
  - `package: name='rkr.simplekeyboard.inputmethod.plus'`, `application-label:'Simple Keyboard Plus'`.
  - `integer/config_keyboard_grid_height` = 8 and `integer/config_keyboard_grid_width` = 16 in the
    resource table, confirming the reduced proximity grid shipped.

## Design decisions and why

- **Expand before committing the trigger character.** `InputLogic.sendKeyCodePoint` routes digits 0-9
  through the asynchronous `sendDownUpKeyEvent`, which ignores batch edits, so committing the trigger
  and then deleting it would race. Running the check first and swallowing the trigger avoids the race
  and does fewer editor operations.
- **No password-field exception.** Was implemented, then removed on request. If it ever comes back the
  hook is `settingsValues.mInputAttributes.mIsPasswordField`.
- **One string preference for the whole auto-text table**, separated by `U+001E` / `U+001F`. Parsing is
  a `split` plus `indexOf`. `pref_enabled_subtypes` sets the precedent.
- **Trigger-code-point pre-check** makes the feature free when unused.
- **Plain suffix match, no word-boundary check.** Predictable and lets a keyword end in a space.
- **Fullscreen preference defaults to on.** Adding a setting should not silently change behaviour for
  existing users. The user who asked for it has to flip it in Settings > Appearance.
- **Fullscreen preference is a single boolean, not landscape-scoped logic**, because
  `config_use_fullscreen_mode` is already only true in `values-land` for phones. Turning the
  preference off simply always returns false.
- **Changed `applicationId` but not `namespace`.** Coexistence only needs a distinct applicationId.
  Changing the namespace as well would have meant renaming ~100 Java files plus every fully-qualified
  class reference in the preference and layout XML, for no benefit.
- **Chose the `.plus` suffix** so the relationship to upstream stays obvious and sorting groups them
  together. Any distinct id would work.
- **Left `shrinkResources` off.** Three `getIdentifier` call sites resolve resources by name at runtime,
  including every keyboard layout except QWERTY. Shrinking could strip them and the breakage would only
  show up at runtime, which is not worth ~50-100 KB. Reasoning and the keep rules it would need are in
  `guide.md`.
- **Did not apply `resConfigs`.** It would cut roughly 150-180 KB but makes the settings UI English
  everywhere. Keyboard languages would be unaffected, since layouts live in `res/xml` and labels come
  from the Java `KeyboardTextsTable`. Left to the user as a product decision.
- **Trimmed the text cache from the front, not the back.** Every reader works backwards from the cursor,
  so the tail is the only part that matters.
- **Capped at 2x the cache size, trimming to 1x**, so trimming is amortised rather than copying on every
  key press.

- Added the hideable launcher icon (option B of two that were offered):
  - Moved MAIN/LAUNCHER off `SettingsActivity` onto a new `activity-alias`
    `.latin.settings.SettingsLauncherAlias`. `SettingsActivity` keeps `exported="true"` with no intent
    filter because the system launches it by component name via `method.xml`, and a component the
    system needs must never be the one being disabled.
  - `ApplicationUtils.setLauncherIconVisible` toggles the alias with `DONT_KILL_APP`. It compares
    against `getComponentEnabledSetting` first and returns early when already correct, treating
    `COMPONENT_ENABLED_STATE_DEFAULT` as visible since the alias ships enabled.
  - `pref_show_app_icon`, default true, in the Preferences screen. `PreferencesSettingsFragment`
    applies it on change and also calls the same idempotent helper in `onCreate` to reconcile: prefs
    are backed up but component state is not, so a restored install could otherwise have the
    preference say hidden while the icon is visible, making the first toggle appear to do nothing.
  - README updated in both the features list and Get Started.
  - Verified in the release APK via `aapt2 dump xmltree`: the alias carries `targetActivity`,
    `enabled=true`, `exported=true`, and an intent-filter with both `action.MAIN` and
    `category.LAUNCHER`; `SettingsActivity` is present, exported, with no filter. Signature re-verified.
  - **Not runtime tested.** aapt2 badging cannot confirm this (it ignores aliases), so whether the icon
    actually appears and then disappears needs a device.

## Open questions / not done

- The debug build has been tested on a device by the user and works well. The **release** build has not
  been run on a device; R8 is only exercised in it, so it is worth a smoke test.
- The memory changes are compile-verified and reasoned about but **not runtime tested**. Two to watch on
  a device: typing a long stretch without moving the cursor, to confirm the text-cache trim never
  desyncs auto-caps or auto-text; and key accuracy near key edges after the proximity grid reduction.
- `metadata/en-US/full_description.txt` and `short_description.txt` still describe the original
  "Simple Keyboard" and its feature list. They are store listing text for F-Droid/Play, unused by the
  APK, so they were left alone.
- The keystore password is stored in plain text in the gitignored `keystore.properties`. Fine for a
  personal fork; worth changing if the key ever matters.
- Auto-text: no case-insensitive matching, no undo on backspace, no confirmation before removing an
  entry, not exposed in `res/xml/app_restrictions.xml`.
- The fullscreen toggle is re-evaluated when the keyboard next shows; it does not force a refresh of
  an already visible keyboard.
- `README.md` shows as modified in git, but not by this work.

## Backlog

- Install the APK and test both features on a device, especially auto-text in a password field and the
  landscape toggle taking effect. Also confirm both keyboards show up separately in Languages & input.
- The launcher icon is still identical to upstream's. Only the name distinguishes them. Consider a
  tinted variant of `res/drawable/ic_launcher.xml` if that turns out to be confusing.
- Back up `keystore/simple-keyboard-plus.jks` somewhere outside the repo. It is gitignored, so a fresh
  clone will not have it, and losing it means no signed updates for an already-installed app.
- Update `metadata/` store listing text if the fork is ever published to F-Droid or Play.
- Consider bumping `versionCode`/`versionName` to distinguish fork releases from upstream 6.6.
- Ask whether `README.md` should list Auto-text and the landscape toggle.
- Ask whether `.ai/tmp/autotext/` and `.ai/tmp/build/` should be kept (both are useful to re-run).
- Consider showing the auto-text entry count as the row summary in `prefs.xml`.
- Consider sorting the auto-text list by keyword once it can grow long.
- Consider `android.sync.suppressAgpWarnings` in `gradle.properties` to quieten the AGP 10
  deprecation warnings about options in `gradle.properties`.