#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
OUT="$ROOT/../dist"
rm -rf "$OUT"
mkdir -p "$OUT"
cp -a "$ROOT/." "$OUT/rmx3830_sim2_carrierfix/"
rm -f "$OUT/rmx3830_sim2_carrierfix/build.sh"
(
  cd "$OUT/rmx3830_sim2_carrierfix"
  zip -9 -r "$OUT/RMX3830_SIM2_CarrierFix_v1.1.0.zip" . >/dev/null
)
echo "$OUT/RMX3830_SIM2_CarrierFix_v1.1.0.zip"
