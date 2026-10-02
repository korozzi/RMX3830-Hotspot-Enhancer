#!/system/bin/sh

LOG=/data/adb/rmx3830_sim2_carrierfix.log
exec >>"$LOG" 2>&1

until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 2
done

sleep 15

echo "=== RMX3830 SIM2 CarrierConfig Fix 1.1.0: service $(date) ==="
echo "--- SIM/operator ---"
getprop gsm.sim.state
getprop gsm.operator.alpha
getprop gsm.operator.numeric
getprop gsm.operator.iso-country
getprop gsm.network.type

echo "--- radio/carrier properties ---"
getprop persist.vendor.radio.phone_count
getprop persist.vendor.radio.primarysim
getprop persist.vendor.radio.nitz.info.sim1
getprop persist.vendor.radio.nitz.info.sim2
getprop persist.radio.multisim.config

echo "--- carrier config cache ---"
for base in \
  /data/user_de/0/com.android.phone \
  /data/data/com.android.phone \
  /data/user_de/0/com.android.providers.telephony \
  /data/data/com.android.providers.telephony
do
  [ -d "$base" ] || continue
  find "$base" -type f -name 'carrierconfig-com.android.carrierconfig-*.xml' -print
done

echo "--- phoneId=1 state ---"
dumpsys telephony.registry 2>/dev/null | grep -E 'phoneId=1|subId=1|Beeline UZ|43404|OUT_OF_SERVICE|NOT_REG_OR_SEARCHING' | tail -n 120

echo "service complete"
