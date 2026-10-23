#!/usr/bin/env python3
"""Exercise the actual Swift API client against a local HTTP fixture (no real OAuth tokens)."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading

state = {"csrf": "anonymous-csrf", "mutation_attempts": 0, "logged_out": False, "checks": set()}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def respond(self, status, body=None, cookie=None):
        data = json.dumps(body).encode() if body is not None else b""
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        if cookie:
            self.send_header("Set-Cookie", cookie + "; Path=/; HttpOnly; SameSite=Lax")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/api/v1/auth/social/providers":
            self.respond(200, ["GOOGLE", "APPLE", "FUTURE_PROVIDER"])
        elif self.path == "/api/v1/auth/csrf":
            self.respond(200, {"headerName": "X-CSRF-TOKEN", "token": state["csrf"]},
                         "JSESSIONID=anonymous-session" if state["csrf"] == "anonymous-csrf" else None)
        elif self.path == "/api/v1/users":
            if self.headers.get("Cookie") != "JSESSIONID=authenticated-session" or state["logged_out"]:
                self.respond(401)
            else:
                state["checks"].add("rotated_cookie")
                self.respond(200, {"nickname": "테스트", "onboardingCompleted": False, "userRole": "ADMIN"})
        else:
            self.respond(404)

    def do_PATCH(self):
        self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if self.headers.get("X-CSRF-TOKEN") != state["csrf"]:
            self.respond(403)
        elif self.path == "/api/v1/users/nickname":
            self.respond(400, {"status": 400, "code": "NICKNAME_REQUIRED", "message": "닉네임은 필수입니다."})
        elif self.path == "/api/v1/users/onboarding":
            state["business_attempts"] += 1
            self.respond(403, {"status": 403, "code": "INCOMPLETE_ONBOARDING", "message": "온보딩을 완료하지 않은 회원입니다."})
        else:
            self.respond(404)

    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if self.path == "/api/v1/admin/generations/1/recover-image":
            valid = (self.headers.get("Content-Type") == "multipart/form-data; boundary=fixture-boundary"
                     and self.headers.get("X-CSRF-TOKEN") == state["csrf"]
                     and self.headers.get("Cookie") == "JSESSIONID=authenticated-session"
                     and b'filename="recovery.png"' in raw and b"Content-Type: image/png" in raw)
            assert valid, "Multipart content type, payload, session and CSRF must be preserved"
            state["multipart_checked"] = True
            self.respond(200, {})
            return
        body = json.loads(raw or b"{}")
        if self.path == f"/api/v1/auth/social/{state['provider']}/challenge":
            valid = self.headers.get("X-CSRF-TOKEN") == "anonymous-csrf" and self.headers.get("Cookie") == "JSESSIONID=anonymous-session"
            if valid:
                state["checks"].add("challenge_csrf_cookie")
                self.respond(200, {"nonce": "server-generated-nonce"})
            else:
                self.respond(403)
        elif self.path == f"/api/v1/auth/social/{state['provider']}":
            valid = self.headers.get("X-CSRF-TOKEN") == "anonymous-csrf" and self.headers.get("Cookie") == "JSESSIONID=anonymous-session" and body.get("idToken") == "synthetic-test-token"
            if valid:
                state["checks"].add("id_token_exchange")
                state["csrf"] = "authenticated-csrf"
                self.respond(200, {"userId": "1", "csrfHeaderName": "X-CSRF-TOKEN", "csrfToken": state["csrf"]}, "JSESSIONID=authenticated-session")
            else:
                self.respond(401)
        elif self.path == "/api/v1/auth/logout":
            if self.headers.get("X-CSRF-TOKEN") == state["csrf"] and self.headers.get("Cookie") == "JSESSIONID=authenticated-session":
                state["logged_out"] = True
                state["checks"].add("logout")
                self.respond(204)
            else:
                self.respond(403)
        else:
            self.respond(404)

    def do_PUT(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        state["mutation_attempts"] += 1
        if self.headers.get("Cookie") != "JSESSIONID=authenticated-session" or self.headers.get("X-CSRF-TOKEN") != state["csrf"] or body.get("platform") != "IOS":
            self.respond(401)
        elif state["mutation_attempts"] == 1:
            state["checks"].add("rotated_csrf")
            state["csrf"] = "refreshed-csrf"
            self.respond(403)
        else:
            state["checks"].add("csrf_retry")
            self.respond(200, {})


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with tempfile.TemporaryDirectory(prefix="aiknow-api-test-") as folder:
            binary = str(Path(folder) / "api-test")
            subprocess.run(["xcrun", "swiftc", "-parse-as-library", str(root / "ios/AiKnow/AiKnow/Services/APIClient.swift"), str(root / "ios/tests/APIClientIntegration.swift"), "-o", binary], check=True)
            for provider in ["GOOGLE", "APPLE", "KAKAO"]:
                state.update(csrf="anonymous-csrf", mutation_attempts=0, business_attempts=0, multipart_checked=False, logged_out=False, checks=set(), provider=provider)
                subprocess.run([binary], env={**os.environ, "API_CLIENT_TEST_URL": f"http://127.0.0.1:{server.server_port}", "API_CLIENT_TEST_PROVIDER": provider}, check=True)
                assert len(state["checks"]) == 6 and state["mutation_attempts"] == 2
                assert state["multipart_checked"]
                assert state["business_attempts"] == 1, "Business-rule 403 must not be retried as CSRF"

    finally:
        server.shutdown()
