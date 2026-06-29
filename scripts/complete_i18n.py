#!/usr/bin/env python3
"""用 runClient 生成的 epsilon-empty-i18n.json 同步语言文件。"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from collections import OrderedDict
from datetime import datetime
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_I18N_DIR = PROJECT_ROOT / "common" / "src" / "main" / "resources" / "assets" / "epsilon" / "i18n"
EMPTY_I18N_FILE = "epsilon-empty-i18n.json"
DEFAULT_EMPTY_I18N_PATHS = {
    "fabric": PROJECT_ROOT / "fabric" / "runs" / "client" / EMPTY_I18N_FILE,
    "neoforge": PROJECT_ROOT / "neoforge" / "runs" / "client" / EMPTY_I18N_FILE,
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="根据 epsilon-empty-i18n.json 为指定 i18n JSON 文件补全、排序并删除多余翻译 key。",
    )
    parser.add_argument(
        "--source",
        choices=("fabric", "neoforge", "custom"),
        help="空 i18n 模板来源；未指定时进入交互选择。",
    )
    parser.add_argument(
        "--empty-i18n",
        type=Path,
        help="自定义 epsilon-empty-i18n.json 路径。设置后等同于 --source custom。",
    )
    parser.add_argument(
        "--target",
        type=Path,
        help="要补全的目标 i18n JSON 文件路径；未指定时通过 input 选择。",
    )
    parser.add_argument(
        "--missing-value",
        default="",
        help="新增 key 使用的值，默认保留为空字符串。",
    )
    sort_group = parser.add_mutually_exclusive_group()
    sort_group.add_argument(
        "--sort",
        dest="sort",
        action="store_true",
        help="按 epsilon-empty-i18n.json 中的 key 顺序排序目标文件。",
    )
    sort_group.add_argument(
        "--no-sort",
        dest="sort",
        action="store_false",
        help="不调整目标文件已有 key 的顺序。",
    )
    parser.set_defaults(sort=None)
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="写回前不创建 .bak 备份文件。",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="只显示将新增和删除的 key，不写入文件。",
    )
    return parser.parse_args()


def resolve_path(path: Path) -> Path:
    path = path.expanduser()
    if not path.is_absolute():
        path = PROJECT_ROOT / path
    return path.resolve()


def prompt(message: str, default: str | None = None) -> str:
    suffix = f" [{default}]" if default else ""
    value = input(f"{message}{suffix}: ").strip()
    return value or (default or "")


def prompt_bool(message: str, default: bool) -> bool:
    default_text = "y" if default else "n"
    while True:
        value = prompt(message, default_text).lower()
        if value in ("y", "yes", "true", "1", "是"):
            return True
        if value in ("n", "no", "false", "0", "否"):
            return False
        print("请输入 y 或 n。")


def choose_empty_i18n_path(args: argparse.Namespace) -> Path:
    if args.empty_i18n:
        return resolve_path(args.empty_i18n)

    source = args.source
    if source is None:
        print("请选择 epsilon-empty-i18n.json 来源：")
        print("  1. fabric   -> fabric/runs/client/epsilon-empty-i18n.json")
        print("  2. neoforge -> neoforge/runs/client/epsilon-empty-i18n.json")
        print("  3. custom   -> 手动输入路径")
        selected = prompt("输入 1/2/3 或 fabric/neoforge/custom", "fabric").lower()
        source = {
            "1": "fabric",
            "2": "neoforge",
            "3": "custom",
        }.get(selected, selected)

    if source in DEFAULT_EMPTY_I18N_PATHS:
        return DEFAULT_EMPTY_I18N_PATHS[source].resolve()

    if source == "custom":
        return resolve_path(Path(prompt("请输入 epsilon-empty-i18n.json 路径")))

    raise ValueError(f"未知模板来源：{source}")


def list_i18n_files() -> list[Path]:
    if not DEFAULT_I18N_DIR.exists():
        return []
    return sorted(DEFAULT_I18N_DIR.glob("*.json"))


def choose_target_path(args: argparse.Namespace) -> Path:
    if args.target:
        return resolve_path(args.target)

    files = list_i18n_files()
    if files:
        print("请选择要补全的 i18n 文件：")
        for index, file_path in enumerate(files, start=1):
            print(f"  {index}. {file_path.relative_to(PROJECT_ROOT)}")
        print("  custom. 手动输入路径")

        selected = prompt("输入序号或 custom", "1").lower()
        if selected.isdigit():
            index = int(selected)
            if 1 <= index <= len(files):
                return files[index - 1].resolve()
            raise ValueError(f"序号超出范围：{selected}")
        if selected != "custom":
            return resolve_path(Path(selected))

    return resolve_path(Path(prompt("请输入目标 i18n JSON 文件路径")))


def choose_sort_enabled(args: argparse.Namespace) -> bool:
    if args.sort is not None:
        return args.sort
    if sys.stdin.isatty():
        return prompt_bool("是否按 epsilon-empty-i18n.json 的 key 顺序排序", True)
    return False


def load_json_object(path: Path) -> OrderedDict[str, Any]:
    if not path.is_file():
        raise FileNotFoundError(f"文件不存在：{path}")

    try:
        with path.open("r", encoding="utf-8") as file:
            data = json.load(file, object_pairs_hook=OrderedDict)
    except json.JSONDecodeError as exc:
        raise ValueError(f"JSON 解析失败：{path} ({exc})") from exc

    if not isinstance(data, dict):
        raise ValueError(f"文件根节点必须是 JSON object：{path}")
    return data


def merge_i18n(
    template: OrderedDict[str, Any],
    target: OrderedDict[str, Any],
    missing_value: str,
    sort_by_template: bool,
) -> tuple[OrderedDict[str, Any], list[str], list[str]]:
    template_keys = set(template.keys())
    added_keys: list[str] = []
    removed_keys = [key for key in target.keys() if key not in template_keys]

    if sort_by_template:
        merged: OrderedDict[str, Any] = OrderedDict()
        keys = template.keys()
    else:
        merged = OrderedDict((key, value) for key, value in target.items() if key in template_keys)
        keys = template.keys()

    for key in keys:
        if key in target:
            if key not in merged:
                merged[key] = target[key]
        else:
            merged[key] = missing_value
            added_keys.append(key)

    return merged, added_keys, removed_keys


def create_backup(path: Path) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_path = path.with_name(f"{path.name}.{timestamp}.bak")
    shutil.copy2(path, backup_path)
    return backup_path


def write_json(path: Path, data: OrderedDict[str, Any]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)
        file.write("\n")


def main() -> int:
    args = parse_args()

    try:
        empty_i18n_path = choose_empty_i18n_path(args)
        target_path = choose_target_path(args)
        sort_by_template = choose_sort_enabled(args)

        template = load_json_object(empty_i18n_path)
        target = load_json_object(target_path)
        merged, added_keys, removed_keys = merge_i18n(template, target, args.missing_value, sort_by_template)

        print(f"模板文件：{empty_i18n_path}")
        print(f"目标文件：{target_path}")
        print(f"按模板排序：{'是' if sort_by_template else '否'}")
        print(f"模板 key 数：{len(template)}")
        print(f"目标原 key 数：{len(target)}")
        print(f"新增 key 数：{len(added_keys)}")
        print(f"删除 key 数：{len(removed_keys)}")

        if added_keys:
            print("新增 key：")
            for key in added_keys:
                print(f"  {key}")
        if removed_keys:
            print("删除 key：")
            for key in removed_keys:
                print(f"  {key}")

        if args.dry_run:
            print("dry-run 模式，未写入文件。")
            return 0

        backup_path: Path | None = None
        if not args.no_backup:
            backup_path = create_backup(target_path)

        write_json(target_path, merged)

        if backup_path:
            print(f"已创建备份：{backup_path}")
        print("i18n 文件补全完成。")
        return 0
    except (OSError, ValueError) as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
