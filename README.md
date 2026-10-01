# RMX3830 Hotspot Enhancer

Advanced hotspot controls injected into the native Realme/Android hotspot Settings screen.

**Release:** 1.0.0  
**Developer:** KoroZzi  
**Target:** realme C51 (RMX3830), Android 15 / realme UI  
**Architecture:** Magisk + LSPosed-Irena

## Features

- Connected-device count and live list.
- Hostname, IP and MAC when Android/Tethering provides them.
- Temporary disconnect, allow and block actions.
- White list with a manual enable switch.
- Black list with unblock support.
- Device limit: 1–10.
- Configurable hotspot idle auto-shutdown.
- Device isolation when supported by the firmware.
- Native-style buttons, separators and touch feedback.
- Full-screen advanced settings interface.

Stock Realme controls such as SSID, password, security, band, hidden SSID and compatibility remain in the native Settings UI.

## Requirements

- realme C51 RMX3830
- Android 15 / realme UI
- Magisk root
- LSPosed-Irena with modern API support
- LSPosed scope: `system` and `com.android.settings`

## Installation

1. Install the Magisk ZIP from the 1.0.0 release.
2. Reboot.
3. Open the native hotspot settings.
4. Open **Advanced hotspot settings**.

No separate launcher application is required.

## Notes

Hardware and firmware capabilities are device-dependent. The RMX3830 firmware reports a maximum Soft AP client resource of 10.

If a connected device does not provide a hostname through Android tethering information, the module cannot invent its real device name; available IP and MAC information can still be displayed.

For debugging: `logcat -d -s RMX3830Hotspot:I '*:S'`

## Project status

**1.0.0 is the first public release.** Future changes should use new versioned releases.

## Developer

**KoroZzi**

Repository: https://github.com/korozzi/RMX3830-Hotspot-Enhancer
