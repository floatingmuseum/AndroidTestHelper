# AGENTS.md

## Project Mission

AndroidTestHelper is a Kotlin Multiplatform / Compose Desktop console for testing Android tablets and other Android devices through bundled Android platform-tools.

The app is a desktop operations tool, not an Android app. It must support Windows, macOS, and Linux from one shared UI codebase, using the matching `adb` binary under the root `plugins/android/platform-tools/` directory or the packaged install directory.

## Repository Layout

- `desktopApp/`
  - Desktop JVM application entry point and packaging module.
  - Keep this module thin. It should own window setup, app title, initial size, and native distribution metadata only.
- `shared/src/commonMain/`
  - Shared Compose UI, state controllers, common models, parser helpers, test-module contracts, and `expect` declarations.
  - Put cross-platform UI and business-facing test flows here.
- `shared/src/jvmMain/`
  - JVM/Desktop `actual` implementations.
  - Put process execution, ADB path resolution, local file system work, native file pickers, AWT cursor behavior, app runtime paths, and desktop interop here.
- `shared/src/jvmTest/`
  - JVM tests for parsers, command builders, path resolution, cache naming, and desktop-specific logic.
- `shared/src/commonMain/composeResources/`
  - Shared Compose resources such as icons and localized assets.
- `plugins/athplugin/`
  - Bundled ATHPlugin APK files used by development runs and desktop distributions.
- `plugins/android/platform-tools/`
  - Bundled Android platform-tools for Windows, macOS, and Linux.
  - Do not ignore, delete, or casually replace this folder. The app depends on it at runtime.
- `ATHPLUGIN_API.md`
  - Source-of-truth handoff contract for ATHPlugin ContentProvider calls.

## Current App Structure

The main Compose orchestration is still in:

- `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/App.kt`

`App.kt` now owns global device selection, shared command log, plugin banner state, and several module orchestration effects. Do not treat it as a pure rendering file.

The shell UI is split into:

- `TestModule.kt`
  - Current modules: `设备`, `应用`, `数据填充`, `文件管理`, `日志`, `设置`.
- `MainScaffold.kt`
  - Module switcher, split top/bottom layout, shared device panel, and shared command log panel.
- Bottom panel
  - Shared device connection panel and command log.
  - The bottom panel is draggable and remains visible across modules.

When adding new test features, add or extend a test module instead of hard-coding a one-off page into the shell.

## Module Boundaries

### Device Module

- Shared UI/model contract:
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/device/DeviceAdb.kt`
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/device/DeviceTestPanel.kt`
- JVM implementation:
  - `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/device/DeviceAdb.jvm.kt`
- Current responsibilities:
  - Device system information and properties.
  - CPU, memory, display, battery, ROM, and network-style diagnostics.
  - Screenshot capture to device temp path, then `adb pull` to a user-selected local directory.
  - APK/XAPK installation.
  - Reboot, shutdown, keyevent shortcuts, current activity lookup.
  - Battery simulation through `dumpsys battery`.
  - Screen size and density changes through `wm size` / `wm density`.
- Keep dangerous device actions explicit in UI and command log.

### App Module

- Shared UI/model contract:
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/app/AppAdb.kt`
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/app/ApplicationTestPanel.kt`
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/app/PluginCheckBanner.kt`
- JVM implementation:
  - `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/app/AppAdb.jvm.kt`
- Current responsibilities:
  - Third-party and system app lists.
  - App search, app tiles, disabled-state display, list progress, and system-app cache.
  - App details split by section: basic, permissions, activities, services, receivers, providers, signatures.
  - Launch, force stop, clear data, enable, disable, uninstall, and APK export.
  - ATHPlugin install/update/enable prompt and ignore state.
- App list and icon loading use a plugin-first strategy with ADB fallback. Keep fallback behavior working unless the task explicitly removes it.
- System app list caching is intentional. Third-party apps auto-load on module entry; system apps can load from cache and refresh on demand.

### Data Fill Module

- Shared code:
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/datafill/`
- JVM implementation:
  - `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/datafill/DataFillAdb.jvm.kt`
- Current behavior:
  - Reads storage from `adb -s <serial> shell df -k /data`.
  - Writes fill files under `/sdcard/AndroidTestHelperFill`.
  - Uses chunked `dd` writes so progress is visible.
  - Current chunk size is `128MB`.
  - Stopping a fill task must cancel the coroutine and destroy the running ADB process.
  - After stopping, clear fill progress and refresh device storage because already-written data remains on the device.
- Do not add frontend-only fake storage values. Read real device state through ADB.

### File Manager Module

- Shared UI/state/contract:
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/filemanager/`
- JVM implementation:
  - `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/filemanager/FileManagerAdb.jvm.kt`
  - `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/filemanager/FileManagerDesktopInterop.jvm.kt`
- Current behavior:
  - Default root comes from settings and is currently constrained to `/sdcard` or `/`.
  - Lists remote files with `ls -la`.
  - Exports with `adb pull`.
  - Uploads dropped local files with `adb push`.
  - Deletes with `rm -rf`, but UI must confirmation-gate deletion and code must refuse root/current file-manager root deletion.
  - Creates files with `touch` and directories with `mkdir -p`.
  - Creation defaults are intentionally `NewFile.txt` and `NewDir`.
  - Refresh should usually target the clicked entry's directory, not blindly reset the whole tree.
  - After create/delete/upload, refresh the affected parent or target directory.
- Quote shell paths carefully. Remote paths can contain spaces and quotes.

### Log Module

- Shared UI/state/contract:
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/devicelog/`
- JVM implementation:
  - `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/devicelog/DeviceLogAdb.jvm.kt`
- Current behavior:
  - Continuously captures `logcat -b all -v threadtime -v year *:V` with year and millisecond precision; no command templates or parameter editor.
  - Formats text as date/time, PID-TID, tag, process, priority, message. Process names come from a best-effort current `ps -A -o PID,NAME` snapshot refreshed every 5 seconds; missing names are `-`, and historical PID names may differ.
  - Keyword filters are literal OR terms separated by `|`, with optional case sensitivity; filtering happens locally against formatted lines, never via shell interpolation.
  - Always saves the full log; a non-empty filter additionally saves `_filtered.log`. Capture settings are fixed for the active capture and both paths remain available on stop/interruption.
  - Filter history persists in `AppRuntimePaths.cacheDirectory()/log_filter_history.json`, newest first, deduplicated, capped at 50 entries; save/reuse/delete must retain the case option.
  - Writes logs under `AppRuntimePaths.logsDirectory()`.
  - Uses a floating stop button while capture is active.
  - Stop is observable and should produce `STOPPED`; USB/device interruption should produce `INTERRUPTED`.
  - Long-running logcat processes must be destroyed when stopped or cancelled.
- Do not block unrelated UI with a stale global running flag while log capture has its own capture state.

### Settings Module

- Shared UI/state:
  - `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/settings/`
- JVM repository:
  - `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/settings/JvmSettingsRepository.kt`
- Current settings:
  - Show command timestamp.
  - Show command duration.
  - File-manager default root path.
- Settings persist to `AppRuntimePaths.cacheDirectory()/settings.json`.
- Normalize settings after load. Unknown or invalid file-manager root values must fall back to `/sdcard`.

## ADB Rules

- Always run device-targeting commands against an explicit device serial:
  - Use `adb -s <serial> ...`.
  - Never rely on implicit single-device behavior.
- Device discovery uses:
  - `adb devices -l`
  - Show serial number and model to users.
- Any command executed against ADB, or any status describing a chosen ADB operation, should be appended to the command log before execution when practical.
- Keep ADB implementation behind module abstractions:
  - Common API in `commonMain`.
  - Process implementation in `jvmMain`.
  - Shared execution utilities in `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/adb/AdbShell.kt`.
- `AdbShell.executeAdb` and `executeAdbBinary` already handle process execution, logging, timing, cancellation polling, and forcible process cleanup. Reuse them for ordinary short-lived commands.
- Long-running streaming commands may manage `ProcessBuilder` directly, but must still log the display command and destroy processes on stop/cancellation.
- Resolve `adb` from bundled platform tools:
  - Windows: `plugins/android/platform-tools/platform-tools-latest-windows/platform-tools/adb.exe`
  - macOS: `plugins/android/platform-tools/platform-tools-latest-darwin/platform-tools/adb`
  - Linux: `plugins/android/platform-tools/platform-tools-latest-linux/platform-tools/adb`
- `AdbShell` searches from `AppRuntimePaths.installDirectory` and `user.dir` upward before falling back to plain `adb`.

## Runtime Files

- JVM-side internal runtime files should live under `AppRuntimePaths`, not `user.home` or raw `java.io.tmpdir`.
- Current runtime roots:
  - `AppRuntimePaths.cacheDirectory()` -> `AndroidTestHelperData/cache`
  - `AppRuntimePaths.logsDirectory()` -> `AndroidTestHelperData/logs`
  - `AppRuntimePaths.tempRootDirectory()` -> `AndroidTestHelperData/temp`
  - `AppRuntimePaths.createTempDirectory(prefix)` for temporary working directories.
- User-chosen exports remain user-controlled:
  - Screenshots, exported APKs, and file-manager exports go to the chosen local directory.
- `AndroidTestHelperData/` is ignored. Do not treat it as source.

## ATHPlugin Contract

The helper app package is:

- `com.floatingmuseum.android.test.helper.plugin`

The provider authority is:

- `com.floatingmuseum.android.test.helper.plugin.provider`

Current provider calls:

- App list:
  - `content://com.floatingmuseum.android.test.helper.plugin.provider/apps?isSystem=<true|false>`
- App detail:
  - `content://com.floatingmuseum.android.test.helper.plugin.provider/details/<packageName>?section=<section>`
- App icon binary:
  - `content://com.floatingmuseum.android.test.helper.plugin.provider/icon/<packageName>`

Rules:

- Keep `ATHPLUGIN_API.md` in sync with every ATHPlugin ContentProvider call.
- When adding, removing, or changing a plugin URI, query parameter, response field, parsing rule, version rule, or fallback behavior, update `ATHPLUGIN_API.md` in the same change.
- The app list provider response is strict JSON. Do not add unknown fields unless desktop parsing is updated.
- Details provider responses may contain extra fields, but desktop currently consumes `items[].label` and `items[].value`.
- Icons are binary streams read through `exec-out content read`; do not wrap them in JSON or Base64.
- If ATHPlugin is missing, disabled, outdated, or any provider call fails, preserve standard ADB fallback behavior.
- Plugin ignore state is tied to the bundled ATHPlugin APK version via `pluginCheckIgnoreKey()`, not the desktop app version.
- Bundled APK selection should prefer the newest `ATHPlugin*.apk` candidate from `plugins/athplugin` or the packaged `resources/plugins/athplugin` directory based on parsed version info.

## Application Fallback Behavior

Standard ADB fallback for app list combines:

- `pm list packages -f -U`
- `pm list packages -3`
- `pm list packages -s`
- `pm list packages -d`
- `dumpsys package`

Fallback app metadata may require pulling base APKs into an `AppRuntimePaths.createTempDirectory(...)` workspace and parsing `AndroidManifest.xml` / `resources.arsc` locally.

Rules:

- Keep icon cache version-aware. Cache keys should include package identity plus versionCode or versionName when available.
- System-app cache should not embed icon bytes in the JSON payload; icons belong in the icon cache directory.
- Clear-cache behavior must clear both the system app list cache and app icon cache.
- Uninstall commands differ:
  - Third-party: `adb -s <serial> uninstall <package>`
  - System app for user 0: `adb -s <serial> shell pm uninstall --user 0 <package>`
- Some ADB commands can return exit code 0 with failure text. Validate command output for destructive app actions where existing helpers already do so.

## UI Guidelines

- Keep controls dense, operational, and diagnostic. This is a test console, not a marketing interface.
- Prefer explicit Chinese labels for user-facing UI, matching the current app.
- Shared device selection and command log should remain visible across modules.
- The command log is a diagnostic surface. Do not hide commands that mutate device state.
- Selected devices and selected modules should be distinguished by color, not by adding noisy text prefixes.
- Dangerous operations need clear confirmation or clear danger styling:
  - shutdown/reboot variants,
  - uninstall,
  - clear data,
  - file delete,
  - battery/display mutation commands.
- Avoid placing platform-specific Compose APIs directly in common UI unless they are available in common source sets.
  - If needed, use `expect` / `actual`, as with `verticalResizePointerIcon()` and desktop interop helpers.
- Keep long-running operations visible and cancellable.

## Source-Set Rules

- Put pure models, parse helpers, shared UI, and test-module state in `commonMain`.
- Put process execution, file-system OS detection, ADB path resolution, AWT cursor code, native picker code, and desktop-specific APIs in `jvmMain`.
- Keep `desktopApp/src/main/.../main.kt` limited to window setup:
  - Current default size: `1280 x 860`.
- Avoid adding Android app module code unless the project target changes. This project controls Android devices; it is not currently an Android app.
- Preserve the current package:
  - `com.floatingmuseum.android.test.helper`

## Testing Expectations

- Add focused JVM tests for parser, command-builder, cache-name, runtime-path, and command-output handling changes.
- Do not require a physical Android device for parser/unit tests.
- Good existing test areas include:
  - `parseAdbDevices`
  - `parseDfStorageInfo`
  - app list and `dumpsys package` parsers
  - app detail parsers
  - uninstall command builders and success/failure output checks
  - device info parsers
  - screenshot path planning
  - quick-action command builders
  - file-manager path/listing helpers
  - log filename/end-state behavior
  - `AppRuntimePaths`
  - settings normalization/persistence helpers
- Device integration should be verified manually or with emulator/device automation when available.

## Validation Commands

On Windows PowerShell:

```powershell
.\gradlew.bat :shared:jvmTest
.\gradlew.bat :desktopApp:compileKotlin
```

Useful run command:

```powershell
.\gradlew.bat :desktopApp:run
```

The local Gradle wrapper may need access to the Gradle distribution lock under `D:\softwares\AppData\.gradle`. If a sandboxed run fails with `gradle-*-bin.zip.lck (拒绝访问。)`, rerun the same Gradle command with the required external execution permission.

Windows JVM verification can also fail from native memory pressure (`errno=1455`, G1 virtual space). Treat that as an environment failure unless source errors are present.

## Git / Generated Files

- Build outputs and IDE-local files are ignored.
- Keep Gradle wrapper files tracked.
- Keep `plugins/android/platform-tools/` tracked unless the distribution strategy changes intentionally.
- Keep bundled `plugins/athplugin/ATHPlugin*.apk` assets tracked when the desktop app is expected to ship them.
- Do not commit `.gradle/`, `.kotlin/`, `build/`, `.idea/`, `*.iml`, or `AndroidTestHelperData/`.
- Before large refactors, check `git status --short` and avoid reverting user changes.

## Implementation Posture

- Keep changes scoped. Avoid broad refactors while adding or fixing one test flow.
- Prefer small abstractions only when they protect source-set boundaries, keep ADB execution centralized, or prevent repeated process-management code.
- When stopping or cancelling long-running ADB tasks, make cancellation observable and clean up child processes.
- Do not add fake UI state for device facts. Read actual state through ADB or show an explicit unavailable/error state.
- Preserve fallback paths unless the user explicitly asks to remove them.
- When a change touches both desktop and ATHPlugin contracts, update the desktop code and `ATHPLUGIN_API.md` together; the plugin implementation lives in a separate repo.
