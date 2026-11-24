## Context

### 版本和依赖

Modrinth 按游戏版本 26.2 与 Fabric 过滤，最新匹配为 `0.2.19-beta`（版本 ID `LzyXnE51`）。已打开发布 JAR，其 `fabric.mod.json` 的 `custom.commit` 指向 `02dfb1b7a91cddd02891a057cdd38478ea195c26`。以下源码以该提交为准。JAR 声明 Fabric Loader ≥0.14.22、Fabric API ≥0.91.1，Sodium 匹配 0.9.1 或元数据允许的 0.9.2 范围；发布页关联的是 26.2 Fabric 的 Sodium 0.9.1。此 fork 固定 Minecraft 26.2、Loader 0.19.3。[Voxy 发布](https://modrinth.com/mod/voxy/version/LzyXnE51) · [Sodium 发布](https://modrinth.com/mod/sodium/version/2Yom1N68)

### Caustica 的一帧

1. `GameRendererMixin.caustica$captureLevelProjection` 取得本帧带视角晃动的投影、视角旋转和相机世界坐标。主光线的抖动在着色器里施加。
2. RT 就绪时，`LevelRendererMixin.caustica$cancelVanillaWorld` 在 `LevelRenderer.render` 入口取消原版绘制。手部绘制前，`WorldRenderScaler.end` 调用 `RtComposite.composite`。
3. `RtComposite.recordFrame` 以 `terrain.staticInstances()` 和实体构建 TLAS，再执行两次 `active.trace`。`world_core.slang` 在 set 0、binding 5 声明 R32F 存储图像 `gDepth`。`guides.slang:writeGuides` 将相机相对位置投影为 reversed-Z 的 z/w（近大远小），并生成运动向量。透明折射可改用透射目标深度，因此该图不能普遍充当最前表面掩码。
4. DLSS-RR 或回退放大路径消费 RT 输出，显示管线执行曝光和映射，再向主颜色目标拷贝；手部、UI、HDR 呈现在后面。把 Voxy 画进原版颜色目标可能遇到原版阶段被取消，或被此处拷贝覆盖。

### Voxy 的数据与绘制

1. 图形初始化：`MixinRenderSystem` 在 `initRenderer` 返回时调用 `VoxyClient.initVoxyClient`。它初始化依赖 `GL.getCapabilities()` 的能力探测，并在支持时创建共享 GL 索引缓冲、注册 `VoxyClientInstance` 工厂。只关远景绘制仍需处理这条启动链。[入口](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinRenderSystem.java) · [客户端](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/VoxyClient.java) · [能力判定](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/core/gl/Capabilities.java)
2. 数据：会话启动通过工厂创建实例。单机数据库位于存档的 `voxy` 子目录。用当前世界的 `WorldIdentifier` 找到引擎，`WorldEngine.acquireIfExists(lod,x,y,z)` 取得已有的 32³ 体素段，`WorldSection.copyData()` 可复制数据，之后须 `release()`。引擎的 `Mapper` 负责解释方块、群系和光照 ID。跨线程传递需考虑引擎引用和退世界顺序；不能另开同一活动数据库或把缺失数据当成可绘制地形。[存储](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/VoxyClientInstance.java) · [世界标识](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/commonImpl/WorldIdentifier.java) · [分段读取](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/common/world/WorldEngine.java) · [体素段](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/common/world/WorldSection.java) · [映射](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/common/world/other/Mapper.java)
3. 绘制：Sodium 的 CUTOUT 阶段运行 `MixinDefaultChunkRenderer.doRender`，把目标颜色和深度视图转为 `GlTextureView`，传 `glId()` 给 `VoxyRenderSystem.renderOpaque`。实际后端 `MDICSectionRenderer` 使用 GL 间接绘制。CPU 端的 `RenderDataFactory.generateMesh` 依赖 Voxy 的模型表并产出其私有打包格式。官方接口没有提供可交给 Caustica 的 Vulkan 图像或 BLAS。[Sodium 钩子](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/mixin/sodium/MixinDefaultChunkRenderer.java) · [渲染系统](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java) · [后端](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java) · [网格格式](https://github.com/MCRcortex/voxy/blob/02dfb1b7a91cddd02891a057cdd38478ea195c26/src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java)

## Goals / Non-Goals

先验收有数据的地形、实心山体与建筑遮挡、移动中的接缝，以及未安装 Voxy 的回归。第一阶段不要求生成未知区块，也不要求复现远处实体或全部资源包特效。

## Decisions

**路线 A（建议）——复用 Voxy 体素，由 Caustica 绘制。** 在 Vulkan 模式下分开 Voxy 的存储初始化与 GL 初始化；使用原有存储和世界标识。保留近景 RT，新增远景段选择、带比例的体素表面与材质转换、上传及退役，再通过现有 BLAS/TLAS 路径在时间重建之前参与求交。当前 `RtTerrain` 的驻留窗口受有效原版视距和 `ClientChunkCache.hasChunk` 限制，`RtSectionSnapshots` 从已加载区块复制 16³ 近景数据，扩大半径不能读取 Voxy 的各级 LOD。应按近景实际就绪的区域裁去重复远景，细层就绪前保留已有粗层，一帧内切换覆盖。预期可见真实存档中的山脊与建筑轮廓，近景自然遮挡远景。远景材质、透明和植被效果取决于新增的材质映射。同类分支已有此设计，但其公开说明依赖该分支自己的远景着色路径；本次核查的主树及 v1.0 发布未提供独立适配器。[Caustica Enhanced 说明](https://github.com/VIXIVDe/Caustica-Enhanced/blob/9f77c327e2eb9757bf46c4432e1615a4431313a8/mod/docs/VOXY_COMPATIBILITY.md)

**路线 B——复用 Voxy 的 GL 绘制，建立 GL/Vulkan 互操作。** 在适配的 GL 上下文中运行 Voxy，通过离屏颜色与深度、跨 API 资源和同步将结果交给 Caustica。在手部与 HUD 之前按同一相机射线的深度和覆盖判定合成。需处理投影/FOV、Y 方向、深度范围、抖动、内部渲染分辨率、动态尺寸与色彩空间；DLSS 还需要与合成顺序一致的运动信息。预期更接近 Voxy 的原生远景外观，远景作为图像层不会自动进入 Caustica 的反射或阴影求交。需分别验证驱动和平台的外部内存与同步能力。

## Risks / Trade-offs

### 首版的实际覆盖范围

远景半径取 Voxy 配置上限与「有效原版视距加 16 区块」中较小的值。近景优先，已有数据逐段加载。每段内部读取两格宽的 LOD 1 体素，最外一格优先取 LOD 0，以便在同一方块网格上交接。缺少其中一级时使用可用的那一级。按六邻域剔除遮蔽面，将同色相邻面合并为矩形。远景使用方块地图色和不透明漫反射，水面和树叶也采用这一简化材质。

渲染线程负责每个 16³ 分段的来源、请求令牌和发布槽。工作线程按世界标识短暂持有引擎引用，复制体素后释放。世界切换会使旧任务失效。近景与远景替换使用同一槽；已发布的近景离开原版窗口时，如果远景数据暂缺，则保留覆盖并延后重试。

项目所有者已选 A，接受简化远景材质。第一阶段使用 Voxy 的粗体素，将表面拆成与原有 16³ 近景段对齐的光追几何；同一空间段使用同一个发布槽，替换完成后才退役旧几何。按近景优先顺序渐进构建，限制首版远景驻留半径，避免将整个存档一次性装入显存。Voxy 在编译时提供接口，运行时仅在匹配的版本和 Vulkan 后端启用数据桥。原版 OpenGL 路径仍归 Voxy 管理。
