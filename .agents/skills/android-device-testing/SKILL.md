---
name: android-device-testing
description: >-
  Step-by-step methodology for real-device ADB testing of UniShuttle on a
  HyperOS (Xiaomi/POCO) device. Covers device detection, Room DB inspection,
  Logcat capture, FCM topic verification, and HyperOS-specific limitations.
  Use when performing physical device testing, validating Room persistence,
  or verifying FCM topic subscriptions.
---

# Android Real-Device Testing — UniShuttle

## Device & ADB Setup

```powershell
$adb = "C:\Users\yamle\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l              # Expect: 431070e7 device
& $adb shell dumpsys power | Select-String "mWakefulness="  # Expect: Awake
```

## HyperOS Limitations

**CRITICAL:** `adb shell input tap` and `adb shell input keyevent` throw
`SecurityException: Injecting input events requires INJECT_EVENTS` on this device.

**Workaround:** Instruct the user to perform UI touch interactions manually on the
physical screen. Verify outcomes via Logcat and Room DB inspection instead of
relying on automated ADB input injection.

## Logcat Workflow

```powershell
& $adb logcat -c                                    # Clear buffer before each test stage
& $adb logcat -d -s NotificationRepo:V              # Dump tagged logs after interaction
& $adb logcat -d | Select-String "Subscribed","Unsubscribed","FCM","Exception"
```

## Room Database Inspection (Python + ADB exec-out)

Use `run-as` via `exec-out` — NOT `adb shell run-as` with `sqlite3`, which
requires root or a device-side sqlite3 binary.

```python
import subprocess, sqlite3, os

adb = r"C:\Users\yamle\AppData\Local\Android\Sdk\platform-tools\adb.exe"
pkg = "com.unishuttle.app.debug"
out_db = r"C:\Users\yamle\OneDrive\Desktop\UniShuttle Android\unishuttle_clean.db"
out_wal = out_db + "-wal"

with open(out_db, "wb") as f:
    subprocess.run([adb, "exec-out", "run-as", pkg, "cat", "databases/unishuttle.db"], stdout=f)
with open(out_wal, "wb") as f:
    subprocess.run([adb, "exec-out", "run-as", pkg, "cat", "databases/unishuttle.db-wal"], stdout=f)

conn = sqlite3.connect(out_db)
conn.row_factory = sqlite3.Row
cur = conn.cursor()
for table in ['stop_alerts', 'notification_preferences', 'notifications']:
    cur.execute(f"SELECT * FROM {table}")
    rows = cur.fetchall()
    print(f"Table '{table}' ({len(rows)} records):")
    for r in rows:
        print("  ", dict(r))
conn.close()
```

Save this script to the brain dir scratch folder, then:
```powershell
python scratch/pull_clean_db.py
```

## Stop Alert Test Sequence

For each stage (arm, change, cancel), follow this protocol:
1. `adb logcat -c` — flush buffer.
2. Instruct user to perform touch action.
3. `adb logcat -d -s NotificationRepo:V` — capture FCM topic log.
4. Run `pull_clean_db.py` — verify Room record.
5. Report exact topic string, Room state, task success/failure, and any exceptions.

**Never infer success from UI label changes alone.** Always confirm via Logcat
AND Room DB inspection.

## Screenshot

```powershell
& $adb shell screencap -p /sdcard/screen.png
& $adb pull /sdcard/screen.png "C:\path\to\output.png"
```

If the screenshot is black: check `mWakefulness=Awake` first.

