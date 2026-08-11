from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.lua_codegen.java_utils import JavaSourceError, discover_util_classes


class DiscoverUtilClassesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.source_root = Path(self.temporary_directory.name) / "utils"

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_discovers_only_public_top_level_utility_classes(self) -> None:
        self.write_java(
            "player/PlayerUtils.java",
            """package com.github.epsilon.utils.player;
public class PlayerUtils {
    public static class NestedUtils {}
}
""",
        )
        self.write_java(
            "render/WorldToScreen.java",
            """package com.github.epsilon.utils.render;
public final class WorldToScreen {}
""",
        )
        self.write_java(
            "player/Ignored.java",
            """package com.github.epsilon.utils.player;
class PackageUtils {}
record RecordUtils(int value) {}
enum EnumUtils { VALUE }
public class Ignored {}
""",
        )

        discovered = discover_util_classes(self.source_root)

        self.assertEqual(["PlayerUtils", "WorldToScreen"], [value.simple_name for value in discovered])
        self.assertEqual(
            "com.github.epsilon.utils.player.PlayerUtils",
            discovered[0].qualified_name,
        )

    def test_rejects_duplicate_short_names(self) -> None:
        self.write_java(
            "first/SharedUtils.java",
            "package com.github.epsilon.utils.first; public class SharedUtils {}\n",
        )
        self.write_java(
            "second/SharedUtils.java",
            "package com.github.epsilon.utils.second; public class SharedUtils {}\n",
        )

        with self.assertRaisesRegex(JavaSourceError, "Duplicate Lua utility short name SharedUtils"):
            discover_util_classes(self.source_root)

    def test_rejects_package_path_mismatch(self) -> None:
        self.write_java(
            "player/PlayerUtils.java",
            "package com.github.epsilon.utils.world; public class PlayerUtils {}\n",
        )

        with self.assertRaisesRegex(JavaSourceError, "Package/path mismatch"):
            discover_util_classes(self.source_root)

    def test_parses_complete_public_api(self) -> None:
        self.write_java(
            "sample/SampleUtils.java",
            """package com.github.epsilon.utils.sample;
import javax.annotation.Nullable;
public class SampleUtils {
    public static final int ANSWER = 42;
    public String label;
    private boolean hidden;

    public SampleUtils(long startedAt) {}
    private SampleUtils() {}

    public static double convert(float value) { return value; }
    public static String convert(String value, @Nullable Object fallback) { return value; }
    public void accept(String... values) {}
    private void ignored() {}

    public enum Mode { FIRST, SECOND }
}
""",
        )

        util_class = discover_util_classes(self.source_root)[0]

        self.assertEqual(
            [("ANSWER", "int", True, True), ("label", "String", False, False)],
            [(field.name, field.type.source, field.is_static, field.is_final) for field in util_class.fields],
        )
        self.assertEqual(
            [("long", "startedAt")],
            [
                (parameter.type.source, parameter.name)
                for parameter in util_class.constructors[0].parameters
            ],
        )
        self.assertEqual(["convert", "convert", "accept"], [method.name for method in util_class.methods])
        self.assertTrue(util_class.methods[0].is_static)
        self.assertEqual("double", util_class.methods[0].return_type.source)
        self.assertTrue(util_class.methods[1].parameters[1].type.nullable)
        self.assertTrue(util_class.methods[2].parameters[0].varargs)
        self.assertEqual(("FIRST", "SECOND"), util_class.nested_enums[0].constants)
        self.assertEqual(
            "com.github.epsilon.utils.sample.SampleUtils$Mode",
            util_class.nested_enums[0].binary_name,
        )

    def test_adds_only_accessible_implicit_constructor(self) -> None:
        self.write_java(
            "sample/ImplicitUtils.java",
            "package com.github.epsilon.utils.sample; public class ImplicitUtils {}\n",
        )
        self.write_java(
            "sample/PrivateUtils.java",
            "package com.github.epsilon.utils.sample; public class PrivateUtils { private PrivateUtils() {} }\n",
        )

        discovered = {value.simple_name: value for value in discover_util_classes(self.source_root)}

        self.assertEqual(1, len(discovered["ImplicitUtils"].constructors))
        self.assertTrue(discovered["ImplicitUtils"].constructors[0].implicit)
        self.assertEqual((), discovered["PrivateUtils"].constructors)

    def write_java(self, relative_path: str, source: str) -> None:
        path = self.source_root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    unittest.main()
