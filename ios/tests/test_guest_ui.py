#!/usr/bin/env python3
"""Mac: python3 ios/tests/test_guest_ui.py --device <booted simulator UDID>.

Isolated anonymous API fixture. Fails if browsing calls any personal API or mutation.
No production authentication bypass is added to the app.
"""
import argparse
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import tempfile
import threading
from urllib.parse import urlsplit, parse_qs
from test_content_ui import Handler, checks

requests = []
violations = []
PUBLIC = {"/api/v1/cardNews", "/api/v1/cardNews/today", "/api/v1/cardNews/9001",
          "/api/v1/cardNews/categories", "/api/v1/cardNews/cardSlides",
          "/api/v1/notices", "/api/v1/notices/1", "/avatars/lumi-v1.png"}


class GuestHandler(Handler):
    def reply(self, code, value):
        data = json.dumps(value).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def handle_request(self):
        url = urlsplit(self.path)
        path = url.path
        requests.append((self.command, path))
        if path == "/__fixture": return self.reply(200, {"guest": True})
        if path == "/api/v1/users" and self.command == "GET": return self.reply(401, {})
        if path == "/api/v1/auth/social/providers": return self.reply(200, [])
        if self.command != "GET" or path not in PUBLIC or parse_qs(url.query).get("isOnlyLiked") == ["true"]:
            violations.append((self.command, self.path))
            return self.reply(401, {})
        return super().handle_request()

    do_GET = do_POST = do_PATCH = do_PUT = do_DELETE = handle_request


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    server = ThreadingHTTPServer(("127.0.0.1", 18081), GuestHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        log_path = Path(tempfile.gettempdir()) / "aiknow-guest-ui.log"
        with log_path.open("w") as log:
            result = subprocess.run(["xcodebuild", "-project", "ios/AiKnow/AiKnow.xcodeproj",
                "-scheme", "AiKnowUITests", "-configuration", "Debug", "-destination",
                f"platform=iOS Simulator,id={args.device}", "-jobs", "2",
                "-only-testing:AiKnowUITests/GuestConnectionTests", "AIKNOW_API_BASE_URL=http://localhost:18081",
                "ONLY_ACTIVE_ARCH=YES", "test"], cwd=root, stdout=log, stderr=subprocess.STDOUT)
        assert result.returncode == 0, f"See {log_path}"
        assert not violations, violations
        assert sum(path == "/api/v1/users" for _, path in requests) == 1, requests
        assert checks >= {"card-open", "slides", "notice"}, checks
        print("Guest public reads and zero personal requests verified.")
    finally:
        server.shutdown()
