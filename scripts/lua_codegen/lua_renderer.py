from __future__ import annotations

from collections import defaultdict
from typing import Any, Iterable

from .java_utils import (
    JavaConstructor,
    JavaExecutable,
    JavaMethod,
    JavaNestedEnum,
    JavaParameter,
    JavaType,
    JavaUtilClass,
)


def render_library(
    model: dict[str, Any],
    dynamic_aliases: dict[str, list[str]],
    util_classes: list[JavaUtilClass],
) -> str:
    lines = ["---@meta"]
    diagnostics = model.get("diagnostics", [])
    if diagnostics:
        lines.append(f"---@diagnostic disable: {', '.join(diagnostics)}")
    lines.append("")
    lines.extend(f"-- {line}" for line in model.get("comments", []))
    lines.append("")

    for alias in model["aliases"]:
        values = dynamic_aliases.get(alias.get("dynamic"), alias.get("values"))
        if values is None:
            lines.append(f"---@alias {alias['name']} {alias['type']}")
        else:
            lines.append(f"---@alias {alias['name']}")
            lines.extend(f"---| {_quote(value)}" for value in values)
    lines.append("")

    for class_model in model["classes"]:
        lines.extend(_render_class(class_model, util_classes))
        lines.append("")

    lines.extend(_render_utils(util_classes))
    if util_classes:
        lines.append("")

    for global_model in model["globals"]:
        if global_model.get("description"):
            lines.extend(_description(global_model["description"]))
        lines.append(f"---@type {global_model['type']}")
        lines.append(f"{global_model['name']} = {global_model.get('value', 'nil')}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def _render_class(class_model: dict[str, Any], util_classes: list[JavaUtilClass]) -> list[str]:
    name = class_model["name"]
    annotation = f"---@class {name}"
    if class_model.get("generic"):
        annotation += f"<{class_model['generic']}>"
    if class_model.get("extends"):
        annotation += f": {class_model['extends']}"
    lines = [annotation]
    for field in class_model.get("fields", []):
        readonly = " readonly" if field.get("readonly") else ""
        optional = "?" if field.get("optional") else ""
        suffix = f" {field['description']}" if field.get("description") else ""
        lines.append(f"---@field{readonly} {field['name']}{optional} {field['type']}{suffix}")
    local_name = class_model.get("local")
    if local_name:
        lines.append(f"local {local_name} = {{}}")
    for method in class_model.get("methods", []):
        lines.append("")
        lines.extend(_render_json_method(local_name, method, util_classes))
    return lines


def _render_json_method(
    local_name: str | None,
    method: dict[str, Any],
    util_classes: list[JavaUtilClass],
) -> list[str]:
    lines = _description(method.get("description"))
    overloads = list(method.get("overloads", []))
    dynamic = method.get("dynamic_overloads")
    if dynamic == "bind_util_class":
        overloads.extend(
            {
                "parameters": [{"name": "utilName", "type": _quote(util.simple_name)}],
                "returns": [{"type": _util_class_type(util)}],
            }
            for util in util_classes
        )
    elif dynamic == "bind_nested_enum":
        overloads.extend(
            {
                "parameters": [{"name": "className", "type": _quote(enum.binary_name)}],
                "returns": [{"type": _enum_class_type(util, enum)}],
            }
            for util in util_classes
            for enum in util.nested_enums
        )
    elif dynamic in {"construct_from_class", "construct_from_name"}:
        overloads.extend(_constructor_overloads(util_classes, dynamic == "construct_from_name"))
    for overload in overloads:
        lines.append(f"---@overload {_json_function_type(overload)}")
    for parameter in method.get("parameters", []):
        optional = "?" if parameter.get("optional") else ""
        suffix = f" {parameter['description']}" if parameter.get("description") else ""
        lines.append(f"---@param {parameter['name']}{optional} {parameter['type']}{suffix}")
    for returned in method.get("returns", []):
        suffix = ""
        if returned.get("name"):
            suffix += f" {returned['name']}"
        if returned.get("description"):
            suffix += f" {returned['description']}"
        lines.append(f"---@return {returned['type']}{suffix}")
    params = ", ".join(value["name"] for value in method.get("parameters", []))
    if method.get("call") == "index":
        lines.append(f'{local_name}[{_quote(method["name"])}] = function({params}) end')
    else:
        separator = "." if method.get("call") == "dot" else ":"
        lines.append(f"function {local_name}{separator}{method['name']}({params}) end")
    return lines


def _render_utils(util_classes: list[JavaUtilClass]) -> list[str]:
    lines: list[str] = []
    for util in util_classes:
        if lines:
            lines.append("")
        lines.extend(_render_util(util, util_classes))
    return lines


def _render_util(util: JavaUtilClass, util_classes: list[JavaUtilClass]) -> list[str]:
    instance_fields = [field for field in util.fields if not field.is_static]
    static_fields = [field for field in util.fields if field.is_static]
    instance_methods = [method for method in util.methods if not method.is_static]
    static_methods = [method for method in util.methods if method.is_static]
    lines = [f"---Java utility: `{util.qualified_name}`."]

    if util.constructors or instance_fields or instance_methods:
        lines.append(f"---@class {_util_instance_type(util)}")
        lines.extend(_render_java_fields(instance_fields, util_classes))
        lines.append(f"local {_util_instance_local(util)} = {{}}")
        lines.extend(
            _render_java_methods(
                _util_instance_local(util),
                _util_instance_type(util),
                instance_methods,
                util_classes,
            )
        )
        lines.append("")

    lines.append(f"---@class {_util_class_type(util)}")
    lines.extend(_render_java_fields(static_fields, util_classes))
    lines.append(f"local {_util_class_local(util)} = {{}}")
    lines.extend(
        _render_java_methods(
            _util_class_local(util),
            _util_class_type(util),
            static_methods,
            util_classes,
        )
    )

    for enum in util.nested_enums:
        lines.append("")
        lines.append(f"---Java enum: `{enum.qualified_name}` (`{enum.binary_name}`).")
        lines.append(f"---@class {_enum_instance_type(util, enum)}")
        lines.append(f"local {_enum_instance_local(util, enum)} = {{}}")
        lines.append("")
        lines.append(f"---@class {_enum_class_type(util, enum)}")
        for constant in enum.constants:
            lines.append(f"---@field readonly {constant} {_enum_instance_type(util, enum)}")
        lines.append(f"local {_enum_class_local(util, enum)} = {{}}")
    return lines


def _render_java_fields(fields: Iterable[Any], util_classes: list[JavaUtilClass]) -> list[str]:
    lines: list[str] = []
    for field in fields:
        readonly = " readonly" if field.is_final else ""
        lua_type = _lua_type(field.type, util_classes)
        lines.append(
            f"---@field{readonly} {field.name} {lua_type} Java type: `{field.type.source}`."
        )
    return lines


def _render_java_methods(
    local_name: str,
    type_name: str,
    methods: list[JavaMethod],
    util_classes: list[JavaUtilClass],
) -> list[str]:
    grouped: dict[str, list[JavaMethod]] = defaultdict(list)
    for method in methods:
        grouped[method.name].append(method)
    lines: list[str] = []
    for name, overloads in grouped.items():
        primary, *alternatives = overloads
        lines.append("")
        for overload in alternatives:
            lines.append(
                f"---@overload {_java_function_type(overload, util_classes, self_type=type_name)}"
            )
        lines.extend(_java_parameter_annotations(primary.parameters, util_classes))
        if primary.return_type.source != "void":
            lines.append(
                f"---@return {_lua_type(primary.return_type, util_classes)} value "
                f"Java type: `{primary.return_type.source}`."
            )
        params = ", ".join(_declaration_parameter_names(primary.parameters))
        lines.append(f"function {local_name}:{name}({params}) end")
    return lines


def _java_parameter_annotations(
    parameters: tuple[JavaParameter, ...],
    util_classes: list[JavaUtilClass],
) -> list[str]:
    lines: list[str] = []
    for parameter in parameters:
        name = "..." if parameter.varargs else parameter.name
        lines.append(
            f"---@param {name} {_lua_type(parameter.type, util_classes)} "
            f"Java type: `{parameter.type.source}{'...' if parameter.varargs else ''}`."
        )
    return lines


def _declaration_parameter_names(parameters: tuple[JavaParameter, ...]) -> list[str]:
    return ["..." if parameter.varargs else parameter.name for parameter in parameters]


def _constructor_overloads(
    util_classes: list[JavaUtilClass],
    from_name: bool,
) -> list[dict[str, Any]]:
    overloads: list[dict[str, Any]] = []
    for util in util_classes:
        for constructor in util.constructors:
            first = {
                "name": "className" if from_name else "javaClass",
                "type": _quote(util.qualified_name) if from_name else _util_class_type(util),
            }
            overloads.append(
                {
                    "parameters": [first, *_json_parameters(constructor, util_classes)],
                    "returns": [{"type": _util_instance_type(util)}],
                }
            )
    return overloads


def _json_parameters(
    executable: JavaExecutable,
    util_classes: list[JavaUtilClass],
) -> list[dict[str, str]]:
    return [
        {
            "name": "..." if parameter.varargs else parameter.name,
            "type": _lua_type(parameter.type, util_classes),
        }
        for parameter in executable.parameters
    ]


def _java_function_type(
    executable: JavaMethod | JavaConstructor,
    util_classes: list[JavaUtilClass],
    self_type: str | None = None,
) -> str:
    params = []
    if self_type:
        params.append(f"self: {self_type}")
    params.extend(
        f"{'...' if parameter.varargs else parameter.name}: {_lua_type(parameter.type, util_classes)}"
        for parameter in executable.parameters
    )
    result = f"fun({', '.join(params)})"
    if isinstance(executable, JavaMethod) and executable.return_type.source != "void":
        result += f": {_lua_type(executable.return_type, util_classes)}"
    return result


def _json_function_type(overload: dict[str, Any]) -> str:
    params = ", ".join(
        f"{parameter['name']}: {parameter['type']}"
        for parameter in overload.get("parameters", [])
    )
    result = f"fun({params})"
    returns = overload.get("returns", [])
    if returns:
        result += ": " + ", ".join(value["type"] for value in returns)
    return result


def _lua_type(java_type: JavaType, util_classes: list[JavaUtilClass]) -> str:
    source = java_type.source
    raw = re_sub_java_type(source)
    primitives = {
        "boolean": "boolean",
        "Boolean": "boolean",
        "byte": "integer",
        "Byte": "integer",
        "short": "integer",
        "Short": "integer",
        "int": "integer",
        "Integer": "integer",
        "long": "integer",
        "Long": "integer",
        "BigInteger": "integer",
        "float": "number",
        "Float": "number",
        "double": "number",
        "Double": "number",
        "BigDecimal": "number",
        "char": "string",
        "Character": "string",
        "String": "string",
        "CharSequence": "string",
        "void": "nil",
    }
    lua_type = None if source.endswith("[]") else primitives.get(raw)
    if lua_type is None and not source.endswith("[]"):
        util = next((value for value in util_classes if value.simple_name == raw), None)
        if util is not None:
            lua_type = _util_instance_type(util)
        else:
            nested = next(
                (
                    (owner, enum)
                    for owner in util_classes
                    for enum in owner.nested_enums
                    if raw in {enum.simple_name, f"{owner.simple_name}.{enum.simple_name}"}
                ),
                None,
            )
            if nested is not None:
                lua_type = _enum_instance_type(*nested)
    if lua_type is None:
        lua_type = "userdata"
    if java_type.nullable and lua_type != "nil":
        lua_type += "|nil"
    return lua_type


def re_sub_java_type(source: str) -> str:
    raw = source
    if "<" in raw:
        raw = raw[:raw.index("<")]
    raw = raw.removesuffix("[]")
    return raw.rsplit(".", 1)[-1]


def _util_instance_type(util: JavaUtilClass) -> str:
    return f"EpsilonJava{util.simple_name}"


def _util_class_type(util: JavaUtilClass) -> str:
    return f"EpsilonJava{util.simple_name}Class"


def _util_instance_local(util: JavaUtilClass) -> str:
    return f"Java{util.simple_name}"


def _util_class_local(util: JavaUtilClass) -> str:
    return f"Java{util.simple_name}Class"


def _enum_instance_type(util: JavaUtilClass, enum: JavaNestedEnum) -> str:
    return f"EpsilonJava{util.simple_name}{enum.simple_name}"


def _enum_class_type(util: JavaUtilClass, enum: JavaNestedEnum) -> str:
    return f"{_enum_instance_type(util, enum)}Class"


def _enum_instance_local(util: JavaUtilClass, enum: JavaNestedEnum) -> str:
    return f"Java{util.simple_name}{enum.simple_name}"


def _enum_class_local(util: JavaUtilClass, enum: JavaNestedEnum) -> str:
    return f"{_enum_instance_local(util, enum)}Class"


def _description(value: str | list[str] | None) -> list[str]:
    if value is None:
        return []
    values = [value] if isinstance(value, str) else value
    return [f"---{line}" if line else "---" for line in values]


def _quote(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'
