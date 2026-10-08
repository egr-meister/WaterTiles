#!/usr/bin/env python3
"""Fail if the packaged APK requests any permission outside the allowlist.

Usage: check_permissions.py <aapt2> <apk> <applicationId>
"""
import re
import subprocess
import sys

aapt2, apk, app_id = sys.argv[1:4]
ALLOWED = {
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    # Signature-level permission AndroidX core adds for non-exported dynamic receivers.
    f"{app_id}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
FORBIDDEN = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.USE_EXACT_ALARM",
    "android.permission.WAKE_LOCK",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
}

out = subprocess.run([aapt2, "dump", "permissions", apk], check=True, capture_output=True, text=True).stdout
print(out)
requested = set(re.findall(r"uses-permission(?:-sdk-23)?: name='([^']+)'", out))
unexpected = requested - ALLOWED
bad = requested & FORBIDDEN
if unexpected or bad:
    print("Unexpected permissions:", sorted(unexpected | bad))
    sys.exit(1)
print("Permissions OK:", sorted(requested))
