#!/system/bin/sh
# Read-only: save only our own logcat tag in a persistent Magisk data directory.
LOGDIR=/data/adb/rmx3830_hotspot/logs
mkdir -p "$LOGDIR" 2>/dev/null || exit 0
chmod 700 "$LOGDIR" 2>/dev/null
if [ -f "$LOGDIR/hotspot.log" ]; then
    mv -f "$LOGDIR/hotspot.log" "$LOGDIR/hotspot.previous.log" 2>/dev/null
fi
(
    logcat -v threadtime -T 1 -s RMX3830Hotspot:I '*:S' \
        >> "$LOGDIR/hotspot.log" 2>/dev/null
) &
exit 0
