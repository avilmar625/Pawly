"""Verify Debug and Release can use a Personal Team profile without APNs."""
import json
import plistlib
import sys
from pathlib import Path

config = sys.argv[1]
target = next(item for item in json.load(sys.stdin) if item["target"] == "Pawly")
settings = target["buildSettings"]
assert settings["CONFIGURATION"] == config, config
assert settings["PRODUCT_BUNDLE_IDENTIFIER"] == "com.alexvilarrubla.pawly"
source = settings.get("CODE_SIGN_ENTITLEMENTS")
if source:
    with (Path(settings["SRCROOT"]) / source).open("rb") as file:
        assert "aps-environment" not in plistlib.load(file), "APNs requires a different provisioning profile"
print(f"{config} device target preserves the Pawly bundle identifier and does not request APNs.")
