# 渲染系统

## Lumin Graphics

Lumin Graphics 是 Epsilon 的轻量高性能渲染框架，位于 `common/.../graphics/`。

## 核心 Renderer

| Renderer | 用途 |
|----------|------|
| `RectRenderer` | 矩形渲染（纯色、渐变） |
| `RoundRectRenderer` | SDF 圆角矩形 |
| `RoundRectOutlineRenderer` | 圆角矩形边框 |
| `TextureRenderer` | 纹理批量渲染 |
| `ShadowRenderer` | 阴影渲染 |
| `TriangleRenderer` | 三角形渲染 |
| `TextRenderer` | 文本渲染（静态字体 + TTF） |
| `TtfTextRenderer` | TrueType 字体渲染 |

## Renderer 生命周期

1. Renderer 必须在渲染线程初始化
2. 推荐使用 `Suppliers.memoize` 延迟初始化

```java
private final Supplier<RectRenderer> rectRenderer = Suppliers.memoize(RectRenderer::create);
```

### 使用模式一：即时绘制并清理

```java
rectRenderer.get().addRect(10, 10, 100, 100, Color.WHITE);
rectRenderer.get().drawAndClear();
```

### 使用模式二：缓冲区复用

```java
rectRenderer.get().addRect(10, 10, 100, 100, Color.WHITE);
rectRenderer.get().draw();
```

### 关闭资源

```java
rectRenderer.get().close();
```

## 重要约束

同一帧内，不要在 `draw()` 之后再 `clear()` 然后继续 `draw()`。这会导致帧内多次缓冲区分配，破坏 In-Flight 优化。如需多次清空后再次绘制，请创建新的 Renderer 实例。

## 文本渲染

静态字体加载器示例：`StaticFontLoader.OSAKA_CHIPS`、`StaticFontLoader.MINECRAFTIA`。

```java
TextRenderer textRenderer = textRendererSupplier.get();
textRenderer.addText("Hello", x, y, scale, color, StaticFontLoader.OSAKA_CHIPS);
float width = textRenderer.getWidth("Hello", scale);
float height = textRenderer.getHeight(scale);
textRenderer.drawAndClear();
```

## 特效

- `BlurShader.INSTANCE.render(x, y, width, height, radius, strength)`：背景模糊
- `FXAAShader`：FXAA 抗锯齿
- `FilterShader`：色彩滤镜

## 参考示例

完整 HUD 渲染示例：`common/.../modules/impl/hud/WatermarkHUD.java`

项目自身 graphics 文档：`common/src/main/java/com/github/epsilon/graphics/README.md`
