# Verification notes

| Check | Status | Notes |
|---|---|---|
| Unit tests (`testDebugUnitTest`) | pending: first CI run | Covers tile counts, reduced final tile, partial fill, exact totals and overflow, tile-size changes, editing/deletion/Undo, historical goals, leap days and calendar boundaries, reminder eligibility/stale/goal-reached suppression, DST and travel |
| Release lint | pending: first CI run | |
| Signed non-minified APK + AAB | pending: first CI run | |
| `apksigner verify --print-certs` / no debug cert / signer match | pending: first CI run | Expected upload cert SHA-256: `a04f33a2097bcd35d7953bc4f0bed41bc779c99daa425b2d9aedf2e72da20b34` |
| AAB `jarsigner` integrity + signer | pending: first CI run | |
| Packaged permission allowlist | pending: first CI run | |
| Native `.so` inventory / 16 KB alignment | pending: first CI run | |
| Release artifact size | pending: first CI run | See the CI job summary |
| R8 + resource shrinking | not enabled yet | Enable after the non-minified release is verified (`-PwatertilesMinify=true`) |
| `adb install` of signed release APK + logcat | pending (device) | |
| Offline launch, all three themes, mosaics, entries, calendar | pending (device) | |
| Notifications: permission grant/deny, delivery, suppression | pending (device) | |
| Reboot recovery, process recreation, rotation | pending (device) | |
| Font scaling, TalkBack, data reset | pending (device) | |
| 16 KB runtime test (16 KB emulator image) | pending (device) | Only needed if native libraries are present |

Record for each manual run: device, Android version, artifact size, result.
