# 脚本 i18n

每个脚本包可以在 `lang/` 中提供语言 JSON：

```text
example-suite/
└── lang/
    ├── en_us.json
    └── zh_cn.json
```

语言文件只负责当前包的名称、说明、SettingGroup、Setting、choice 和 Module 文案。脚本管理 UI 的按钮、状态和
错误标题由 Epsilon 自身语言资源提供。

## 完整示例

```json
{
  "_value": "示例脚本包",
  "description": "包含多个 Epsilon Lua Module",
  "groups": {
    "general": {
      "_value": "全局设置"
    }
  },
  "settings": {
    "shared range": {
      "_value": "共享距离"
    },
    "debug log": {
      "_value": "调试日志"
    }
  },
  "modules": {
    "combat-assist": {
      "_value": "战斗辅助",
      "description": "示例战斗模块",
      "groups": {
        "general": {
          "_value": "常规"
        }
      },
      "settings": {
        "attempts": {
          "_value": "尝试次数"
        },
        "mode": {
          "_value": "模式",
          "normal": "普通",
          "strict": "严格"
        }
      }
    },
    "world-overlay": {
      "_value": "世界覆盖层",
      "description": "显示世界标记"
    }
  }
}
```

`_value` 表示当前 object 自身对应 key 的翻译。例如根节点 `_value` 是包名，Module 节点的 `_value` 是 Module
名称。它不是普通路径段。

## 完整 key

loader 在内部把相对 key 加上 `lua.<packageId>` 前缀：

```text
lua.<packageId>._value
lua.<packageId>.description
lua.<packageId>.groups.<groupId>._value
lua.<packageId>.settings.<settingId>._value
lua.<packageId>.settings.<settingId>.<choiceId>
lua.<packageId>.modules.<moduleId>._value
lua.<packageId>.modules.<moduleId>.description
lua.<packageId>.modules.<moduleId>.groups.<groupId>._value
lua.<packageId>.modules.<moduleId>.settings.<settingId>._value
lua.<packageId>.modules.<moduleId>.settings.<settingId>.<choiceId>
```

包内 JSON 不要再写 `lua.<packageId>` 外层；上面的示例根节点已经对应 `lua.example-suite`。

## 格式约束

- JSON 根节点必须是 object。
- 所有叶节点必须是 string。
- 不允许数组、数字、布尔或 `null` 叶节点。
- `_value` 必须是 string。
- ID 保持原样，Setting ID 中的空格也保留，不自动转换为下划线。
- Module、Setting 和 group 名称 key 使用已经校验为小写的稳定 ID；choice 翻译 key 使用 choice 字符串的
  小写形式。

若语言文件格式错误，Epsilon 会记录包含文件路径的错误并把该语言 catalog 当作空值处理，不执行不安全的
部分解析。

## 回退顺序

翻译解析顺序为：

1. 当前选择语言的 `lang/<code>.json`。
2. `lang/en_us.json`。
3. manifest 中的 `name`/`description`，或 Module/Setting/Group 的 fallback 名称。
4. 对 ID 最后一段进行首字母格式化后的文本。

当 Epsilon 切换语言时，脚本创建的 `LuaTranslateComponent` 会与原生组件一起 refresh。当前实现不监听语言
文件变化；编辑语言文件后点击该脚本包的 Reload。

## 建议流程

先完整编写 `en_us.json`，再以相同结构编写 `zh_cn.json`。新增或删除 Module、Setting、SettingGroup 或
choice 时，同步更新两份文件，并在 Addon Panel 和 Module GUI 中人工检查回退是否被意外触发。

仓库中的实际示例：

- [en_us.json](../examples/lua/example-suite/lang/en_us.json)
- [zh_cn.json](../examples/lua/example-suite/lang/zh_cn.json)
