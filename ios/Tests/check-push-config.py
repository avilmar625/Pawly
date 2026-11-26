"""Verify the device target resolves an APNs entitlement for the requested configuration."""
import json
import plistlib
import sys
from pathlib import Path

config = sys.argv[1]
expected = {"Debug": "development", "Release": "production"}[config]
target = next(item for item in json.load(sys.stdin) if item["target"] == "Pawly")
settings = target["buildSettings"]
with (Path(settings["SRCROOT"]) / settings["CODE_SIGN_ENTITLEMENTS"]).open("rb") as source:
    entitlement = plistlib.load(source).get("aps-environment")
assert entitlement == "$(APS_ENVIRONMENT)" and settings.get("APS_ENVIRONMENT") == expected, config
print(f"{config} device target requests the {expected} APNs entitlement.")
