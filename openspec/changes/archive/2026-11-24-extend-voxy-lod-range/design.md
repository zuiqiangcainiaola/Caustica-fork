## Context

第一阶段 `VoxyWorldTerrain.radius` 将半径截在原版视距加 16 区块，`RtTerrain.syncVoxyWindow` 按列枚举整个高度。直接扩大这个方形窗口在最大设置下会需要上亿个分段。

Voxy 0.2.19-beta 的 `VoxyConfigMenu` 按滑块整数除以 16 写入 `sectionRenderDistance`，界面显示该整数的两倍。官方遍历器以配置值乘 512 得到水平半径（方块），存储层级 0 至 4 的体素边长分别为 1、2、4、8、16 格。应用设置后读取内存中的生效值。

## Decisions

在已有数据上做分层选择，近处继续使用既有分段，远处以较大的空间单元构网。相邻覆盖按实际驻留交接，使反射与阴影共享主射线可用的世界几何。记录目标水平距离、世界坐标和实际读取的层级。区分太阳直射被遮挡、水体反射、环境光和曝光差异。

### 实施细节

`VoxyWorldTerrain` 从已记录的 L4 区域沿非空子节点发现地形，后台读取层级数据。`RtTerrain` 以 64×64×64 格为单位调度远景，按玩家到瓦片中心的水平距离选择 L1、L2、L3 或 L4。分界为 192、384 和 768 格；优先读取所选层，缺页时查询可用层。视距外缘按水平圆形裁切。近处原版区块和交界细分段就绪后按 16³ 格区域接管覆盖，替换完成前保留现有可见几何。

所有射线使用同一个 Vulkan 加速结构。远景命中时检查近景占用掩码，按现有简化材质着色。原来的 10000 格世界射线长度小于 Voxy 最大半径 32768 格，主射线、反弹、太阳直射和反射引导改用可横跨可见区域的 66000 格上限。水体及大气体积积分仍使用各自原有的距离范围。

视距换算核对了 Voxy 0.2.19-beta 的滑块、显示格式、运行时统一变量和遍历器。[界面与设置写入](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/config/VoxyConfigMenu.java#L108-L123) · [区块单位](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/iris/VoxyUniforms.java#L46) · [方块半径](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/core/rendering/hierachical/HierarchicalOcclusionTraverser.java#L229)

## Validation

保留旧 JAR，先在 OSHACRA ISLAND 副本以固定晴天、时间、视距、位置和朝向记录两组开关 Voxy 对照。水面至少含一对地标本体已出画的画面。太阳阴影在近景同一受光点及相邻参照处对比。实施后在旧机位复测，并对旧上限之外的地标重复全部对照。缺少数据、未命中或图像不可判读时，保留证据并注明限制。
