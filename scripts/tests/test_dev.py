from __future__ import annotations

import unittest
from unittest.mock import call, patch

from scripts import dev


class DevEntryPointTest(unittest.TestCase):
    @patch("scripts.dev._run_command", return_value=0)
    def test_top_level_verify_runs_tests_then_drift_check(self, run_command) -> None:
        result = dev.main(["verify"])

        self.assertEqual(0, result)
        self.assertEqual(
            [
                call(dev.LUA_TESTS, "运行 Lua codegen 测试"),
                call((dev.LUA_GENERATOR, "--check"), "检查 Lua API 生成物"),
            ],
            run_command.call_args_list,
        )

    @patch("scripts.dev._run_command", side_effect=(0, 7, 0))
    def test_update_stops_after_the_first_failed_step(self, run_command) -> None:
        result = dev.main(["lua", "update"])

        self.assertEqual(7, result)
        self.assertEqual(2, run_command.call_count)

    @patch("scripts.dev._run_command", return_value=0)
    def test_i18n_forwards_all_arguments(self, run_command) -> None:
        result = dev.main(
            ["i18n", "--source", "fabric", "--owner", "epsilon", "--dry-run"]
        )

        self.assertEqual(0, result)
        run_command.assert_called_once_with(
            (
                dev.I18N_COMPLETER,
                "--source",
                "fabric",
                "--owner",
                "epsilon",
                "--dry-run",
            ),
            "运行 i18n 补全",
        )


if __name__ == "__main__":
    unittest.main()
