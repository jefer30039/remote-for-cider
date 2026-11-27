"""Añade traducciones al español a Localizable.xcstrings: addstrings.py ARCHIVO '{"English": "Español", ...}'."""
import json, sys
path, extra = sys.argv[1], json.loads(sys.argv[2])
d = json.load(open(path))
for en, es in extra.items():
    entry = d["strings"].setdefault(en, {})
    entry.setdefault("localizations", {})["es"] = {"stringUnit": {"state": "translated", "value": es}}
json.dump(d, open(path, "w"), ensure_ascii=False, indent=2, sort_keys=True)
print(len(d["strings"]))
