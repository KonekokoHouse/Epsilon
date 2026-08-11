from __future__ import annotations

import json
from pathlib import Path
from typing import Any


class ApiModelError(RuntimeError):
    pass


def load_api_model(path: Path) -> dict[str, Any]:
    try:
        model = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ApiModelError(f"Cannot load Lua API model: {path}: {error}") from error
    if not isinstance(model, dict):
        raise ApiModelError(f"Lua API model root must be an object: {path}")
    _require_list(model, "aliases", path)
    classes = _require_list(model, "classes", path)
    _require_list(model, "globals", path)
    seen_classes: set[str] = set()
    for class_model in classes:
        if not isinstance(class_model, dict):
            raise ApiModelError(f"Every class must be an object: {path}")
        name = _require_string(class_model, "name", path)
        if name in seen_classes:
            raise ApiModelError(f"Duplicate Lua API class {name}: {path}")
        seen_classes.add(name)
        if "fields" in class_model:
            _require_list(class_model, "fields", path)
        if "methods" in class_model:
            _require_list(class_model, "methods", path)
    return model


def class_export_keys(model: dict[str, Any], class_name: str) -> set[str]:
    class_model = next(
        (value for value in model["classes"] if value.get("name") == class_name),
        None,
    )
    if class_model is None:
        raise ApiModelError(f"Lua API model has no class named {class_name}")
    return {
        value["name"]
        for collection in (class_model.get("fields", []), class_model.get("methods", []))
        for value in collection
    }


def _require_list(model: dict[str, Any], key: str, path: Path) -> list[Any]:
    value = model.get(key)
    if not isinstance(value, list):
        raise ApiModelError(f"Lua API model key {key!r} must be an array: {path}")
    return value


def _require_string(model: dict[str, Any], key: str, path: Path) -> str:
    value = model.get(key)
    if not isinstance(value, str) or not value:
        raise ApiModelError(f"Lua API model key {key!r} must be a non-empty string: {path}")
    return value
