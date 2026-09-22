#!/usr/bin/env python3
"""Loopback-only image proxy for local Web host QA."""

from __future__ import annotations

import argparse
import http.server
import os
import re
import sys
from dataclasses import dataclass
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urljoin, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

ALLOWED_HOST = "dropgate.malvik.dev"
ALLOWED_PATH_PREFIX = "/brewbuddy/images/"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8787
MAX_IMAGE_BYTES = 10 * 1024 * 1024
FETCH_TIMEOUT_SECONDS = 15
LOCAL_ORIGIN = re.compile(r"^https?://(?:localhost|127\.0\.0\.1|\[::1\]):\d+$")


@dataclass(frozen=True)
class UpstreamResponse:
    status: int
    content_type: str
    body: bytes


def validate_target(raw_url: str) -> str:
    parsed = urlparse(raw_url)
    if (
        parsed.scheme != "https"
        or parsed.hostname != ALLOWED_HOST
        or parsed.username is not None
        or parsed.password is not None
        or parsed.port is not None
        or not parsed.path.startswith(ALLOWED_PATH_PREFIX)
    ):
        raise ValueError("target is not an allowed Brew Buddy image URL")
    return parsed.geturl()


class NoRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def fetch_image(raw_url: str, max_bytes: int = MAX_IMAGE_BYTES) -> UpstreamResponse:
    current_url = validate_target(raw_url)
    opener = build_opener(NoRedirectHandler)
    for _ in range(4):
        request = Request(current_url, headers={"Accept": "image/*"})
        try:
            response = opener.open(request, timeout=FETCH_TIMEOUT_SECONDS)
        except HTTPError as error:
            if error.code in {301, 302, 303, 307, 308}:
                location = error.headers.get("Location")
                if not location:
                    raise RuntimeError("upstream redirect has no location") from error
                current_url = validate_target(urljoin(current_url, location))
                continue
            raise RuntimeError(f"upstream returned HTTP {error.code}") from error
        except URLError as error:
            raise RuntimeError(f"upstream request failed: {error.reason}") from error

        content_type = response.headers.get_content_type()
        if not content_type.startswith("image/"):
            raise RuntimeError("upstream response is not an image")
        declared_length = response.headers.get("Content-Length")
        if declared_length and int(declared_length) > max_bytes:
            raise RuntimeError("upstream image is too large")
        body = response.read(max_bytes + 1)
        if len(body) > max_bytes:
            raise RuntimeError("upstream image is too large")
        return UpstreamResponse(response.status, content_type, body)
    raise RuntimeError("too many upstream redirects")


def origin_from_request(handler: http.server.BaseHTTPRequestHandler) -> str | None:
    origin = handler.headers.get("Origin")
    return origin if origin and LOCAL_ORIGIN.fullmatch(origin) else None


class ImageProxyHandler(http.server.BaseHTTPRequestHandler):
    fetcher = staticmethod(fetch_image)

    def log_message(self, format: str, *args) -> None:
        print(format % args, file=sys.stderr, flush=True)

    def _cors_headers(self, origin: str | None) -> None:
        self.send_header("Vary", "Origin")
        if origin:
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _response(self, status: int, body: bytes, content_type: str, origin: str | None) -> None:
        self.send_response(status)
        self._cors_headers(origin)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_OPTIONS(self) -> None:
        self._response(204, b"", "text/plain; charset=utf-8", origin_from_request(self))

    def do_GET(self) -> None:
        origin = origin_from_request(self)
        parsed = urlparse(self.path)
        if parsed.path == "/healthz":
            self._response(200, b"ok\n", "text/plain; charset=utf-8", origin)
            return
        if parsed.path != "/image":
            self._response(404, b"not found\n", "text/plain; charset=utf-8", origin)
            return
        raw_url = parse_qs(parsed.query).get("url", [""])[0]
        try:
            target = validate_target(raw_url)
            result = self.fetcher(target)
            self._response(result.status, result.body, result.content_type, origin)
        except ValueError as error:
            self._response(400, f"{error}\n".encode(), "text/plain; charset=utf-8", origin)
        except Exception as error:  # noqa: BLE001 - convert upstream failures to HTTP errors.
            self._response(502, f"image proxy failed: {error}\n".encode(), "text/plain; charset=utf-8", origin)


def create_server(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT):
    return http.server.ThreadingHTTPServer((host, port), ImageProxyHandler)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.environ.get("WEB_IMAGE_PROXY_HOST", DEFAULT_HOST))
    parser.add_argument(
        "--port",
        type=int,
        default=int(os.environ.get("WEB_IMAGE_PROXY_PORT", DEFAULT_PORT)),
    )
    args = parser.parse_args()
    server = create_server(args.host, args.port)
    print(f"Web image proxy listening on http://{args.host}:{server.server_port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
