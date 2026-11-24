## Why

Caustica 在光追帧接管原版世界绘制，近处已有光追几何。玩家存档中的 Voxy 远景当前无法出现在该帧中。第一阶段要解决的是已记录地形在原版视距外的可见性，以及它与近景交界处的遮挡。

## What Changes

- 目标组合为 Minecraft 26.2、Fabric、可选的 Voxy 0.2.19-beta。Voxy 和 Sodium 由玩家另外安装。
- 远景接入必须与 Caustica 的相机、覆盖范围和时间重建一致；保留无 Voxy 时的世界、手部和 HUD 绘制。
- 项目所有者选择数据侧接入。使用 Voxy 已记录的体素，为 Caustica 增加远景几何与驻留管理。

## Capabilities

### New Capabilities

- `voxy-distant-terrain` 定义第一阶段可见性、遮挡、过渡与可选依赖。

### Modified Capabilities

无。沿用上次重构的 Vulkan 管线资源约定。

## Impact

新增 Voxy 可选编译依赖、Vulkan 下的存储初始化、远景网格和光追材质分支。Caustica JAR 沿用既有安装方式。Voxy、Sodium 及测试存档由运行环境提供。
