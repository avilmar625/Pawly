"""Verify localization resources and format arguments without sending translations to a service."""
from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1] / "Pawly"
languages = ["ca", "de", "en", "es", "eu", "fr", "pt"]
pair = re.compile(r'("(?:[^"\\]|\\.)*")\s*=\s*("(?:[^"\\]|\\.)*");')
argument = re.compile(r"%(?:\d+\$)?(?:ll|l)?[@dfiug]")
catalogs = {}
for language in languages:
    entries = {}
    for line in (root / f"{language}.lproj" / "Localizable.strings").read_text().splitlines():
        if not line.strip():
            continue
        key, value = map(json.loads, pair.fullmatch(line).groups())
        assert key not in entries and value.strip(), (language, key)
        assert sorted(argument.findall(key)) == sorted(argument.findall(value)), (language, key)
        entries[key] = value
    catalogs[language] = entries

for language, entries in catalogs.items():
    assert entries.keys() == catalogs["es"].keys(), language
print(f"{len(catalogs['es'])} catalog keys validated in {len(languages)} languages")

# User-entered values and interpolated messages are deliberately outside this static check.
literal = re.compile(r'(?:Text|Button|Label|Section|Picker|TextField|navigationTitle|accessibilityLabel|LabeledContent|ProgressView|Toggle|DatePicker|alert)\(\s*"([^"\\\n]{2,})"')
used = {key for source in root.glob("*.swift") for key in literal.findall(source.read_text())}
missing = sorted(used - catalogs["es"].keys())
assert not missing, f"Visible literal keys missing from the catalogs: {missing}"
print(f"{len(used)} direct UI literals covered")
