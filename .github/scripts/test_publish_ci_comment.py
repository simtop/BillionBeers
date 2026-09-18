#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import sys
import unittest
from unittest.mock import patch
from pathlib import Path

SCRIPT = Path(__file__).with_name("publish-ci-comment.py")
SPEC = importlib.util.spec_from_file_location("publish_ci_comment", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)
MARKER = "<!-- marker -->"


def comment(body="old", login="github-actions[bot]", kind="Bot"):
    return {"body": f"{MARKER}\n{body}\n", "url": "https://api.github.test/comments/1",
            "html_url": "https://github.test/comment/1", "user": {"login": login, "type": kind}}


class PublishCommentTest(unittest.TestCase):
    @patch.object(MODULE, "api_request")
    def test_updates_existing_marker_instead_of_creating_duplicate(self, api):
        api.side_effect = [[comment()], {"html_url": "https://github.test/comment/1"}]
        result = MODULE.publish("token", "owner/repo", "7", MARKER, "new")
        self.assertEqual("https://github.test/comment/1", result)
        self.assertEqual("PATCH", api.call_args_list[1].args[2])
        self.assertNotIn("old", api.call_args_list[1].args[3]["body"])

    @patch.object(MODULE, "api_request")
    def test_creates_comment_with_marker(self, api):
        api.side_effect = [[], {"html_url": "https://github.test/comment/2"}]
        result = MODULE.publish("token", "owner/repo", "7", MARKER, "new")
        self.assertEqual("https://github.test/comment/2", result)
        self.assertEqual("POST", api.call_args_list[1].args[2])
        self.assertIn(MARKER, api.call_args_list[1].args[3]["body"])

    @patch.object(MODULE, "api_request")
    def test_finds_marker_after_first_page(self, api):
        api.side_effect = [[{"body": "unrelated"}] * 100, [comment()], {"html_url": "url"}]
        MODULE.publish("token", "owner/repo", "7", MARKER, "new")
        self.assertIn("page=2", api.call_args_list[1].args[1])
        self.assertEqual("PATCH", api.call_args_list[2].args[2])

    @patch.object(MODULE, "api_request")
    def test_ignores_forged_marker_and_other_bots(self, api):
        api.side_effect = [[comment(login="a-person", kind="User"), comment(login="another[bot]")], {"html_url": "url"}]
        MODULE.publish("token", "owner/repo", "7", MARKER, "new")
        self.assertEqual("POST", api.call_args_list[1].args[2])

    @patch.object(MODULE, "api_request")
    def test_unchanged_body_is_not_patched(self, api):
        api.return_value = [comment("new")]
        MODULE.publish("token", "owner/repo", "7", MARKER, "new")
        self.assertEqual(1, api.call_count)

    @patch.object(MODULE, "api_request")
    def test_pending_and_success_do_not_create_a_comment(self, api):
        api.return_value = []
        self.assertIsNone(MODULE.publish("token", "owner/repo", "7", MARKER, "passed", create=False))
        self.assertEqual(1, api.call_count)

    @patch.object(MODULE, "api_request")
    def test_update_only_replaces_existing_failure(self, api):
        api.side_effect = [[comment("old failures")], {"html_url": "url"}]
        MODULE.publish("token", "owner/repo", "7", MARKER, "passed", create=False)
        self.assertEqual("PATCH", api.call_args_list[1].args[2])
        self.assertNotIn("old failures", api.call_args_list[1].args[3]["body"])

    @patch.object(MODULE, "api_request")
    def test_generation_is_rechecked_after_comment_lookup(self, api):
        api.return_value = [comment()]
        checked = []

        def stale():
            checked.append(api.call_count)
            return False

        MODULE.publish("token", "owner/repo", "7", MARKER, "new", is_current=stale)
        self.assertEqual([1], checked)
        self.assertEqual(1, api.call_count)

    @patch.object(MODULE, "api_request")
    def test_post_timeout_retry_relists_before_creating(self, api):
        api.side_effect = [[], OSError("response lost"), [comment("new")]]
        with self.assertRaises(OSError):
            MODULE.publish("token", "owner/repo", "7", MARKER, "new")
        MODULE.publish("token", "owner/repo", "7", MARKER, "new")
        self.assertEqual(1, sum(len(call.args) > 2 and call.args[2] == "POST" for call in api.call_args_list))

    @patch.object(MODULE, "api_request")
    def test_duplicate_wakeup_does_not_reset_same_generation_failures(self, api):
        state = '<!-- billionbeers-ci:state {"attempt": 1} -->'
        api.return_value = [comment(f"{state}\nknown failure")]
        MODULE.publish("token", "owner/repo", "7", MARKER, f"{state}\npending", create=False, reset=True)
        self.assertEqual(1, api.call_count)

    @patch.object(MODULE, "api_request")
    def test_new_generation_does_reset_old_failures(self, api):
        api.side_effect = [[comment('<!-- billionbeers-ci:state {"attempt": 1} -->\nold failure')], {"html_url": "url"}]
        MODULE.publish("token", "owner/repo", "7", MARKER,
                       '<!-- billionbeers-ci:state {"attempt": 2} -->\npending', create=False, reset=True)
        self.assertEqual("PATCH", api.call_args.args[2])
        self.assertNotIn("old failure", api.call_args.args[3]["body"])

    def test_truncation_preserves_marker_and_metadata_within_limit(self):
        body = f"{MARKER}\n<!-- generation: 2 -->\n" + "x" * 10000
        result = MODULE.bounded_body(MARKER, body)
        self.assertLessEqual(len(result), MODULE.MAX_COMMENT_LENGTH)
        self.assertTrue(result.startswith(f"{MARKER}\n<!-- generation: 2 -->"))
        self.assertIn("Comment truncated", result)


if __name__ == "__main__":
    unittest.main()
