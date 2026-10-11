# RMX3830 Hotspot Enhancer

Advanced hotspot controls injected directly into the native Realme/Android hotspot Settings screen.

**Source version:** 1.0.1 (audit branch; not yet device-validated)  
**Developer:** KoroZzi  
**Target device:** realme C51 (RMX3830)  
**Android:** Android 15 / realme UI  
**Architecture:** Magisk + LSPosed-Irena

## Features

The module adds **Advanced hotspot settings** to the existing system hotspot Settings page. It does not add a separate launcher application.

### Connected devices
- Live connected-device count.
- Device hostname when Android/Tethering provides it.
- IP address when available.
- MAC address.
- Disconnect a connected device.
- Add a connected device to the allow list.
- Block a connected device.

### Access lists
- **White list** — maintain allowed devices.
- Manual **Enable white list** switch.
- Adding a device does **not** enable the white list automatically.
- Removing a device updates the configuration immediately.
- **Black list** — maintain blocked devices.
- Unblock devices from the black list.

### Additional settings
- **Device limit:** 1–10 devices.
- **Auto shutdown:** configurable idle timeout.
- **Device isolation:** prevents connected devices from exchanging traffic directly when supported by the firmware.

The module leaves the stock hotspot controls such as SSID, password, security, band, hidden SSID and compatibility in the native Realme Settings UI.

## Requirements

- realme C51 RMX3830
- Android 15 / realme UI
- Unlocked/rooted device with Magisk
- LSPosed-Irena with modern API support
- LSPosed scope: `system` and `com.android.settings`

## Installation

1. Install the ZIP artifact attached to the intended release. Do not package an APK from a debug build.
2. Reboot.
3. Open **Settings → Personal hotspot / Hotspot settings**.
4. Open **Advanced hotspot settings**.
5. Configure the required options.

No separate launcher application is required.

## Important

Hardware and firmware capabilities are device-dependent. The module only exposes functions that can be handled by the Android/Realme Wi-Fi framework on the device.

The maximum client limit reported by the RMX3830 firmware is 10.

If a connected device does not provide a hostname through Android tethering information, the UI cannot invent its real device name. IP and MAC information may still be shown.

## Troubleshooting

If the advanced entry does not appear, verify that the LSPosed module is enabled for both `system` and `com.android.settings`, then reboot.

Debug command:

```sh
logcat -d -s RMX3830Hotspot:I '*:S'
```

## Project status

**1.0.0 is the first public release.** The `audit/hardening-v1.0.1` branch contains source-level fixes and is not a published or device-validated release.

### Audit changes in 1.0.1 source
- Explicitly disable Android debugging in the release build type.
- Persist allow-list edits immediately without silently enabling allow-list mode.
- Keep device MAC addresses, IP addresses and hostnames out of logcat.
- Prevent temporary disconnect from removing a block that existed before the action.
- Make “Unblock” remove only the block-list entry; allow-list membership remains a separate user choice.

### Validation status
These changes have been reviewed statically, but have **not** been built or tested on an RMX3830 device in this environment. The repository currently has no Gradle wrapper checked in, so a reproducible build should be run with a compatible local Gradle/Android SDK setup or a wrapper should be added before release. Verify that the packaged APK is a release build and test client management, both access lists, hotspot restart, and isolation on the target firmware before publishing.

## Developer

**KoroZzi**

Project: https://github.com/korozzi/RMX3830-Hotspot-Enhancer
