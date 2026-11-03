#!/usr/bin/env python3
"""Run first-member setup against an isolated HTTP fixture, without real OAuth."""
import argparse
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import threading
from urllib.parse import urlsplit
import test_content_ui as content

writes = []
class Handler(content.Handler):
    def handle_request(self):
        path = urlsplit(self.path).path
        if path == '/__fixture':
            data = json.dumps({'onboardingSetup': True}).encode()
            self.send_response(200); self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data); return
        if self.command == 'PATCH' and path == '/api/v1/users/nickname':
            assert self.headers.get('X-CSRF-TOKEN') == 'fixture-csrf'
            body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
            assert body['nickname'] == '새 회원'
            content.state['nickname'] = body['nickname']; writes.append('nickname')
            self.send_response(204); self.end_headers(); return
        if self.command == 'PUT' and path == '/api/v1/notification-setting': writes.append('notification')
        if self.command == 'PATCH' and path == '/api/v1/users/onboarding':
            assert content.state['nickname'] == '새 회원'
            assert content.settings['settingTime'] == '18:20:00'
            writes.append('onboarding')
        return super().handle_request()
    do_GET = do_POST = do_PATCH = do_PUT = do_DELETE = handle_request

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--device', required=True); args = parser.parse_args()
    content.settings.update(settingTime='18:20:00', isAllowed=False)
    server = ThreadingHTTPServer(('127.0.0.1', 18081), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with open('/tmp/aiknow-onboarding-ui.log', 'w') as log:
            result = subprocess.run(['xcodebuild','-project','ios/AiKnow/AiKnow.xcodeproj','-scheme','AiKnowUITests',
                '-configuration','Debug','-destination',f'platform=iOS Simulator,id={args.device}','-jobs','2',
                '-only-testing:AiKnowUITests/OnboardingSetupTests','AIKNOW_API_BASE_URL=http://localhost:18081',
                'ONLY_ACTIVE_ARCH=YES','test'],cwd=Path(__file__).resolve().parents[2],stdout=log,stderr=subprocess.STDOUT)
        assert result.returncode == 0 and content.state['onboardingCompleted']
        assert writes[0]=='nickname' and writes[-1]=='onboarding' and writes.count('nickname')==writes.count('onboarding')==1, writes
        assert content.settings['settingTime']=='18:20:00'
        print('PASS first-member nickname, OS permission handling, Korean reception time, completion ordering; notifications allowed:',content.settings['isAllowed'])
    finally: server.shutdown()
