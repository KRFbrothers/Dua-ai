"""Check engine ownership and behavior against the shared documentation contract."""
import ast
import json
import os
from pathlib import Path
import re
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
CONTRACT_PATH = ROOT / "packages/contracts/KILL_SWITCHES.md"
CONTRACT = json.loads(re.search(r"```json\n(.*?)\n```", CONTRACT_PATH.read_text(), re.S).group(1))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import kill_switches


class KillSwitchContractTests(unittest.TestCase):
    def test_documented_and_implemented_flag_ownership(self):
        source = Path(kill_switches.__file__).read_text()
        tree = ast.parse(source)
        documented = set(re.findall(r"\bDUA_[A-Z_]+\b", ast.get_docstring(tree)))
        self.assertEqual(documented, set(CONTRACT["flags"]))
        self.assertIn("packages/contracts/KILL_SWITCHES.md", ast.get_docstring(tree))
        implemented = {node.value for node in ast.walk(tree)
                       if isinstance(node, ast.Constant) and isinstance(node.value, str)
                       and re.fullmatch(r"DUA_[A-Z_]+", node.value)}
        expected = {name for name, flag in CONTRACT["flags"].items() if "engine" in flag["implementedBy"]}
        self.assertEqual(implemented, expected)

    def test_defaults_and_truthy_values_match_contract(self):
        functions = {
            "DUA_DISABLE_AGENT": kill_switches.agent_disabled,
            "DUA_DISABLE_IMAGINE": kill_switches.imagine_disabled,
            "DUA_MAINTENANCE": kill_switches.maintenance_message,
        }
        expected = {name for name, flag in CONTRACT["flags"].items()
                    if flag["kind"] == "switch" and "engine" in flag["implementedBy"]}
        self.assertEqual(set(functions), expected)
        for name, function in functions.items():
            with self.subTest(flag=name), patch.dict(os.environ, {}, clear=True):
                self.assertEqual(bool(function()), CONTRACT["flags"][name]["default"])
            for value in CONTRACT["truthy"]:
                with self.subTest(flag=name, value=value), patch.dict(os.environ, {name: " " + value.upper() + " "}, clear=True):
                    self.assertTrue(function())
            for value in ("", "0", "false", "no", "off", "unexpected"):
                with self.subTest(flag=name, value=value), patch.dict(os.environ, {name: value}, clear=True):
                    self.assertFalse(function())

    def test_shared_messages_and_existing_message_rules(self):
        self.assertEqual(kill_switches.DEFAULT_MAINTENANCE_MESSAGE, CONTRACT["messages"]["maintenance"])
        self.assertEqual(kill_switches.AGENT_PAUSED, CONTRACT["messages"]["agent"])
        self.assertEqual(kill_switches.IMAGINE_PAUSED, CONTRACT["messages"]["imagine"])
        with patch.dict(os.environ, {"DUA_MAINTENANCE": "1"}, clear=True):
            self.assertEqual(kill_switches.maintenance_message(), CONTRACT["messages"]["maintenance"])
            os.environ["DUA_MAINTENANCE_MESSAGE"] = " \x01Back\nsoon\x7f "
            self.assertEqual(kill_switches.maintenance_message(), "Back soon")
            os.environ["DUA_MAINTENANCE_MESSAGE"] = "x" * 500
            self.assertEqual(len(kill_switches.maintenance_message()), CONTRACT["maintenanceMessageMaxLength"])
            os.environ["DUA_MAINTENANCE"] = "0"
            self.assertIsNone(kill_switches.maintenance_message())


if __name__ == "__main__":
    unittest.main()
