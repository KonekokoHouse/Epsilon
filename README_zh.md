<h1 align="center">Epsilon</h1>
<h4 align="center">
    <p>
        <a href="./README.md">English</a> |
        <b>中文</b>
    </p>
</h4>

<p align="center">
  <a href="https://github.com/NekoyaHouse/Epsilon/actions"><img alt="构建" src="https://img.shields.io/badge/build-gradle-4c1?style=flat-square"></a>
  <a href="LICENSE"><img alt="许可证" src="https://img.shields.io/badge/license-GPLv3-blue?style=flat-square"></a>
  <img alt="加载器" src="https://img.shields.io/badge/loaders-NeoForge%20%26%20Fabric-6a5acd?style=flat-square">
  <a href="https://discord.gg/vYbaae3X7e"><img alt="Discord" src="https://img.shields.io/badge/Discord-加入社区-5865F2?style=flat-square&logo=discord&logoColor=white"></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/WPvwQZvYci"><img alt="QQ 一群" src="https://img.shields.io/badge/QQ%20%E4%B8%80%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
  <a href="https://qm.qq.com/q/3hhg8ww9ag"><img alt="QQ 二群" src="https://img.shields.io/badge/QQ%20%E4%BA%8C%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
</p>

> [!IMPORTANT]
> ## 开发暂缓公告
> Epsilon 目前将暂缓开发。在此期间，官方将减缓对战斗类功能的维护，但仍然欢迎提交 **Pull Request**。
>
> 本仓库中已经发布的代码仍遵循 [GNU General Public License v3.0](LICENSE)。本公告不会改变现有版本的许可证或既有权利。

## 📌 项目简介
基于 NeoForge & Fabric 构建的多加载器现代化 Minecraft 辅助客户端，拥有先进的渲染系统和模块化架构。

## 🚀 插件系统
[Epsilon 插件模板](https://github.com/slmpc/Epsilon-Addon-Template)

[Addon 开发文档](docs/addon-development.md)

## 🎨 渲染系统

图形技术栈由以下开源仓库共同构成：

- [LuminGraphics](https://github.com/slmpc/LuminGraphics)
- [LuminGraphics-MC](https://github.com/slmpc/LuminGraphics-MC)
- [PrismRHI](https://github.com/slmpc/PrismRHI)

Lumin 渲染系统通过 LuminGraphics-MC 接入 Minecraft，并由 PrismRHI 提供底层支持。它提供自定义渲染管线，支持：
- 矩形与圆角矩形
- 阴影与模糊效果
- TTF 字体渲染
- 纹理渲染
- 自定义顶点格式

基于 Lumin 的声明式 UI 层见 [Epsilon GUI Library 文档](docs/gui-library.md)。

## ⚙️ 构建与运行

```bash
# 构建模组
./gradlew build

# 运行客户端
./gradlew runClient
```

## 🐍 Python 开发工具

仓库维护与代码生成脚本使用 [uv](https://docs.astral.sh/uv/)。Python 依赖声明在 `pyproject.toml` 中，并由
`uv.lock` 锁定：

```bash
uv sync --frozen
uv run scripts/dev.py verify
```

完整的代码生成流程、生成产物、发现规则和测试命令见 [scripts 说明](scripts/README.md)。

## 🙏 鸣谢

感谢以下项目。第三方代码归属信息详见 [NOTICE](NOTICE.md)。
- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client)
- [Orbit](https://github.com/MeteorDevelopment/orbit)
- [LeavesHack](https://github.com/MrBZBZ/LeavesHack)
- [TrollHack](https://github.com/Luna5ama/TrollHack)

## 📝 许可证

Epsilon 遵循 [GNU General Public License v3.0](LICENSE) 许可证。
[LuminGraphics](https://github.com/slmpc/LuminGraphics)、[LuminGraphics-MC](https://github.com/slmpc/LuminGraphics-MC) 和 [PrismRHI](https://github.com/slmpc/PrismRHI) 是分别发布的独立项目，遵循 `LGPL-3.0-only` 许可证。

---

版权所有 © 2026 NekoyaHouse.
