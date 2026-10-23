#!/usr/bin/env python3
"""Exercise UIKit image decoding in a booted simulator with an isolated HTTP fixture."""
import argparse
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import platform
import random
import struct
import subprocess
import tempfile
import threading
import zlib
from urllib.parse import urlsplit


def png():
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    # A large, poorly compressible RGB fixture catches oversized original decodes
    # and URLCache's per-response size limit (unlike a tiny solid-color PNG).
    width, height = 1800, 1200
    rng = random.Random(42)
    rows = b"".join(b"\0" + rng.randbytes(width * 3) for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    counts = Counter()
    payload = png()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args): pass
        def do_GET(self):
            path = urlsplit(self.path).path
            counts[path] += 1
            status, body, cache = 200, payload, "public, max-age=3600"
            if path == "/counts": body, cache = json.dumps(counts).encode(), "no-store"
            elif path == "/flaky" and counts[path] == 1: status, body, cache = 503, b"", "no-store"
            elif path == "/invalid": body = b"not an image"
            self.send_response(status)
            self.send_header("Content-Type", "image/png" if path != "/counts" else "application/json")
            self.send_header("Cache-Control", cache)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers(); self.wfile.write(body)

    server = ThreadingHTTPServer(("127.0.0.1", 18090), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        sdk = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], text=True).strip()
        with tempfile.TemporaryDirectory(prefix="aiknow-images-") as tmp:
            binary = str(Path(tmp) / "probe")
            subprocess.run(["xcrun", "--sdk", "iphonesimulator", "swiftc", "-parse-as-library", "-sdk", sdk, "-target",
                            platform.machine() + "-apple-ios17.0-simulator",
                            "ios/AiKnow/AiKnow/Services/ImageLoader.swift", "ios/tests/ImageLoaderProbe.swift",
                            "-o", binary], cwd=root, check=True)
            import os
            env = dict(os.environ, SDKROOT=sdk)
            subprocess.run(["xcrun", "simctl", "spawn", args.device, binary], env=env, check=True)
    finally:
        server.shutdown()
