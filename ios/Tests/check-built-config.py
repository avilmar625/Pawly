"""Check the processed app configuration without printing values or credentials."""
import plistlib
import sys
from pathlib import Path
from urllib.parse import urlparse

bundle = Path(sys.argv[1])
with (bundle / "Info.plist").open("rb") as info:
    config = plistlib.load(info)
required = ["GIDClientID", "PAWLY_BACKEND_URL", "CLOUDINARY_CLOUD_NAME",
            "CLOUDINARY_IMAGE_UPLOAD_PRESET", "CLOUDINARY_VIDEO_UPLOAD_PRESET",
            "NSCameraUsageDescription", "NSMicrophoneUsageDescription",
            "NSPhotoLibraryUsageDescription", "NSLocationWhenInUseUsageDescription"]
errors = [key for key in required if not isinstance(config.get(key), str)
          or not config[key].strip() or "$(" in config[key]]
if config.get("FirebaseMessagingAutoInitEnabled") is not False:
    errors.append("Firebase Messaging automatic registration must be off for Personal Team")
if "remote-notification" in config.get("UIBackgroundModes", []):
    errors.append("unexpected remote-notification background mode")
url = urlparse(config.get("PAWLY_BACKEND_URL", ""))
if url.scheme != "https" or not url.hostname:
    errors.append("PAWLY_BACKEND_URL (HTTPS)")
schemes = [value for item in config.get("CFBundleURLTypes", []) for value in item.get("CFBundleURLSchemes", [])]
if not any(value and "$(" not in value for value in schemes):
    errors.append("GOOGLE_REVERSED_CLIENT_ID")
for key in ("CFBundleIcons", "CFBundleIcons~ipad"):
    primary = config.get(key, {}).get("CFBundlePrimaryIcon", {})
    if primary.get("CFBundleIconName") != "AppIcon" or not primary.get("CFBundleIconFiles"):
        errors.append(key + " (AppIcon)")
if not (bundle / "Assets.car").is_file():
    errors.append("compiled asset catalog")
for language in ["ca", "de", "en", "es", "eu", "fr", "pt"]:
    folder = bundle / f"{language}.lproj"
    if not (folder / "Localizable.strings").is_file() or not (folder / "InfoPlist.strings").is_file():
        errors.append(language)
if errors:
    raise SystemExit("Missing or invalid app settings: " + ", ".join(errors))
print("Processed Google, backend, Cloudinary, permission and localization settings passed.")
