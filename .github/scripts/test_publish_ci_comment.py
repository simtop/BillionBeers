#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import sys
import unittest
from unittest.mock import patch
from pathlib import Path

SCRIPT = Path(__file__).with_name("publish-ci-comment.py")
SPEC = importlib.util.spec_from_file_location("publish_ci_comment", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class PublishCommentTest(unittest.TestCase):
    @patch.object(MODULE, "api_request")
    def test_updates_existing_marker_instead_of_creating_duplicate(self, api):
        api.side_effect = [
            [{"body": "<!-- marker -->\nold", "url": "https://api.github.test/comments/1"}],
            {"html_url": "https://github.test/comment/1"},
        ]
        result = MODULE.publish("token", "owner/repo", "7", "<!-- marker -->", "new")
        self.assertEqual("https://github.test/comment/1", result)
        self.assertEqual(2, api.call_count)
        self.assertEqual("PATCH", api.call_args_list[1].args[2])

    @patch.object(MODULE, "api_request")
    def test_creates_comment_with_marker(self, api):
        api.side_effect = [[], {"html_url": "https://github.test/comment/2"}]
        result = MODULE.publish("token", "owner/repo", "7", "<!-- marker -->", "new")
        self.assertEqual("https://github.test/comment/2", result)
        self.assertEqual("POST", api.call_args_list[1].args[2])
        self.assertIn("<!-- marker -->", api.call_args_list[1].args[3]["body"])


if __name__ == "__main__":
    unittest.main()
