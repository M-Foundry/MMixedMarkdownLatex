# 场景驱动接口检查与补充计划

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 从宿主开发者的具体场景检查 M3L 接口，仅补无法通过现有入口完成的交互扩展。

**Architecture:** 保留现有 `MarkdownStyle`、`MarkdownComponents` 和原生钩子。只在行内链接图标与图片交互处增加必要配置，其他场景优先复用既有接口。

**Tech Stack:** Kotlin、Compose、Markwon、Android TextView。

**Spec:** 用户要求多轮假设使用场景、寻找接口、补缺口、尽量少改内部实现。

## Global Constraints

- 不添加代码注释、测试代码、测试依赖或发行包。
- 保留未提交改动、内部版本号 1.0.1 和 Apache-2.0 许可证。
- 更新中英文 README 与中文 changelog。

## Review Focus

- 链接尾部图标保持原文复制、长链接换行和双向文本顺序。
- 同地址多张图片能按渲染位置分别响应点击和动画。
- 图片动画不引发 Coil 重载，位移在预留空间内不被裁切。
- 样式与回调变化后新旧 span 不混用。

## Tasks

- [x] 分轮核对链接、图片、代码块、引用、公式和表格的宿主需求与现有入口。
- [x] 补链接图标位置和图片定位点击、按帧位移入口。
- [x] 更新双语 README、中文 changelog，并编译、检查依赖与 diff。

## 核对结果

- 链接尾部箭头和单张图片上下移动原先缺少直接入口，本次已补。
- 代码块工具栏、引用按块装饰、公式宏变换与块级点击、表格逐列样式可使用既有接口。
- `compileDebugKotlin verifyRendererDependencies --offline` 通过；设备视觉效果尚未检查。
