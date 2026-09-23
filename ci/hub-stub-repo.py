# Stand-in for the Plugin Hub's upload server so ci/hub-package.sh can run the real
# packager without credentials. It accepts every write, keeping only build logs
# (paths under /log/) in the directory given as the first argument so a failure
# can be shown, and answers 404 to reads (no previous manifest, so a full build).
import http.server
import os
import sys

OUT = sys.argv[1]


class H(http.server.BaseHTTPRequestHandler):
    def _ok(self, code=200):
        n = int(self.headers.get('Content-Length') or 0)
        body = self.rfile.read(n) if n else b''
        if self.command == 'PUT' and self.path.startswith('/log/'):
            name = os.path.basename(self.path)
            with open(os.path.join(OUT, name), 'wb') as f:
                f.write(body)
        self.send_response(code)
        self.send_header('Content-Length', '0')
        self.end_headers()

    def do_GET(self): self._ok(404)
    def do_HEAD(self): self._ok(404)
    def do_PUT(self): self._ok(201)
    def do_MKCOL(self): self._ok(201)
    def do_POST(self): self._ok(200)
    def do_DELETE(self): self._ok(204)
    def log_message(self, *a): pass


http.server.HTTPServer(('127.0.0.1', 18777), H).serve_forever()
