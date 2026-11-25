"""Select an available iPhone from the newest installed iOS simulator runtime."""
import json
import subprocess

inventory = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "-j"]))
choices = []
for runtime, devices in inventory["devices"].items():
    if ".iOS-" not in runtime:
        continue
    version = tuple(int(n) for n in runtime.split(".iOS-")[-1].split("-"))
    for device in devices:
        if device["isAvailable"] and device["name"].startswith("iPhone"):
            choices.append((version, device["name"], device["udid"]))
if not choices:
    raise SystemExit("No available iPhone simulator")
print(max(choices)[2])
