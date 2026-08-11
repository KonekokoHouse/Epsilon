#!/usr/bin/env python3
"""Epsilon 仓库开发脚本的统一命令入口。"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path
from typing import Sequence


ROOT = Path(__file__).resolve().parents[1]
LUA_GENERATOR = "scripts/generate_epsilon_lib.py"
LUA_TESTS = ("-m", "unittest", "discover", "-s", "scripts/tests")
I18N_COMPLETER = "scripts/complete_i18n.py"


def _run_command(arguments: Sequence[str], label: str) -> int:
    print(f"==> {label}", flush=True)
    environment = os.environ.copy()
    environment["PYTHONUTF8"] = "1"
    completed = subprocess.run(
        [sys.executable, *arguments],
        cwd=ROOT,
        env=environment,
        check=False,
    )
    return completed.returncode


def _run_steps(steps: Sequence[tuple[str, Sequence[str]]]) -> int:
    for label, arguments in steps:
        result = _run_command(arguments, label)
        if result != 0:
            return result
    return 0


def _lua_steps(action: str) -> tuple[tuple[str, Sequence[str]], ...]:
    generate = ("生成 Lua API", (LUA_GENERATOR,))
    check = ("检查 Lua API 生成物", (LUA_GENERATOR, "--check"))
    tests = ("运行 Lua codegen 测试", LUA_TESTS)
    if action == "generate":
        return (generate,)
    if action == "check":
        return (check,)
    if action == "test":
        return (tests,)
    if action == "verify":
        return tests, check
    if action == "update":
        return generate, tests, check
    raise ValueError(f"未知 Lua action: {action}")


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="统一运行 Epsilon 的 Python 维护与代码生成脚本。",
    )
    commands = parser.add_subparsers(dest="command", required=True)

    commands.add_parser(
        "verify",
        help="运行全部非修改性检查；当前等同于 lua verify。",
    )

    lua = commands.add_parser("lua", help="生成或检查 Lua API 类型库。")
    lua.add_argument(
        "action",
        choices=("generate", "check", "test", "verify", "update"),
        help="generate 只生成；check 只查漂移；test 只测试；verify 测试并查漂移；update 生成后验证。",
    )

    commands.add_parser(
        "i18n",
        help="运行 i18n 补全器；后续参数原样传给 complete_i18n.py。",
        add_help=False,
    )
    return parser


def _configure_console_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8")


def main(argv: Sequence[str] | None = None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)

    # i18n 有交互模式和较多可选参数，统一入口只透传，不复制另一份参数定义。
    if arguments and arguments[0] == "i18n":
        return _run_command((I18N_COMPLETER, *arguments[1:]), "运行 i18n 补全")

    args = create_parser().parse_args(arguments)
    if args.command == "verify":
        return _run_steps(_lua_steps("verify"))
    if args.command == "lua":
        return _run_steps(_lua_steps(args.action))
    raise AssertionError(f"未处理的命令: {args.command}")


if __name__ == "__main__":
    _configure_console_encoding()
    raise SystemExit(main())
