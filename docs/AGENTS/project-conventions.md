# 项目约定

## i18n 翻译系统

- `TranslateComponent`：翻译组件接口
- `EpsilonTranslateComponent.create("modules", "kill_aura")` -> key: `epsilon.modules.kill_aura`
- `DefaultTranslateComponent.create("example_addon.settings.enable_particles")` -> key: `example_addon.settings.enable_particles`
- 翻译 key 生成在 `I18NFileGenerator.generate("epsilon-empty-i18n.json")`

### Module 翻译 key 约定

- 内置模块：`epsilon.modules.{moduleNameLowerCase}`
- Addon 模块：`{addonId}.modules.{moduleNameLowerCase}`
- Setting 翻译：`{addonId}.settings.{settingNameLowerCase}`
- SettingGroup 翻译：`{addonId}.settings.{groupNameLowerCase}`

## 代码规范

1. 使用 Java 编写，遵循项目现有代码风格
2. 私有构造函数 + 单例 `INSTANCE` 模式用于 Module 和 Addon
3. 所有注释使用中文
4. Logger 通过 `Constants.LOGGER` 获取
5. Minecraft 实例通过 `Constants.mc` 或 Module 中的 `this.mc` 获取
6. 编译环境使用 `JDK 25`

## 查阅文档资源

- NeoForge 官方文档：https://docs.neoforged.net/
- NeoForge 迁移入门：https://docs.neoforged.net/primer/docs/
- Porting Primers（中文）：https://gu-zt.github.io/Porting-Primers/
- Mixin 文档：https://wiki.fabricmc.net/zh_cn:tutorial:mixin_introduction
- Fabric API 文档：https://docs.fabricmc.net/develop/
- 项目自身 graphics 文档：`common/src/main/java/com/github/epsilon/graphics/README.md`
