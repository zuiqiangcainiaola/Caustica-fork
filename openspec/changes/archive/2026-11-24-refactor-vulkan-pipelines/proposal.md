## Why

世界光追、覆盖层、显示、曝光、HDR 合成和 SDR 呈现各自实现同一套 SPIR-V 资源读取与模块创建。若后续着色阶段加载失败，已经创建的模块可能跳过释放。两个呈现类还逐行重复描述符更新、调度与销毁逻辑。一次调整需要同步多处，容易漏掉图像句柄复用时的重新绑定。

## What Changes

- 集中着色器模块的加载和释放，由每次创建管线的调用持有自己的临时模块。
- 让 HDR 界面合成和 SDR 菜单呈现复用一份实现，分别保留入口、着色器、调试名称和实例状态。
- 按现有测试实际使用的接口声明 JUnit 编译与运行依赖。

## Capabilities

### New Capabilities

- `vulkan-pipeline-construction` 记录现有资源、呈现绑定和调度约定，以及部分创建失败时的模块释放要求。

### Modified Capabilities

无已有 OpenSpec 条目。

## Impact

涉及 Java 渲染管线、描述符回归测试与 Gradle 测试配置。游戏版本、着色器、配置格式、Fabric 和 NGX 的运行时依赖沿用当前设置。
