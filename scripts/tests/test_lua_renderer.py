from __future__ import annotations

import unittest
from pathlib import Path

from scripts.lua_codegen.java_utils import (
    JavaConstructor,
    JavaField,
    JavaMethod,
    JavaNestedEnum,
    JavaParameter,
    JavaType,
    JavaUtilClass,
)
from scripts.lua_codegen.lua_renderer import render_library


class LuaRendererTest(unittest.TestCase):
    def test_renders_typed_util_members_overloads_and_constructors(self) -> None:
        util_class = JavaUtilClass(
            simple_name="SampleUtils",
            qualified_name="com.github.epsilon.utils.SampleUtils",
            source_path=Path("SampleUtils.java"),
            fields=(
                JavaField("COUNT", JavaType("int"), True, True),
                JavaField("values", JavaType("String[]"), False, False),
            ),
            constructors=(
                JavaConstructor((JavaParameter("name", JavaType("String")),)),
            ),
            methods=(
                JavaMethod((), "read", JavaType("String", nullable=True), True),
                JavaMethod((JavaParameter("value", JavaType("int")),), "read", JavaType("long"), True),
                JavaMethod(
                    (JavaParameter("values", JavaType("String"), varargs=True),),
                    "accept",
                    JavaType("void"),
                    False,
                ),
            ),
            nested_enums=(
                JavaNestedEnum(
                    "Mode",
                    "com.github.epsilon.utils.SampleUtils.Mode",
                    "com.github.epsilon.utils.SampleUtils$Mode",
                    ("FIRST", "SECOND"),
                ),
            ),
        )
        model = {
            "aliases": [{"name": "EpsilonUtilClassName", "dynamic": "util_classes"}],
            "classes": [
                {
                    "name": "EpsilonLuaJava",
                    "local": "LuaJava",
                    "methods": [
                        {
                            "name": "bindUtilClass",
                            "call": "dot",
                            "dynamic_overloads": "bind_util_class",
                            "parameters": [{"name": "utilName", "type": "EpsilonUtilClassName"}],
                        },
                        {
                            "name": "new",
                            "call": "index",
                            "dynamic_overloads": "construct_from_class",
                            "parameters": [
                                {"name": "javaClass", "type": "userdata"},
                                {"name": "...", "type": "any"},
                            ],
                        },
                    ],
                }
            ],
            "globals": [],
        }

        rendered = render_library(model, {"util_classes": ["SampleUtils"]}, [util_class])

        self.assertIn(
            '---@overload fun(utilName: "SampleUtils"): EpsilonJavaSampleUtilsClass',
            rendered,
        )
        self.assertIn(
            "---@overload fun(javaClass: EpsilonJavaSampleUtilsClass, name: string): EpsilonJavaSampleUtils",
            rendered,
        )
        self.assertIn("---@field readonly COUNT integer Java type: `int`.", rendered)
        self.assertIn("---@field values userdata Java type: `String[]`.", rendered)
        self.assertIn(
            "---@overload fun(self: EpsilonJavaSampleUtilsClass, value: integer): integer",
            rendered,
        )
        self.assertIn("---@return string|nil value Java type: `String`.", rendered)
        self.assertIn("---@param ... string Java type: `String...`.", rendered)
        self.assertIn("function JavaSampleUtils:accept(...) end", rendered)
        self.assertIn("---@field readonly FIRST EpsilonJavaSampleUtilsMode", rendered)


if __name__ == "__main__":
    unittest.main()
