#!/usr/bin/env python3
"""Exercise admin UI over HTTP with version, role, cookie and CSRF validation.
No production accounts or records are used. Rebuild normally after this test.
"""
import argparse
from copy import deepcopy
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import threading
from urllib.parse import urlsplit, parse_qs
import test_content_ui as content

checks, failures = set(), []
draft = {"sourceTitle": "검증 원문", "sourceUrl": "https://example.com/admin-fixture", "publishedAt": "2026-10-23T00:00:00Z",
         "title": "운영 카드", "summary": "관리자 HTTP 연결 검증", "keyPoints": ["API 계약 검증"],
         "slides": [{"sequence": 1, "title": "검증 슬라이드", "content": "운영 카드 본문", "layout": "text"}]}
review = {"id": 1, "version": 1, "status": "PENDING", "draft": deepcopy(draft), "cardNewsId": None}
article = {"version": 7, "publicationStatus": "PUBLISHED", "news": {**content.card, "id": 9002,
           "title": draft["title"], "summary": draft["summary"], "sourceTitle": draft["sourceTitle"], "sourceUrl": draft["sourceUrl"],
           "sourcePublishedAt": draft["publishedAt"], "contentType": "NEWS"},
           "slides": [{"id": 1, **draft["slides"][0]}]}
records = {1: {"id": 1, "version": 1, "task": "draft", "sourceUrl": draft["sourceUrl"], "createdAt": "2026-10-23T00:00:00Z",
               "requestJson": '{"prompt":"fixture"}', "responseJson": '{"title":"검증 응답"}',
               "contextJson": '{"source":"저장된 이미지 선택 자료"}'}}
notices, audits = {}, []


class Handler(content.Handler):
    def send_header(self, name, value):
        super().send_header(name, value)
        if name == "Content-Type":
            super().send_header("Set-Cookie", "JSESSIONID=fixture-session; Path=/; HttpOnly")

    def handle_request(self):
        path = urlsplit(self.path).path
        query = parse_qs(urlsplit(self.path).query)
        if not path.startswith(("/api/v1/admin/", "/__role")):
            return super().handle_request()
        method = self.command
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        code, value = 200, {}
        try:
            if path == "/__role":
                content.state["userRole"] = query["role"][0]
                checks.add("role-" + query["role"][0]); value = {"updated": True}
            else:
                assert content.state["userRole"] == "ADMIN", "admin role required"
                if method != "GET":
                    assert self.headers.get("X-CSRF-TOKEN") == "fixture-csrf", "CSRF missing"
                    assert "JSESSIONID=fixture-session" in self.headers.get("Cookie", ""), "session missing"
                value = self.admin(path, query, method, body)
        except (AssertionError, KeyError, ValueError) as error:
            failures.append(f"{method} {path}: {error}")
            code, value = 400, {"status": 400, "code": "FIXTURE_CONTRACT", "message": str(error)}
        data = json.dumps(value).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)

    def admin(self, path, query, method, body):
        parts = path.split("/"); resource = parts[4]
        ident = int(parts[5]) if len(parts) > 5 and parts[5].isdigit() else None
        operation = parts[6] if len(parts) > 6 else None
        def page(values): return {"content": values, "last": True}
        def record(target, action):
            if action not in ["approve", "reject"]: assert body["version"] == target["version"], "stale version"
            if action != "approve": assert body.get("reason", "").strip(), "reason required"
            target["version"] += 1
            audits.append({"id": len(audits)+1, "action": action, "actorId": 1, "occurredAt": "2026-10-23T00:00:00Z", "reason": body.get("reason", "관리자 승인")})
        checks.add(f"{method} {path}")
        if resource == "news-submissions":
            if ident is None:
                return page([{"id": 1, "status": review["status"], "cardNewsId": review["cardNewsId"]}] if query.get("status", ["PENDING"])[0] == review["status"] else [])
            if method == "GET": return review
            record(review, operation or "edit")
            if method == "PUT":
                assert body["draft"]["sourceUrl"] == draft["sourceUrl"]
                review["draft"] = body["draft"]
            if operation == "reject":
                review.update(status="DENIED", reviewNote=body["reason"], reviewedBy=1, reviewedAt="2026-10-23T00:00:00Z")
            if operation == "reopen": review.update(status="PENDING", reviewNote=None)
            if operation == "approve":
                assert body["contentType"] in ["NEWS", "AI_THEORY"] and isinstance(body["categoryIds"], list)
                review.update(status="APPROVED", cardNewsId=9002, publicationStatus="PUBLISHED")
            return review
        if resource == "card-news":
            if ident is None: return page([article])
            if method != "GET":
                record(article, operation or "edit")
                if method == "PUT":
                    assert body["draft"]["sourceUrl"] == draft["sourceUrl"]
                    article["news"]["title"] = body["draft"]["title"]
                else: article["publicationStatus"] = "HIDDEN" if operation == "hide" else "PUBLISHED"
            return article
        if resource == "editorial":
            assert query["resourceType"][0] in ["SUBMISSION", "ARTICLE", "GENERATION"]
            assert int(query["resourceId"][0]) > 0
            return page(audits)
        if resource == "notices":
            if ident is None:
                if method == "GET": return page(list(notices.values()))
                ident = max(notices, default=0)+1
            if method == "DELETE": notices.pop(ident); return {}
            if method == "PUT": assert "수정" in body["title"]
            if method in ["POST", "PUT"]:
                assert body["title"] and body["content"] and isinstance(body["published"], bool)
                notices[ident] = {**body, "id": ident}
            return notices[ident]
        if resource == "profile":
            if method == "POST":
                ident = max((p["id"] for p in content.profiles), default=0)+1
                content.profiles.append({**body,"id":ident})
            elif method == "PATCH":
                assert "수정" in body["name"]
                index = next(i for i,p in enumerate(content.profiles) if p["id"] == ident)
                content.profiles[index] = {**body,"id":ident}
            else:
                content.profiles[:] = [p for p in content.profiles if p["id"] != ident]; return {}
            assert 0 < len(body["name"]) <= 20 and 0 < len(body["imgUrl"]) <= 20
            return {**body, "id": ident}
        if resource == "generations":
            if ident is None: return page([{**v,"responseSaved": True,"recovered": "recoveredResponseJson" in v} for v in records.values()])
            item = records[ident]
            if method != "GET":
                record(item, "recover")
                assert isinstance(json.loads(body["responseJson"]), dict)
                item["recoveredResponseJson"] = body["responseJson"]
            return item
        raise AssertionError("unexpected admin resource")

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = handle_request


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("--device", required=True)
    parser.add_argument("--only", action="append", choices=["testAReviewAndPublication", "testBNoticeAndProfileCRUD", "testCGenerationRecovery", "testDRoleChangesUpdateRightmostTab"])
    args = parser.parse_args()
    content.state.update(onboardingCompleted=True, userRole="ADMIN")
    root = Path(__file__).resolve().parents[2]
    server = ThreadingHTTPServer(("127.0.0.1", 18082), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with open('/tmp/aiknow-admin-http-ui.log','w') as log:
            result = subprocess.run(['xcodebuild','-project','ios/AiKnow/AiKnow.xcodeproj','-scheme','AiKnowUITests',
                '-configuration','Debug','-destination',f'platform=iOS Simulator,id={args.device}','-jobs','2',
                *([f'-only-testing:AiKnowUITests/AdminAPIConnectionTests/{name}' for name in args.only] if args.only else ['-only-testing:AiKnowUITests/AdminAPIConnectionTests']),
                'AIKNOW_API_BASE_URL=http://localhost:18082',
                'ONLY_ACTIVE_ARCH=YES','test'],cwd=root,stdout=log,stderr=subprocess.STDOUT)
        expected = {'role-USER','role-ADMIN','GET /api/v1/admin/news-submissions/1','PUT /api/v1/admin/news-submissions/1',
            'POST /api/v1/admin/news-submissions/1/reject','POST /api/v1/admin/news-submissions/1/reopen','POST /api/v1/admin/news-submissions/1/approve',
            'GET /api/v1/admin/card-news/9002','PUT /api/v1/admin/card-news/9002','POST /api/v1/admin/card-news/9002/hide','POST /api/v1/admin/card-news/9002/restore',
            'GET /api/v1/admin/editorial/history','POST /api/v1/admin/notices','PUT /api/v1/admin/notices/1','DELETE /api/v1/admin/notices/1',
            'POST /api/v1/admin/profile','PATCH /api/v1/admin/profile/2','DELETE /api/v1/admin/profile/2','POST /api/v1/admin/generations/1/recover'}
        if args.only:
            if 'testAReviewAndPublication' not in args.only: expected = {x for x in expected if not any(p in x for p in ['/news-submissions', '/card-news', '/editorial/history'])}
            if 'testBNoticeAndProfileCRUD' not in args.only: expected = {x for x in expected if not any(p in x for p in ['/admin/notices', '/admin/profile'])}
            if 'testCGenerationRecovery' not in args.only: expected.discard('POST /api/v1/admin/generations/1/recover')
            if 'testDRoleChangesUpdateRightmostTab' not in args.only: expected.discard('role-USER')
        print('UI exit:' ,result.returncode,'Verified:',len(checks),'Missing:',sorted(expected-checks),'Contract failures:',failures)
        assert result.returncode == 0 and expected <= checks and not failures
    finally: server.shutdown()
