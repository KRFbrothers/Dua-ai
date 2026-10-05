import unittest
from types import SimpleNamespace
from dua_coder.status import task_status
from dua_coder.proposals import KEY


class StatusTests(unittest.TestCase):
    def context(self, head="a" * 40, checked="a" * 40):
        return SimpleNamespace(invocation_id="turn", state={KEY: {"id": {
            "created": 1, "status": "published", "base_sha": "b" * 40,
            "changes": [{"path": "README.md", "content": "private-source"}],
            "review": {"verdict": "ready"}, "result": {
                "url": "https://github.com/pr/1", "branch": "dua-agent/test", "commit_sha": head}}},
            "dua:validation_result": {"result": {"branch": "dua-agent/test", "head_sha": checked,
                "status": "passed", "latest": {"url": "https://github.com/run/1"}}}})

    def test_exact_result_and_no_source(self):
        result = task_status(self.context())
        self.assertEqual(result["checks"]["status"], "passed")
        self.assertEqual(result["patch"]["files"], ["README.md"])
        self.assertNotIn("private-source", str(result))

    def test_other_commit_cannot_mark_latest_patch_passed(self):
        self.assertEqual(task_status(self.context(checked="c" * 40))["checks"]["status"], "not_verified")

    def test_no_patch_or_evidence_is_not_verified(self):
        result = task_status(SimpleNamespace(invocation_id="turn", state={}))
        self.assertIsNone(result["patch"])
        self.assertEqual(result["checks"]["status"], "not_verified")
