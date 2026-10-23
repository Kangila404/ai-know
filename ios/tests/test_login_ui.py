#!/usr/bin/env python3
"""Check official login controls in both appearances with isolated provider responses.

Usage: python3 ios/tests/test_login_ui.py --device <simulator UDID>
No provider credentials are entered. Rebuild normally to restore the production URL.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import threading

challenges = set()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass

    def do_GET(self):
        if self.path == "/__fixture": self.reply(200, {"loginFixture": True})
        elif self.path == "/api/v1/auth/social/providers": self.reply(200, ["GOOGLE", "KAKAO", "APPLE"])
        elif self.path == "/api/v1/auth/csrf": self.reply(200, {"headerName": "X-CSRF-TOKEN", "token": "branding-csrf"})
        else: self.reply(401, None)

    def do_POST(self):
        if self.headers.get("X-CSRF-TOKEN") != "branding-csrf":
            self.reply(403, None)
            return
        challenges.add(self.path)
        # Stop before invoking the OAuth UI; assert that each button reached its server flow.
        self.reply(503, None)

    def reply(self, status, value):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", 18083), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with open("/tmp/aiknow-login-ui.log", "w") as log:
            result = subprocess.run([
                "xcodebuild", "-project", "ios/AiKnow/AiKnow.xcodeproj", "-scheme", "AiKnowUITests",
                "-configuration", "Debug", "-destination", f"platform=iOS Simulator,id={args.device}", "-jobs", "2",
                "-only-testing:AiKnowUITests/LoginBrandingTests", "AIKNOW_API_BASE_URL=http://localhost:18083",
                "ONLY_ACTIVE_ARCH=YES", "test"
            ], cwd=Path(__file__).resolve().parents[2], stdout=log, stderr=subprocess.STDOUT)
        expected = {f"/api/v1/auth/social/{provider}/challenge" for provider in ["GOOGLE", "KAKAO", "APPLE"]}
        print("UI exit:", result.returncode, "Button routes:", sorted(challenges), "Missing:", sorted(expected - challenges))
        assert result.returncode == 0 and challenges == expected
    finally:
        server.shutdown()
