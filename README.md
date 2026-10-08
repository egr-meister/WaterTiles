# WaterTiles

An offline water-intake tracker for Android. Daily progress is drawn as a mosaic of droplet-shaped tiles; each full tile represents 100 or 200 mL. Native Kotlin and Jetpack Compose. No accounts, no network, no analytics.

## Features

- A daily goal you choose (100–10,000 mL), stored as effective-dated records.
- A mosaic progress view with partial tiles, a reduced-capacity final tile, overflow text and a full text summary.
- 100 or 200 mL tile size, as a display preference.
- Add Water with amount, date and time, plus four editable quick portions (100/200/250/500 mL by default) with Undo.
- Entry editing (amount, date, time) and deletion with Undo.
- A monthly calendar with recorded-volume bars, goal-reached ✓ markers and day details.
- Optional local reminders: inexact, off by default, suppressed when the goal is reached or water was recorded recently.
- Three bundled themes: Lagoon, Lavender and Midnight.
- Reduced-animation option, font scaling, 48 dp touch targets and screen-reader labels.
- Clear all local data, which returns the app to setup.

## Architecture

There is one `:app` module with manual DI (`AppContainer`).

```
core/                 TimeProvider (injectable clock + zone), TodayClock (midnight/foreground refresh), Format
data/local            Room entities/DAOs/database, DataStore settings
data/repository       WaterRepository (entries + goals, transactions)
domain/entries        validation, DST-aware time resolution, quick-add guard, undo slot
domain/goals          effective-dated goal resolution
domain/mosaic         tile mathematics + text equivalents
domain/reminders      slot computation and delivery eligibility
domain/calendar       month grid math
reminders/            AlarmManager scheduling, notification, receivers
ui/today ui/entry ui/calendar ui/settings ui/setup ui/theme ui/components ui/navigation
```

The UI uses Compose and Material 3 with customized colour schemes, Navigation Compose, ViewModel + StateFlow and `collectAsStateWithLifecycle`. All database work runs on Room's coroutine dispatchers, off the main thread.

## Toolchain (pinned)

| Component | Version |
|---|---|
| JDK | 17 (Temurin in CI) |
| Gradle (wrapper) | 8.14.3 |
| Android Gradle Plugin | 8.12.0 |
| Kotlin / Compose compiler plugin | 2.2.0 |
| KSP | 2.2.0-2.0.2 |
| Compose BOM | 2025.06.00 |
| Room | 2.7.1 |
| DataStore | 1.1.7 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

## Build commands

```bash
./gradlew testDebugUnitTest        # focused unit tests
./gradlew lintRelease              # release lint
./gradlew assembleDebug            # debug APK, no secrets needed
./gradlew assembleRelease bundleRelease   # signed release (needs credentials, see below)
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk`: install this one locally.
- `app/build/outputs/bundle/release/app-release.aab`: **only the AAB is uploaded to Google Play.**

R8 is off by default, so the first verified release is non-minified. After that release is verified, enable shrinking with `-PwatertilesMinify=true`, repeat the checks and keep `app/build/outputs/mapping/release/mapping.txt`.

## Tile mathematics

With G = the day's goal, T = tile size and V = the recorded volume:

- There are `ceil(G / T)` tiles, and each tile's capacity is `min(T, remaining goal)`. When the goal isn't divisible by the tile size, the final tile is smaller. For example, a 1,500 mL goal with 200 mL tiles gives 7 × 200 + 1 × 100. The detail sheet labels that final tile, and it is drawn slightly smaller.
- Tiles fill row by row. Each tile is filled with `min(capacity, remaining volume)`, so its fraction is `filled / capacity`. Volume is never rounded to whole tiles. For example, 150 mL with 100 mL tiles gives one full tile and one tile at 50%.
- The fill rises from the bottom and is clipped to the droplet. It illustrates volume and is not an area measurement. Each tile's accessible description states its exact mL.
- Overflow keeps the mosaic full and adds no extra tiles. The header shows the actual total, the actual percentage (floored, and it can go above 100%) and "N mL above your selected goal".
- Filled, partial and empty tiles differ by outline and pattern as well as colour. Full tiles have a thick outline and a highlight stroke, partial tiles a waterline with dashes, and empty tiles a dashed outline.
- Totals are always derived from the entries as `Long`. Tile size and theme never change stored data.

## Goals, calendar and date attribution

- A goal change can apply **today** or **tomorrow**. Records dated before the effective date are never rewritten. A newer change supersedes any pending future change.
- Each day uses the goal effective on that date. A day with no saved goal shows "Recorded volume" with a volume-only mosaic and no percentage.
- Every entry stores its absolute timestamp, logged local date, logged local time and zone ID. History is grouped by the **stored local date**, so travelling never moves entries between days. Explicitly editing an entry updates its date attribution, and moving it to another date updates both days.
- An ambiguous wall-clock time (DST fall-back) resolves to the earlier offset. A nonexistent time (DST spring-forward) is rejected with a message.
- Future dates are read-only. Days without entries say "No records" and make no claim about intake. No history or example intake is preloaded.

## Reminders: limitations and permissions

- Reminders are off by default. `POST_NOTIFICATIONS` is requested on Android 13+ only when you turn them on. If you deny it, tracking keeps working and Settings shows an optional link to the system notification settings.
- Scheduling uses a single **inexact** one-shot alarm (`setAndAllowWhileIdle`) for the next slot, so delivery is approximate and Android may delay it. The app does not request `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`, and it has no foreground service, wake lock or battery-exemption request.
- At delivery the app re-checks several conditions and skips the reminder if any one fails:
  - reminders are still enabled
  - the slot still fits the current window
  - the reminder is not stale (late by at most min(45 min, interval/2))
  - today's goal hasn't been reached
  - no entry has been recorded since the previous slot boundary
- Missed reminders are never replayed. Every reminder uses one notification ID.
- The app reschedules after each delivery, after settings changes, on boot (`RECEIVE_BOOT_COMPLETED`), on time and time-zone changes, after an app update and on app start. Receivers aren't exported, and PendingIntents are explicit and immutable.
- **Force-stopping the app cancels its alarms. Reminders resume the next time you open WaterTiles.**

## Offline, privacy, storage and backup

- The only permissions are `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`. There is no `INTERNET` and no `ACCESS_NETWORK_STATE`. AndroidX also adds the app-private signature permission `<appId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which is not a runtime permission. CI checks the packaged APK against this allowlist (`scripts/check_permissions.py`).
- Entries and goals live in Room. Preferences live in DataStore. Both are in app-private storage.
- `allowBackup="false"`, `fullBackupContent` and `dataExtractionRules` exclude everything from cloud backup and device-to-device transfer.
- The in-app Privacy screen describes local storage and local notifications.
- Room schemas are exported to `app/schemas/` and the database has explicit migrations. Destructive migration is never enabled. The first build generates `app/schemas/.../1.json`, which you should commit.

## Signing (PKCS12) and GitHub Secrets

The release build uses `signingConfigs.release` with `storeType = "PKCS12"`. Credentials come from environment variables or from an uncommitted `keystore.properties` file. When they are missing, `assembleRelease` and `bundleRelease` fail. The build never falls back to debug signing, and debug builds don't need any secrets.

These are the repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64` of the `.p12` file |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password (equal to the store password for PKCS12) |

For a local build, create `keystore.properties` in the project root. It is gitignored.

```
storeFile=../keys/WaterTiles/watertiles-release.p12
storePassword=…
keyAlias=watertiles
keyPassword=…
```

CI (`.github/workflows/android.yml`) does the following:

1. Installs JDK 17 and SDK Platform 36.
2. Runs the unit tests and release lint.
3. Decodes the keystore into the runner temp directory.
4. Builds the signed APK and AAB.
5. Runs `apksigner verify --print-certs` and fails on `CN=Android Debug` or on a signer mismatch.
6. Runs `jarsigner -verify` plus a signer fingerprint check on the AAB. A self-signed upload certificate is accepted.
7. Checks permissions and native alignment, and reports the artifact sizes.
8. Uploads the artifacts and deletes the keystore.

**Key roles:** the keystore here is the **upload key**. With Play App Signing, Google holds the **app signing key** that re-signs what users install. Keep the upload key and its passwords backed up. If you lose them, request an upload-key reset in Play Console.

## 16 KB page size

CI inspects both the APK and the AAB for `.so` files (`scripts/check_native_alignment.py`). For each library it checks ELF `PT_LOAD` alignment of at least 16 KB, and when native code is present it also runs `zipalign -c -P 16`. The app has no NDK code of its own. Any native library would come from a dependency (DataStore can ship `libdatastore_shared_counter.so`), and the CI report records what was found. Targeting API 36 alone doesn't prove 16 KB compatibility, so runtime compatibility is claimed only after testing on a 16 KB device or emulator image. See `VERIFICATION.md`.

## Local installation and logcat

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat --pid=$(adb shell pidof -s com.egrmeister.watertiles)
```

## Android 16 notes

- Edge-to-edge is enforced, so the app calls `enableEdgeToEdge()` and every screen handles system bar, cutout and IME insets.
- Predictive Back is on (`enableOnBackInvokedCallback`). Back from a day goes to the Calendar, unsaved entry forms ask before discarding, and Back from Today exits.
- The app imposes no orientation or resizability restrictions. Large screens get a navigation rail and a two-pane Today layout.

## Verification status

Artifact sizes and pending manual checks are listed in [VERIFICATION.md](VERIFICATION.md).
