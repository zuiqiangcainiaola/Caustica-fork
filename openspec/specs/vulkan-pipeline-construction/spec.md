# vulkan-pipeline-construction Specification

## Purpose

约定 Caustica 创建 Vulkan 管线时的着色器资源归属、HDR 与 SDR 呈现的绑定和调度行为，以及验证这些行为所用的测试依赖。

## Requirements

### Requirement: 着色器模块与资源归属

渲染器 SHALL 从类路径 `/caustica/rt/` 加载原有 SPIR-V 文件，保留缺失资源路径和读取异常原因，并在管线创建结束时释放该次调用取得的临时模块和原生代码缓冲。

#### Scenario: 多阶段管线部分失败

- **WHEN** 一次世界光追或覆盖层管线创建已成功加载部分模块，后续读取、模块创建或管线创建失败
- **THEN** 调用方收到失败，已经取得的临时着色器模块各释放一次

#### Scenario: 管线正常创建

- **WHEN** 显示、曝光、呈现、光追或覆盖层管线成功创建
- **THEN** 着色器名称、阶段与布局沿用原有约定，返回的管线可在临时模块释放后使用

### Requirement: HDR 界面合成与 SDR 呈现

两个呈现入口 SHALL 各自使用原有着色器和调试标签，独立持有资源并保留现有创建、设置图像、使绑定失效、调度与销毁方法。

#### Scenario: 同一帧中的不同呈现操作

- **WHEN** 调用 HDR 界面合成或 SDR 菜单转换
- **THEN** 对应实例在绑定 0 写入存储图像，在绑定 1 读取带采样器的图像，两者均采用 GENERAL 布局；以偏移 0 的单个 float 传入纸白亮度，按宽和高分别向上取整到 16×16 工作组

#### Scenario: 图像重建与重复调用

- **WHEN** 同一实例连续设置相同图像视图和采样器
- **THEN** 可复用之前的绑定；显式失效后即使句柄数值相同也必须重写，另一个实例的绑定不受影响

#### Scenario: 管线销毁

- **WHEN** 所有者多次销毁同一呈现管线
- **THEN** 该实例持有的管线、布局和描述符资源仅释放一次

### Requirement: 测试依赖边界

Gradle SHALL 以现有 BOM 版本编译 Jupiter API 测试并在运行时提供 Jupiter 引擎与平台启动器。

#### Scenario: 发现现有 JUnit 测试

- **WHEN** Gradle 执行 test 任务
- **THEN** 现有 Jupiter 测试可被发现和执行，模组运行时依赖声明保持原样
