# RMX3830 Hotspot Diagnostic v0.3.2

Read-only diagnostic Modern Xposed API 102 module for realme C51 RMX3830 / Android 15. It does not change hotspot settings yet. It probes SoftApConfiguration, SoftApManager and Settings methods and logs signatures/events only; SSIDs, MACs, passwords, arguments and return values are not logged.

Build: GitHub Actions -> Build RMX3830 Hotspot Diagnostic -> Artifacts. The Magisk artifact contains a systemlessly mounted APK plus service script. LSPosed scopes: android and com.android.settings.

Known device data from the supplied technical profile: Soft AP hardware/resource maximum 10 clients; 2.4 GHz channels 1-13; 5 GHz 36/40/44/48; 6 GHz empty; STA+AP concurrency was not supported in the diagnostic dump. These are device-specific observations, not promises of expanded capability.
