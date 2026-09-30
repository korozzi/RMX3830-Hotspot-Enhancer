from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
OUT = ROOT / "dist/RMX3830-Hotspot-Enhancer-v1.0.0-Magisk.zip"
if not APK.exists():
    raise SystemExit("APK missing")
with ZipFile(APK) as z:
    required = {"META-INF/xposed/java_init.list", "META-INF/xposed/module.prop", "META-INF/xposed/scope.list", "classes.dex"}
    missing = required - set(z.namelist())
    if missing:
        raise SystemExit("APK missing: " + ",".join(sorted(missing)))
items = {
    "module.prop": ROOT / "magisk/module.prop",
    "uninstall.sh": ROOT / "magisk/uninstall.sh",
    "customize.sh": ROOT / "magisk/customize.sh",
    "system/app/RMX3830HotspotEnhancer/base.apk": APK,
}
OUT.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(OUT, "w", ZIP_DEFLATED) as z:
    for name, source in items.items():
        info = ZipInfo(name)
        info.compress_type = ZIP_DEFLATED
        info.external_attr = (0o755 if name.endswith(".sh") else 0o644) << 16
        z.writestr(info, source.read_bytes())
print(OUT)
