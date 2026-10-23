#!/usr/bin/env python3
"""Run the app's APIClient inside a booted iOS Simulator against localhost:8080."""
import argparse
import os
from pathlib import Path
import platform
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--device", default="booted", help="Simulator UDID, or booted when only one is running")
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
sdk = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], text=True).strip()
with tempfile.TemporaryDirectory(prefix="aiknow-simulator-api-") as folder:
    binary = str(Path(folder) / "api-probe")
    subprocess.run(["xcrun", "--sdk", "iphonesimulator", "swiftc", "-parse-as-library", "-sdk", sdk,
                    "-target", platform.machine() + "-apple-ios17.0-simulator",
                    str(root / "ios/AiKnow/AiKnow/Services/APIClient.swift"),
                    str(root / "ios/tests/SimulatorAPIProbe.swift"), "-o", binary],
                   env={**os.environ, "SDKROOT": sdk}, check=True)
    subprocess.run(["xcrun", "simctl", "spawn", args.device, binary], check=True)
