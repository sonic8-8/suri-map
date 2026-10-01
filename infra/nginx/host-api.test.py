#!/usr/bin/env python3
"""Verify the Hetzner API snippet with disposable Nginx/Python containers."""
import http.client
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def serve():
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'

        def do_GET(self):
            if self.path.split('?')[0] in ('/api/incidents/events', '/api/incidents/test/events'):
                if self.headers.get('Authorization') != 'Bearer test-only':
                    body = b'{"error":"test_unavailable"}'
                    self.send_response(503)
                    self.send_header('Content-Length', str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                    return
                self.send_response(200)
                self.send_header('Content-Type', 'text/event-stream')
                self.send_header('X-Accel-Buffering', 'no')
                self.end_headers()
                self.wfile.write(b':connected\n\n')
                self.wfile.flush()
                time.sleep(2)
                self.wfile.write(b'id: 42\ndata: alive\n\n')
                self.wfile.flush()
                self.close_connection = True
                return
            body = json.dumps({'path': self.path, 'headers': dict(self.headers)}).encode()
            self.send_response(200)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    ThreadingHTTPServer(('0.0.0.0', 18081), Handler).serve_forever()


def check():
    headers = {'Host': 'suri-map.sonic8-8.com', 'Authorization': 'Bearer test-only',
               'X-Client-Channel': 'WEB', 'Last-Event-ID': '41', 'Idempotency-Key': 'test-only'}
    for route in ('/api', '/api/echo?value=1', '/api/incidents/events/extra'):
        connection = http.client.HTTPConnection('127.0.0.1', 8080, timeout=4)
        connection.request('GET', route, headers=headers)
        response = connection.getresponse()
        data = json.loads(response.read())
        assert response.status == 200 and data['path'] == route
        assert data['headers']['Last-Event-ID'] == '41'
        assert data['headers']['X-Forwarded-Port'] == '443'
        connection.close()
    for route in ('/api/incidents/events', '/api/incidents/test/events?from=41'):
        connection = http.client.HTTPConnection('127.0.0.1', 8080, timeout=4)
        connection.request('GET', route)
        response = connection.getresponse()
        assert response.status == 503 and json.loads(response.read())['error'] == 'test_unavailable'
        connection.close()
        connection = http.client.HTTPConnection('127.0.0.1', 8080, timeout=4)
        start = time.monotonic()
        connection.request('GET', route, headers=headers)
        response = connection.getresponse()
        assert response.status == 200 and response.getheader('Content-Type') == 'text/event-stream'
        assert response.readline() + response.readline() == b':connected\n\n'
        assert time.monotonic()-start < 1.5, 'SSE must arrive before upstream completion'
        assert response.read() == b'id: 42\ndata: alive\n\n', 'SSE closed during idle interval'
        connection.close()
    for route in ('/login', '/api-other'):
        connection = http.client.HTTPConnection('127.0.0.1', 8080, timeout=4)
        connection.request('GET', route)
        response = connection.getresponse()
        assert response.status == 200 and response.read() == b'website'
        connection.close()
    print('PASS: API forwarding/errors, account/incident SSE immediate delivery and idle survival, non-API routes')


def run():
    def docker(*args):
        return subprocess.check_output(['docker', *args], text=True, timeout=30).strip()

    containers = []
    with tempfile.TemporaryDirectory(prefix='suri-map-host-api-') as directory:
        config = Path(directory)/'nginx.conf'
        snippet = Path(__file__).with_name('suri-map-api.locations.conf').read_text()
        # Shorten only the inherited timeout; the production snippet remains unchanged.
        config.write_text('events {}\nhttp { proxy_read_timeout 1s; server { listen 8080;\n'
                          + snippet + '\nlocation / { return 200 "website"; }\n}}\n')
        try:
            upstream = docker('create', '-v', f'{Path(__file__).resolve()}:/test.py:ro',
                              'python:3.12-alpine', 'python', '/test.py', '--serve')
            containers.append(upstream)
            docker('start', upstream)
            proxy = docker('create', '--network', f'container:{upstream}', '-v',
                           f'{config}:/etc/nginx/nginx.conf:ro', 'nginx:1.27.5-alpine')
            containers.append(proxy)
            docker('start', proxy)
            docker('exec', proxy, 'nginx', '-t')
            time.sleep(1)
            print(docker('exec', upstream, 'python', '/test.py', '--check'))
        finally:
            for container in reversed(containers):
                docker('rm', '-f', container)


if __name__ == '__main__':
    if '--serve' in sys.argv:
        serve()
    elif '--check' in sys.argv:
        check()
    else:
        run()
