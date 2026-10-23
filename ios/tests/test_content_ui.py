#!/usr/bin/env python3
"""Run native screens against isolated HTTP fixtures; never mutates the real server.

Usage: python3 ios/tests/test_content_ui.py --device <booted simulator UDID>
Builds with a fixture API URL; rebuild normally afterward to restore the real URL.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import threading
from urllib.parse import urlsplit, parse_qs

state = {"nickname": "연결 테스트", "onboardingCompleted": False, "userId": "fixture", "profile": None}
card = {"id": 9001, "title": "API 연결 검증 카드", "keyPoints": ["서버에서 받은 핵심 내용"],
        "publicationDate": "2026-10-07", "categories": [{"id": 1, "name": "기술"}], "liked": False, "read": False}
notice = {"id": 1, "title": "API 공지", "content": "서버에서 받은 공지 본문", "publishedAt": "2026-10-07T00:00:00Z"}
profiles = [{"id": 1, "name": "테스트 프로필", "imgUrl": ""}]
settings = {"settingTime": "09:00:00", "isAllowed": False}
checks = set()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass

    def handle_request(self):
        url = urlsplit(self.path)
        path, query = url.path, parse_qs(url.query)
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        method = self.command
        code, value = 200, None
        if method not in ("GET", "HEAD") and self.headers.get("X-CSRF-TOKEN") != "fixture-csrf":
            code = 403
        elif path == "/__fixture": value = {"fixture": True}
        elif path == "/api/v1/auth/csrf": value = {"headerName": "X-CSRF-TOKEN", "token": "fixture-csrf"}
        elif path == "/api/v1/users": value = state
        elif path == "/api/v1/users/onboarding" and method == "PATCH":
            state["onboardingCompleted"] = True; value = state; checks.add("onboarding")
        elif path == "/api/v1/users/nickname" and method == "PATCH":
            state["nickname"] = body["nickname"]; checks.add("nickname")
        elif path == "/api/v1/users/profile/1" and method == "PATCH":
            state["profile"] = profiles[0]; checks.add("profile")
        elif path == "/api/v1/profile": value = profiles
        elif path in ("/api/v1/cardNews/today", "/api/v1/cardNews/9001"):
            value = card
            if path.endswith("/9001"): checks.add("card-open")
        elif path == "/api/v1/cardNews/categories": value = card["categories"]
        elif path == "/api/v1/cardNews":
            value = [card]
            if query.get("query"):
                assert query["query"] == ["API"]
                checks.add("search")
            if query.get("categoryId") == ["1"]: checks.add("category")
            if query.get("isOnlyLiked") == ["true"]:
                value = [card] if card["liked"] else []; checks.add("liked-filter")
        elif path == "/api/v1/cardNews/cardSlides":
            assert query.get("cardNewsId") == ["9001"]
            value = [{"id": 1, "title": "서버 슬라이드", "content": "API에서 받은 카드 본문", "sequence": 1}]
            checks.add("slides")
        elif path == "/api/v1/cardNews/9001/like" and method == "PATCH":
            card["liked"] = body["liked"]; value = {"liked": card["liked"]}; checks.add("like")
        elif path == "/api/v1/cardNews/9001/read" and method == "PUT":
            card["read"] = True; code = 204; checks.add("read")
        elif path == "/api/v1/notices": value = {"content": [notice], "last": True}
        elif path == "/api/v1/notices/1": value = notice; checks.add("notice")
        elif path == "/api/v1/notification-setting":
            if method == "PUT": settings.update(body); checks.add("settings")
            value = settings
        elif path == "/api/v1/device-tokens": code = 204
        else: code = 404
        data = json.dumps(value).encode() if value is not None else b""
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers(); self.wfile.write(data)

    do_GET = do_PATCH = do_PUT = do_POST = do_DELETE = handle_request


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    server = ThreadingHTTPServer(("127.0.0.1", 18081), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with open("/tmp/aiknow-content-ui.log", "w") as log:
            result = subprocess.run(["xcodebuild", "-project", "ios/AiKnow/AiKnow.xcodeproj", "-scheme", "AiKnowUITests",
                "-configuration", "Debug", "-destination", f"platform=iOS Simulator,id={args.device}", "-jobs", "2",
                "-only-testing:AiKnowUITests/ContentConnectionTests", "AIKNOW_API_BASE_URL=http://localhost:18081",
                "ONLY_ACTIVE_ARCH=YES", "test"], cwd=root, stdout=log, stderr=subprocess.STDOUT)
        expected = {"card-open", "onboarding", "search", "category", "liked-filter", "slides", "like", "read", "notice", "nickname", "profile", "settings"}
        print("UI test exit:", result.returncode, "Verified:", ", ".join(sorted(checks)), "Missing:", ", ".join(sorted(expected - checks)))
        assert result.returncode == 0 and checks >= expected
    finally:
        server.shutdown()
