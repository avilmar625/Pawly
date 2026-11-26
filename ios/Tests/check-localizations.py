"""Verify localization resources and format arguments without sending translations to a service."""
from pathlib import Path
import argparse
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

# Choices persist their original values in Firestore. Every literal option also needs a display translation.
options = re.compile(r'ForEach\(\s*\[((?:\s*"[^"\\\n]+"\s*,?)+)\]')
choices = {option for source in root.glob("*.swift") for group in options.findall(source.read_text())
           for option in re.findall(r'"([^"]+)"', group)}
missing_choices = sorted(choices - catalogs["es"].keys())
assert not missing_choices, f"Missing picker or menu translations: {missing_choices}"
print(f"{len(choices)} inline menu and picker choices covered")


parser = argparse.ArgumentParser()
parser.add_argument("--compiled", type=Path, help="Xcode Objects-normal directory for the Pawly target")
compiled = parser.parse_args().compiled
if compiled:
    tables = [json.loads(file.read_text()).get("tables", {}) for file in compiled.rglob("*.stringsdata")]
    extracted = {item["key"] for table in tables for item in table.get("Localizable", [])}
    assert extracted, f"No SwiftUI localization keys extracted from {compiled}"
    missing = sorted(extracted - catalogs["es"].keys())
    assert not missing, f"Missing translations for compiled SwiftUI keys: {missing}"
    print(f"{len(extracted)} SwiftUI keys emitted by Xcode covered")
