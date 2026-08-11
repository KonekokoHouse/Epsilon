from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

import tree_sitter_java
from tree_sitter import Language, Node, Parser


JAVA_LANGUAGE = Language(tree_sitter_java.language())
EXTRA_UTIL_CLASS_NAMES = frozenset({"WorldToScreen"})


class JavaSourceError(RuntimeError):
    pass


@dataclass(frozen=True)
class JavaType:
    source: str
    nullable: bool = False


@dataclass(frozen=True)
class JavaParameter:
    name: str
    type: JavaType
    varargs: bool = False


@dataclass(frozen=True)
class JavaField:
    name: str
    type: JavaType
    is_static: bool
    is_final: bool


@dataclass(frozen=True)
class JavaExecutable:
    parameters: tuple[JavaParameter, ...]


@dataclass(frozen=True)
class JavaConstructor(JavaExecutable):
    implicit: bool = False


@dataclass(frozen=True)
class JavaMethod(JavaExecutable):
    name: str
    return_type: JavaType
    is_static: bool


@dataclass(frozen=True)
class JavaNestedEnum:
    simple_name: str
    qualified_name: str
    binary_name: str
    constants: tuple[str, ...]


@dataclass(frozen=True)
class JavaUtilClass:
    simple_name: str
    qualified_name: str
    source_path: Path
    fields: tuple[JavaField, ...] = ()
    constructors: tuple[JavaConstructor, ...] = ()
    methods: tuple[JavaMethod, ...] = ()
    nested_enums: tuple[JavaNestedEnum, ...] = ()


def discover_util_classes(source_root: Path) -> list[JavaUtilClass]:
    source_root = source_root.resolve()
    parser = Parser(JAVA_LANGUAGE)
    discovered: list[JavaUtilClass] = []

    for source_path in sorted(source_root.rglob("*.java")):
        source = source_path.read_bytes()
        tree = parser.parse(source)
        if tree.root_node.has_error:
            raise JavaSourceError(f"Java parse error: {source_path}")

        package_name = _package_name(tree.root_node, source, source_path)
        expected_package = _expected_package(source_root, source_path)
        if package_name != expected_package:
            raise JavaSourceError(
                f"Package/path mismatch in {source_path}: {package_name!r} != {expected_package!r}"
            )

        for declaration in tree.root_node.named_children:
            if declaration.type != "class_declaration" or not _has_modifier(declaration, source, "public"):
                continue
            name_node = declaration.child_by_field_name("name")
            if name_node is None:
                raise JavaSourceError(f"Public class without a name: {source_path}")
            simple_name = _node_text(name_node, source)
            if not _is_lua_util(simple_name):
                continue
            discovered.append(
                _parse_util_class(
                    declaration,
                    source,
                    source_path,
                    package_name,
                    simple_name,
                )
            )

    if not discovered:
        raise JavaSourceError(f"No Lua utility classes found under {source_root}")

    by_name: dict[str, JavaUtilClass] = {}
    for util_class in discovered:
        previous = by_name.setdefault(util_class.simple_name, util_class)
        if previous != util_class:
            raise JavaSourceError(
                f"Duplicate Lua utility short name {util_class.simple_name}: "
                f"{previous.source_path} and {util_class.source_path}"
            )
    return sorted(by_name.values(), key=lambda value: value.simple_name)


def _parse_util_class(
    declaration: Node,
    source: bytes,
    source_path: Path,
    package_name: str,
    simple_name: str,
) -> JavaUtilClass:
    body = declaration.child_by_field_name("body")
    if body is None:
        raise JavaSourceError(f"Class without a body: {source_path}")

    fields: list[JavaField] = []
    constructors: list[JavaConstructor] = []
    methods: list[JavaMethod] = []
    nested_enums: list[JavaNestedEnum] = []
    has_declared_constructor = False

    for member in body.named_children:
        if member.type == "constructor_declaration":
            has_declared_constructor = True
            if _has_modifier(member, source, "public"):
                constructors.append(_parse_constructor(member, source, source_path))
        elif member.type == "field_declaration" and _has_modifier(member, source, "public"):
            fields.extend(_parse_fields(member, source, source_path))
        elif member.type == "method_declaration" and _has_modifier(member, source, "public"):
            methods.append(_parse_method(member, source, source_path))
        elif member.type == "enum_declaration" and _has_modifier(member, source, "public"):
            nested_enums.append(
                _parse_nested_enum(member, source, source_path, package_name, simple_name)
            )

    if not has_declared_constructor and not _has_modifier(declaration, source, "abstract"):
        constructors.append(JavaConstructor((), implicit=True))

    return JavaUtilClass(
        simple_name=simple_name,
        qualified_name=f"{package_name}.{simple_name}",
        source_path=source_path,
        fields=tuple(fields),
        constructors=tuple(constructors),
        methods=tuple(methods),
        nested_enums=tuple(nested_enums),
    )


def _parse_fields(declaration: Node, source: bytes, source_path: Path) -> list[JavaField]:
    type_node = declaration.child_by_field_name("type")
    if type_node is None:
        raise JavaSourceError(f"Public field without a type: {source_path}")
    field_type = _java_type(type_node, declaration, source)
    fields: list[JavaField] = []
    for declarator in (node for node in declaration.named_children if node.type == "variable_declarator"):
        name_node = declarator.child_by_field_name("name")
        if name_node is None:
            raise JavaSourceError(f"Public field without a name: {source_path}")
        fields.append(
            JavaField(
                name=_node_text(name_node, source),
                type=field_type,
                is_static=_has_modifier(declaration, source, "static"),
                is_final=_has_modifier(declaration, source, "final"),
            )
        )
    if not fields:
        raise JavaSourceError(f"Public field without a declarator: {source_path}")
    return fields


def _parse_constructor(declaration: Node, source: bytes, source_path: Path) -> JavaConstructor:
    return JavaConstructor(_parse_parameters(declaration, source, source_path))


def _parse_method(declaration: Node, source: bytes, source_path: Path) -> JavaMethod:
    name_node = declaration.child_by_field_name("name")
    type_node = declaration.child_by_field_name("type")
    if name_node is None or type_node is None:
        raise JavaSourceError(f"Public method without a name or return type: {source_path}")
    return JavaMethod(
        parameters=_parse_parameters(declaration, source, source_path),
        name=_node_text(name_node, source),
        return_type=_java_type(type_node, declaration, source),
        is_static=_has_modifier(declaration, source, "static"),
    )


def _parse_parameters(declaration: Node, source: bytes, source_path: Path) -> tuple[JavaParameter, ...]:
    parameters_node = declaration.child_by_field_name("parameters")
    if parameters_node is None:
        raise JavaSourceError(f"Executable without parameters node: {source_path}")
    parameters: list[JavaParameter] = []
    for parameter in parameters_node.named_children:
        if parameter.type not in {"formal_parameter", "spread_parameter"}:
            continue
        if parameter.type == "spread_parameter":
            declarator = next(
                (node for node in parameter.named_children if node.type == "variable_declarator"),
                None,
            )
            name_node = declarator.child_by_field_name("name") if declarator is not None else None
            type_node = next(
                (
                    node
                    for node in parameter.named_children
                    if node.type not in {"modifiers", "variable_declarator"}
                ),
                None,
            )
        else:
            name_node = parameter.child_by_field_name("name")
            type_node = parameter.child_by_field_name("type")
        if name_node is None or type_node is None:
            raise JavaSourceError(f"Parameter without a name or type: {source_path}")
        parameters.append(
            JavaParameter(
                name=_node_text(name_node, source),
                type=_java_type(type_node, parameter, source),
                varargs=parameter.type == "spread_parameter",
            )
        )
    return tuple(parameters)


def _parse_nested_enum(
    declaration: Node,
    source: bytes,
    source_path: Path,
    package_name: str,
    owner_name: str,
) -> JavaNestedEnum:
    name_node = declaration.child_by_field_name("name")
    body = declaration.child_by_field_name("body")
    if name_node is None or body is None:
        raise JavaSourceError(f"Public nested enum without a name or body: {source_path}")
    simple_name = _node_text(name_node, source)
    constants = tuple(
        _node_text(name, source)
        for child in body.named_children
        if child.type == "enum_constant"
        for name in [child.child_by_field_name("name")]
        if name is not None
    )
    qualified_name = f"{package_name}.{owner_name}.{simple_name}"
    return JavaNestedEnum(
        simple_name=simple_name,
        qualified_name=qualified_name,
        binary_name=f"{package_name}.{owner_name}${simple_name}",
        constants=constants,
    )


def _java_type(type_node: Node, declaration: Node, source: bytes) -> JavaType:
    raw_type = _node_text(type_node, source)
    normalized = re.sub(r"\s+", " ", raw_type).strip()
    normalized = re.sub(r"\s*([<>,?&\[\]])\s*", r"\1", normalized)
    declaration_prefix = source[declaration.start_byte:type_node.start_byte].decode("utf-8")
    nullable = bool(re.search(r"@(?:[A-Za-z_][\w.]*\.)?Nullable\b", declaration_prefix + raw_type))
    return JavaType(normalized, nullable)


def _package_name(root: Node, source: bytes, source_path: Path) -> str:
    declarations = [node for node in root.named_children if node.type == "package_declaration"]
    if len(declarations) != 1:
        raise JavaSourceError(f"Expected one package declaration: {source_path}")
    text = _node_text(declarations[0], source)
    return text.removeprefix("package").removesuffix(";").strip()


def _expected_package(source_root: Path, source_path: Path) -> str:
    relative_parent = source_path.relative_to(source_root).parent
    suffix = ".".join(relative_parent.parts)
    return "com.github.epsilon.utils" + (f".{suffix}" if suffix else "")


def _has_modifier(declaration: Node, source: bytes, modifier: str) -> bool:
    modifiers = next((node for node in declaration.children if node.type == "modifiers"), None)
    if modifiers is None:
        return False
    return bool(re.search(rf"\b{re.escape(modifier)}\b", _node_text(modifiers, source)))


def _is_lua_util(simple_name: str) -> bool:
    return simple_name.endswith("Utils") or simple_name in EXTRA_UTIL_CLASS_NAMES


def _node_text(node: Node, source: bytes) -> str:
    return source[node.start_byte:node.end_byte].decode("utf-8")
