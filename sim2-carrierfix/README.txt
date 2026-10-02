RMX3830 SIM2 CarrierConfig Fix 1.1.0

Target:
  realme C51 / RMX3830
  Android 15 / realme UI
  Magisk

Observed in the RMX3830 diagnostic dump:
  - DSDS and two radio stacks are present.
  - SIM2 is loaded and identified as Beeline UZ / MCCMNC 43404.
  - phoneId=1 repeatedly sees real Beeline GSM/WCDMA cells but remains
    OUT_OF_SERVICE / emergency-only.
  - CarrierConfigLoader repeatedly restores the phoneId=1 cached XML whose
    filename ends in -1867.xml.
  - The cached config shown in the dump reports mccmnc=[43405], which is
    the SIM1 Ucell code, not SIM2 Beeline 43404.

This module is deliberately conservative:
  - removes only the phoneId=1 CarrierConfig cache entry ending in -1867.xml;
  - records the regenerated cache and phoneId=1 state;
  - does NOT change NV, EFS, IMEI, modem firmware, or radio persist properties;
  - does NOT force an arbitrary MCC/MNC into the modem.

Install:
  1. Build/install the ZIP produced by the repository workflow.
  2. Reboot.
  3. Wait 1-2 minutes after Android finishes booting.
  4. Check SIM2 registration.

If SIM2 is still emergency-only, collect:
  su -c 'cat /data/adb/rmx3830_sim2_carrierfix.log'
  su -c 'logcat -b radio -d -v threadtime | tail -n 500'
  su -c 'dumpsys telephony.registry'

Important:
The dump proves a persistent phoneId=1 registration problem and a suspicious
CarrierConfig cache mismatch, but it does not prove that CarrierConfig is the
only root cause. This module therefore avoids unsafe modem/NV changes.
