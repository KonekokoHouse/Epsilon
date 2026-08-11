#!/usr/bin/env python3
"""Generate the Java utility registry and LuaLS metadata for Epsilon's Lua API."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

from lua_codegen.api_model import ApiModelError, class_export_keys, load_api_model
from lua_codegen.java_utils import JavaSourceError, JavaUtilClass, discover_util_classes
from lua_codegen.lua_renderer import render_library


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "common" / "src" / "main" / "java" / "com" / "github" / "epsilon" / "scripting" / "lua"
UTIL_SOURCE_ROOT = ROOT / "common" / "src" / "main" / "java" / "com" / "github" / "epsilon" / "utils"
API_MODEL_PATH = ROOT / "scripts" / "lua_codegen" / "epsilon_api.json"
DEFAULT_OUTPUT = ROOT / "docs" / "examples" / "lua" / "epsilon_lib.lua"
DEFAULT_UTIL_REGISTRY_OUTPUT = JAVA_ROOT / "LuaUtilRegistry.java"


class GenerationError(RuntimeError):
    pass


def read_java(relative_path: str) -> str:
    path = JAVA_ROOT / relative_path
    try:
        return path.read_text(encoding="utf-8")
    except OSError as error:
        raise GenerationError(f"Cannot read Java source: {path}") from error


def extract_api_keys(relative_path: str) -> list[str]:
    source = read_java(relative_path)
    return re.findall(r'\bapi\.set\(\s*"([^"]+)"', source)


def verify_exports(model: dict[str, Any]) -> None:
    documented = {
        "LuaStorage.java": class_export_keys(model, "EpsilonStorage"),
        "LuaSettingApi.java": class_export_keys(model, "EpsilonSettingHostApi"),
        "LuaModuleApi.java": class_export_keys(model, "EpsilonModuleApi"),
        "render/LuaUiContext.java": class_export_keys(model, "EpsilonUiContext"),
        "render/LuaRender3DContext.java": class_export_keys(model, "EpsilonRender3DContext"),
        "LuaScriptPackage.java": class_export_keys(model, "EpsilonPackageApi"),
    }
    for relative_path, documented_keys in documented.items():
        exported_keys = set(extract_api_keys(relative_path))
        if exported_keys != documented_keys:
            missing = sorted(exported_keys - documented_keys)
            stale = sorted(documented_keys - exported_keys)
            details = []
            if missing:
                details.append("missing metadata: " + ", ".join(missing))
            if stale:
                details.append("stale metadata: " + ", ".join(stale))
            raise GenerationError(f"Lua API drift in {relative_path}: {'; '.join(details)}")

    runtime_source = read_java("LuaRuntime.java")
    if 'luajava.set("bindEventClass"' not in runtime_source:
        raise GenerationError("LuaRuntime no longer exports luajava.bindEventClass")
    if 'luajava.set("bindUtilClass"' not in runtime_source:
        raise GenerationError("LuaRuntime no longer exports luajava.bindUtilClass")


def extract_events() -> tuple[list[str], list[str]]:
    source = read_java("event/LuaEventRegistry.java")
    class_names: list[str] = []
    event_ids: list[str] = []
    for match in re.finditer(r'\bregister\(\s*"([^"]+)"\s*,(?P<body>.*?)\);', source, re.DOTALL):
        class_name = match.group(1)
        strings = re.findall(r'"([^"]+)"', match.group(0))
        class_names.append(class_name)
        event_ids.extend(strings[1:])
    if not class_names:
        raise GenerationError("No event registrations found in LuaEventRegistry.java")
    return event_ids, class_names


def render_util_registry(util_classes: list[JavaUtilClass]) -> str:
    registrations = "\n".join(
        f'        register("{value.simple_name}", {value.qualified_name}.class);'
        for value in util_classes
    )
    return f'''// 由 scripts/generate_epsilon_lib.py 自动生成，请勿手工编辑。
package com.github.epsilon.scripting.lua;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LuaUtilRegistry {{
    private static final Map<String, Class<?>> BY_NAME = new LinkedHashMap<>();

    static {{
{registrations}
    }}

    private LuaUtilRegistry() {{
    }}

    public static Class<?> resolve(String name) {{
        Class<?> utilClass = BY_NAME.get(name);
        if (utilClass == null) {{
            throw new IllegalArgumentException("未知 Epsilon util: " + name
                    + "，可用名称: " + String.join(", ", BY_NAME.keySet()));
        }}
        return utilClass;
    }}

    private static void register(String name, Class<?> type) {{
        Class<?> previous = BY_NAME.putIfAbsent(name, type);
        if (previous != null) throw new IllegalStateException("重复 Epsilon util name: " + name);
    }}
}}
'''


def generate_lua_library(util_classes: list[JavaUtilClass], model: dict[str, Any]) -> str:
    verify_exports(model)
    event_ids, class_names = extract_events()
    return render_library(
        model,
        {
            "event_ids": event_ids,
            "event_classes": class_names,
            "util_classes": [value.simple_name for value in util_classes],
        },
        util_classes,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate the Epsilon Lua utility registry and LuaLS library.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="Output path for the generated Lua library.")
    parser.add_argument(
        "--util-registry-output",
        type=Path,
        default=DEFAULT_UTIL_REGISTRY_OUTPUT,
        help="Output path for the generated Java utility registry.",
    )
    parser.add_argument("--check", action="store_true", help="Fail when generated outputs are missing or out of date.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output = args.output.resolve()
    util_registry_output = args.util_registry_output.resolve()
    try:
        model = load_api_model(API_MODEL_PATH)
        util_classes = discover_util_classes(UTIL_SOURCE_ROOT)
        generated_registry = render_util_registry(util_classes)
        generated_library = generate_lua_library(util_classes, model)
    except (ApiModelError, GenerationError, JavaSourceError, OSError, UnicodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    if args.check:
        stale = []
        for path, generated in (
            (util_registry_output, generated_registry),
            (output, generated_library),
        ):
            try:
                current = path.read_text(encoding="utf-8")
            except OSError:
                current = None
            if current != generated:
                stale.append(path)
        if stale:
            for path in stale:
                print(f"out of date: {path}", file=sys.stderr)
            print("run: uv run scripts/dev.py lua generate", file=sys.stderr)
            return 1
        print(f"up to date: {util_registry_output}")
        print(f"up to date: {output}")
        return 0

    util_registry_output.parent.mkdir(parents=True, exist_ok=True)
    util_registry_output.write_text(generated_registry, encoding="utf-8", newline="\n")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(generated_library, encoding="utf-8", newline="\n")
    print(f"generated: {util_registry_output}")
    print(f"generated: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
