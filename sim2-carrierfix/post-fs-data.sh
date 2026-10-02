#!/system/bin/sh

LOG=/data/adb/rmx3830_sim2_carrierfix.log
exec >>"$LOG" 2>&1
echo "=== RMX3830 SIM2 CarrierConfig Fix 1.1.0: post-fs-data $(date) ==="

# The dump showed CarrierConfigLoader repeatedly restoring the phoneId=1
# cache entry ending in -1867.xml. Remove only that cache entry from the
# Android phone/telephony data locations. Do not touch modem NV/EFS/IMEI.
for base in \
  /data/user_de/0/com.android.phone \
  /data/data/com.android.phone \
  /data/user_de/0/com.android.providers.telephony \
  /data/data/com.android.providers.telephony
do
  [ -d "$base" ] || continue
  find "$base" -type f -name 'carrierconfig-com.android.carrierconfig-*-1867.xml' -print -delete
done

echo "post-fs-data complete"
