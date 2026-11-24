## 1. 实现

- [x] 1.1 合并六处模块加载，覆盖多阶段创建失败的临时模块释放。
- [x] 1.2 合并两个呈现入口的内部实现，保留独立状态与公开方法。
- [x] 1.3 收窄 JUnit 依赖并更新描述符回归检查。

## 2. 验证

- [x] 2.1 严格校验 OpenSpec 变更，核对调用路径和着色器绑定约定。
- [x] 2.2 执行可用的构建与回归测试，记录与基线的差异及未验证范围。

## 3. 验证记录

- Windows x64、JDK 25、Vulkan SDK 1.4.350.0、DLSS SDK v310.7.0。通过 CMake 构建 Release NGX shim 后运行 `gradlew.bat build`，71 项 JUnit 测试全部通过。模组 JAR 包含 22 个已通过 `spirv-val` 的着色器、3 个 Windows 原生库和原有的两个 NightConfig 依赖。
- 在 RTX 4060 Laptop GPU 上启用 Khronos 验证层。先在同一个作用域加载两个实际呈现模块，再在独立作用域加载显示模块并故意请求缺失文件。错误带有资源路径，验证层在设备销毁前后共报告 0 个错误。
- `python scripts/test_parallax.py` 的 35 项生产 Slang 计算检查通过。
- 天气和水体 PowerShell 脚本在改动前后分别报告 60 和 13 项失败，包含旧版本号、天气参数和水体布局断言。当前验证覆盖构建、绑定失效契约与实际设备上的模块生命周期。Minecraft 中的 HDR/SDR 画面尚未做目视对比。
