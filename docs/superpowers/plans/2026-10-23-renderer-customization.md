# 渲染器扩展接口实施计划

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 开放已审查的全部渲染样式、插槽、组件替换和高级扩展入口，并更新双语 README。

**Architecture:** MarkdownStyle 聚合各组件样式，MarkdownComponents 提供按块上下文解析样式、插槽和默认渲染装饰入口。CompositionLocal 传递递归配置，保留现有入口和默认外观。

**Tech Stack:** Kotlin 2.0.21、Compose 1.7.6、Markwon 4.6.2、TextMate。

**Spec:** 本会话接口审查清单及用户“全部开放，记得更新readme”的指令。

## Global Constraints

- 不添加代码注释、测试代码、测试依赖或发行包。
- 保留现有未提交的选择手柄着色改动和 changelog 内容。
- 不引入 Material、宿主交互或额外运行依赖。

## Review Focus

- 样式动态变化后原生 spans、高亮及公式缓存及时刷新。
- 表格单元格和嵌套引用继承配置和组件路径。
- 自定义字体与字号变化保持代码行号基线对齐。
- 组件替换可调用默认内容且不触发自身无限递归。
- 链接图标和图片扩展保留可选择文本及链接处理。

## Tasks

- [x] 定义聚合样式、块上下文、插槽、组件替换和单块覆盖接口。
- [x] 实现链接、排版、选区、行内代码、图片与 Markwon 扩展配置。
- [x] 实现代码块样式、调色板、高亮规则、语言别名和语法来源接口。
- [x] 实现公式颜色、字号、对齐、间距、加载与失败插槽。
- [x] 接通引用、表格、分隔线和递归配置传递。
- [x] 更新中英文 README、中文 changelog，并完成编译和依赖检查。

## 验证结果

- `compileDebugKotlin verifyRendererDependencies --offline` 通过。
- 交叉审查后修正高亮透明度、动态原生样式残留、标题范围与圆角图片宽度问题。
- 保留既有 AGP 对 compileSdk 36 的兼容性提示和 Gradle 弃用提示。
- 未添加测试代码或生成发行包；尚未进行 Android 设备上的视觉验收。
