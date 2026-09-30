#!/system/bin/sh
MODDIR=/data/adb/modules/rmx3830_hotspot_diagnostic
PROP=$MODDIR/module.prop
LOGDIR=/data/adb/rmx3830_hotspot/logs
mkdir -p "$LOGDIR" 2>/dev/null
chmod 700 "$LOGDIR" 2>/dev/null
if [ -f "$LOGDIR/hotspot.log" ]; then
    mv -f "$LOGDIR/hotspot.log" "$LOGDIR/hotspot.previous.log" 2>/dev/null
fi
(
    logcat -v threadtime -T 1 -s RMX3830Hotspot:I '*:S' >> "$LOGDIR/hotspot.log" 2>/dev/null
) &
(
    ok=0
    i=0
    while [ "$i" -lt 20 ]; do
        data="$(logcat -d -s RMX3830Hotspot:I '*:S' 2>/dev/null)"
        if echo "$data" | grep -q "MODULE_LOADED" && echo "$data" | grep -q "STANDALONE_PERMISSION_HOOK_READY"; then
            ok=1
            break
        fi
        sleep 3
        i=$((i + 1))
    done
    if [ "$ok" -eq 1 ]; then
        sed -i 's/^description=.*/description=Статус: работает/' "$PROP" 2>/dev/null
    else
        sed -i 's/^description=.*/description=Статус: не работает/' "$PROP" 2>/dev/null
    fi
) &
exit 0
