#!/usr/bin/env python3
"""Empaqueta cada carpeta del puente en un ZIP para subirlo a la release (así GitHub cuenta sus descargas).

Uso: python3 bridge/package.py <carpeta de salida>
Luego: gh release upload vX.Y.Z <salida>/remote-for-cider-bridge-*.zip
"""
import os
import sys
import zipfile

here = os.path.dirname(os.path.abspath(__file__))
out_dir = sys.argv[1] if len(sys.argv) > 1 else "."
os.makedirs(out_dir, exist_ok=True)
for system in ("windows", "linux", "macos"):
    name = f"remote-for-cider-bridge-{system}"
    path = os.path.join(out_dir, name + ".zip")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        folder = os.path.join(here, system)
        for f in sorted(os.listdir(folder)):
            info = zipfile.ZipInfo(f"{name}/{f}")
            info.compress_type = zipfile.ZIP_DEFLATED
            executable = f.endswith((".sh", ".py"))
            info.external_attr = (0o755 if executable else 0o644) << 16
            with open(os.path.join(folder, f), "rb") as src:
                z.writestr(info, src.read())
    print(path)
