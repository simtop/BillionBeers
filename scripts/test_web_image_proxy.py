#!/usr/bin/env python3

from __future__ import annotations

import http.client
import importlib.util
import sys
import threading
import unittest
from pathlib import Path
from urllib.parse import quote


MODULE_PATH = Path(__file__).with_name("web-image-proxy.py")
spec = importlib.util.spec_from_file_location("web_image_proxy", MODULE_PATH)
proxy = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = proxy
spec.loader.exec_module(proxy)


class WebImageProxyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original_fetcher = proxy.ImageProxyHandler.fetcher
        proxy.ImageProxyHandler.fetcher = staticmethod(
            lambda url: proxy.UpstreamResponse(200, "image/png", b"png-fixture")
        )
        cls.server = proxy.create_server("127.0.0.1", 0)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        proxy.ImageProxyHandler.fetcher = cls.original_fetcher

    def request(self, method, path, headers=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port)
        connection.request(method, path, headers=headers or {})
        response = connection.getresponse()
        body = response.read()
        connection.close()
        return response, body

    def test_allowed_image_has_local_cors_headers(self):
        target = "https://dropgate.malvik.dev/brewbuddy/images/fixture.jpg"
        response, body = self.request(
            "GET",
            "/image?url=" + quote(target, safe=""),
            {"Origin": "http://localhost:8080"},
        )
        self.assertEqual(response.status, 200)
        self.assertEqual(body, b"png-fixture")
        self.assertEqual(response.getheader("Content-Type"), "image/png")
        self.assertEqual(response.getheader("Access-Control-Allow-Origin"), "http://localhost:8080")
        self.assertEqual(response.getheader("Vary"), "Origin")

    def test_non_allowlisted_target_is_rejected(self):
        target = "https://example.com/brewbuddy/images/fixture.jpg"
        response, _ = self.request("GET", "/image?url=" + quote(target, safe=""))
        self.assertEqual(response.status, 400)

    def test_non_https_target_is_rejected(self):
        target = "http://dropgate.malvik.dev/brewbuddy/images/fixture.jpg"
        response, _ = self.request("GET", "/image?url=" + quote(target, safe=""))
        self.assertEqual(response.status, 400)

    def test_options_returns_cors_metadata(self):
        response, body = self.request(
            "OPTIONS", "/image", {"Origin": "http://127.0.0.1:8080"}
        )
        self.assertEqual(response.status, 204)
        self.assertEqual(body, b"")
        self.assertEqual(response.getheader("Access-Control-Allow-Origin"), "http://127.0.0.1:8080")

    def test_non_local_origin_is_not_allowed(self):
        response, _ = self.request(
            "OPTIONS", "/image", {"Origin": "https://attacker.example"}
        )
        self.assertIsNone(response.getheader("Access-Control-Allow-Origin"))

    def test_health_endpoint(self):
        response, body = self.request("GET", "/healthz")
        self.assertEqual(response.status, 200)
        self.assertEqual(body, b"ok\n")


if __name__ == "__main__":
    unittest.main()
