# Technical Documentation — Workshop Zone

> 本文面向开发者、维护者和兼容模组作者。普通玩家请阅读 [README.md](../README.md)。

## 1. WorkshopSession 生命周期

- 玩家成功打开受支持方块的原版 GUI 后，服务端以实际点击方块为中心扫描（水平 8 格、垂直 4 格）。
- 扫描只访问已加载区块，不每 tick 扫描。
- 服务端为每名玩家保存一个仅存在于内存中的临时会话。
- 关闭 GUI、断线、死亡、重生、切换维度、离开有效距离或中心方块失效时清理。
- `sessionId` 单调递增，`revision` 在快照变化时递增，`syncId` 绑定原版 `ScreenHandler.syncId`。

## 2. 服务端扫描范围

- 扫描水平 8 格、垂直 4 格范围内的受支持方块。
- 只访问已加载区块，不强制加载。
- 类别状态惰性检查：打开/刷新生成快照、修改标签或尝试插入时才检查当前逻辑容器。

## 3. 逻辑大箱解析

- `WorkshopContainerResolver` 解析相连箱子为 `LogicalContainer`。
- 两半库存通过 `DoubleInventory` 合并。
- 大箱只有一个稳定代表位置，计数为 1 个逻辑容器。
- 两半标签同步保存相同的规范化规则；两边规则不同或内容不兼容时视为冲突。

## 4. 网络协议和稳定 Identifier

所有自定义 Payload 使用 `CustomPayload` with `Id<>`:

- `workshop_zone:workshop_craft_preview` — S2C 预览
- `workshop_zone:confirm_workshop_craft` — C2S 确认
- `workshop_zone:workshop_craft_execution_result` — S2C 执行结果
- 其他 Payload 用于会话同步、搜索、标签、归仓等

稳定 Identifier 编码：

- `WorkshopCraftPreviewResultCode`: `workshop_zone:available`, `workshop_zone:not_needed`, `workshop_zone:insufficient`, `workshop_zone:grid_not_empty`, etc.
- `WorkshopCraftExecutionResultCode`: `workshop_zone:success`, `workshop_zone:cancelled`, `workshop_zone:expired`, etc.
- `WorkshopCraftMode`: `workshop_zone:single`, `workshop_zone:batch`
- 未知 ID 解码时抛出 `DecoderException`。

## 5. sessionId、revision 和 syncId 验证

- `WorkshopCraftService.preview()` 和 `confirm()` 在使用前验证 `session` → `revision` → `syncId` 与当前一致。
- `WorkshopCraftPendingChecks.validate()` 检查预览 nonce、玩家 UUID、session、revision、sync 和过期。
- 客户端 `WorkshopCraftClientFilter.acceptPreview()` 要求 session、revision、sync 匹配且 nonce > 上次接受值。

## 6. 远程 GUI 切换安全验证

- 客户端只发送 session ID、revision、sync ID 和目标位置。
- 服务器要求目标仍在当前扫描结果中、目标区块已加载、玩家距离不超过 8 格。
- 重新确认方块类型、维度、会话和原版 ScreenHandler。
- `WorkshopOpenChecksTest` 系列单元测试覆盖各种失败场景。

## 7. 标签 NBT 版本兼容

标签存储在箱子/木桶 BlockEntity 的 `workshop_zone:container_label` NBT 复合项中：

```json
{
  version: 3,
  mode: "workshop_zone:exact_item",
  item: "minecraft:iron_ingot"
}
```

- 版本 1（旧版精确物品）和版本 2（精确物品/Item Tag）均可读取。
- 下次保存时自动写为版本 3。
- 未知版本、模式、条目类型、物品 ID、损坏字段组合记录警告并安全回退。
- 白名单保存时按"精确物品在前、Item Tag 在后，同类型按 ID 排序"规范化并去重。

## 8. 标签模式和白名单

- `workshop_zone:none` — 无限制
- `workshop_zone:exact_item` — 仅允许注册 ID 匹配的物品
- `workshop_zone:item_tag` — 仅允许动态 Item Tag 成员
- `workshop_zone:whitelist` — 最多 32 项，包含精确物品和 Item Tag 条目

白名单保存时去重和规范化；输入顺序不影响规则相等性。

## 9. 输入过滤

标签限制覆盖以下路径：
- 手动放入（`Slot#canInsert` Mixin）
- Shift 快速移动（`ScreenHandler#onSlotClick` Mixin）
- 原版漏斗
- Fabric Transfer API 标准 `InventoryStorage`

所有路径共用 `ContainerLabelRule` 判定，不注册第二套冲突 Storage。

## 10. 一键归仓事务

- 来源：玩家主存储区（槽位 9–35），Shift 模式下追加快捷栏（0–8）。
- 优先级：精确物品标签 > 白名单精确物品 > 单一 Item Tag > 白名单 Item Tag。
- 同级按"已有同堆可合并、距离、扫描顺序、稳定位置"确定性排序。
- 每个槽使用独立外层 Fabric Transaction：先模拟目标插入，再从玩家槽提取完全相同数量，两边一致才提交。

## 11. 搜索目录和详细搜索

- **目录**（`WorkshopItemCatalog`）：进入搜索界面时由服务器依据当前 `WorkshopSession` 生成。只包含物品注册 ID、工坊总数量、逻辑容器数量和变体标记。
- **详细搜索**（`WorkshopItemSearchPlanner`）：选择具体物品后执行，逐容器验证维度、已加载区块、锁、权限。
- 目录不发送容器位置、名称、槽位或 Data Component。

## 12. 权限与锁验证

- `WorkshopSearchContainerCollector` 检查容器访问和 `canSearchItem` 权限。
- `WorkshopCraftAccessCallback.EVENT` 在合成提取前验证。
- `ContainerLabelEditCallback` 在标签编辑前验证。
- 原版锁通过标准 API 检查。
- 未加载区块不强制加载。

## 13. 容器高亮渲染

- 使用 Fabric API 1.21.1 的 `WorldRenderEvents.AFTER_ENTITIES`。
- 独立线框 `RenderLayer`，轮廓可穿墙显示。
- 渲染层在结束阶段恢复深度写入、深度测试、混合、剔除和线宽。
- 默认持续 5 秒，最多 64 个逻辑容器 / 128 个方块位置。
- 大箱两半都描边。

## 14. 配方查看器位置适配

- `RecipeViewerDetector` 使用 Fabric Loader 的公开 API 精确检测 `jei`、`emi`、`roughlyenoughitems`。
- AUTO + 检测到配方查看器：优先上方 → 左侧 → 下方 → 折叠。
- AUTO + 未检测到：保持原有右侧优先。
- `WorkshopSidebarPlacementResolver` 统一解析各 Placement 的最终矩形。

## 15. 单次和批量仓库辅助合成

详见 [README.md](../README.md) 核心功能部分。技术实现：

### 批量模式识别

- Minecraft 1.21.1 Yarn `CraftRequestC2SPacket.shouldCraftAll()` 返回 boolean。
- 原版 `RecipeBookWidget#mouseClicked` → `ClientPlayerInteractionManager#clickRecipe(…, craftAll)` → 创建包。
- 服务端 `ServerPlayNetworkHandlerMixin` 调用 `packet.shouldCraftAll()` 区分模式。

### 服务端流程

1. `WorkshopCraftService.preview()` 识别 `craftAll` → 设置 `WorkshopCraftMode.BATCH` / `.SINGLE`。
2. `WorkshopCraftPlanBuilder.build()` 分别计算 `playerOnlyMaxIterations` 和 `combinedMaxIterations`。
3. 仅当 `combinedMaxIterations > playerOnlyMaxIterations` 时显示批量确认（`storageItemCount > 0` 且仓库提高上限）。
4. 生成 `WorkshopCraftPendingConfirmation`（含 `craftMode` 和 `plannedIterations`）。
5. 确认时重新计算；`plannedIterations` 不一致则 `BATCH_CHANGED` / `MATERIALS_CHANGED` 失败。
6. `WorkshopCraftTransactionExecutor.execute()` 在同一外层 Transaction 中填充。

### 批量最大次数

- `MAX_BATCH_ITERATIONS = 64`（`WorkshopCraftPlanBuilder`）。
- 受联合库存可行分配、`ItemStack#getMaxCount`、`Slot#getMaxItemCount` 和硬上限共同约束。
- 最大堆叠 16 的材料限制批次为 16，最大堆叠 1 的限制为 1。
- 批次为 1 时退化为 SINGLE 模式。

### 预览 Payload 字段

`WorkshopCraftPreviewPayload` 新增：
- `craftMode` — SINGLE/BATCH 稳定 Identifier
- `plannedIterations` — 批次数
- `outputPerIteration` — 单次输出数量
- `totalOutputCount` — long 类型总输出数
- `playerOnlyMaxIterations` — 仅玩家最大次数
- `combinedMaxIterations` — 联合最大次数

## 16. Ingredient 求解

### 单次（1 次）

`WorkshopCraftAssignmentSolver.solve(ingredients, supplies)` 委托给 `solve(ingredients, supplies, 1)` → `adapt()` → `AllocationSolver.solveBatch(…, 1)`。

### 批量（N 次）

1. `AllocationSolver.maxIterations()` 使用二分搜索（low=0, high=limit, 最多 7 轮）。
2. 每轮调用 `solveBatch()` 执行变体级最小费用流。
3. 每个 `ItemVariant` 聚合所有供给槽为一个 `VariantCapacity`（含总量和 maxCount）。
4. 费用结构：`STORAGE_COST = 1_000_000_000` 确保玩家优先；`stableOrder` 作为次级费用实现确定性。
5. 求解返回：每个配方位置选定的变体 + 每个源槽的精确提取量。

### 求解优先级

1. 最大化可制作次数（二分确定最大 N）。
2. 在 N 次下最小化仓库用量（费用最小化）。
3. 玩家同一变体的较小堆优先。

## 17. Fabric Transfer API 事务

- `WorkshopCraftTransactionExecutor.execute()` 使用 `Transaction.openOuter()`。
- 外层 Transaction 覆盖：所有玩家来源槽提取 + 所有仓库来源槽提取 + 所有工作台目标槽插入。
- 任何提取/插入不一致或数量验证失败 → `transaction.abort()` → 整体回滚。
- 批量填充不是循环执行 N 次单次事务。
- 成功后 `markDirty()` + content sync 让原版重算输出槽。

## 18. 待确认 Preview 生命周期

- `WorkshopCraftPendingStore` 每玩家最多一个 pending confirmation。
- `previewId` 单调递增，一次 confirm 消耗。
- 以下情况清理：确认、取消、过期（200 ticks）、新 preview 替换旧 preview、GUI 关闭、session 失效、玩家断开。
- 过期/重复/stale 确认返回相应的 `WorkshopCraftExecutionResultCode`。

## 19. Mixin 目标与注入点

### 服务端

| Mixin | 目标 | 注入点 | 用途 |
|-------|------|--------|------|
| `ServerPlayerInteractionManagerMixin` | `interactBlock` | HEAD + RETURN | 记录/确认 ScreenHandler 打开 |
| `ServerPlayerEntityMixin` | `onHandledScreenClosed` | HEAD | 清理会话 |
| `ChestBlockEntityMixin` | `readNbt`/`writeNbt` | TAIL | 标签读写 |
| `BarrelBlockEntityMixin` | `readNbt`/`writeNbt` | TAIL | 标签读写 |
| `SlotMixin` | `canInsert` | HEAD | 标签感知插入过滤 |
| `ScreenHandlerMixin` | `onSlotClick` | HEAD | 拒绝违规手动放入 |
| `ServerPlayNetworkHandlerMixin` | `onCraftRequest` | HEAD | 拦截配方请求 |

### 客户端

| Mixin | 目标 | 注入点 | 用途 |
|-------|------|--------|------|
| `CraftingScreenMixin` | `render` | TAIL | 绘制确认 Overlay |
| `CraftingConfirmationInputMixin` | `mouseClicked`/`keyPressed`/`charTyped` | HEAD | 确认期间消费输入 |
| `ClientPlayNetworkHandlerMixin` | `onSynchronizeRecipes` | HEAD | 配方重载时清理 |

## 20. 性能边界

- 配方计划只在玩家点击具体配方时计算，不每 tick。
- 批量最大 64 次，二分搜索最多 7 轮。
- 每轮最多 9 个 Ingredient、有限数量的供给槽。
- 搜索目录在进入搜索和手动刷新时生成，不每 tick 扫描。
- 大箱去重、不异步读 World/Inventory/RecipeManager。
- 事务只用一个外层 Transaction，不循环 64 次。

## 21. 数据包重载行为

- `ClientPlayNetworkHandlerMixin` 在 `onSynchronizeRecipes` 的 `HEAD` 清理客户端旧预览。
- Item Tag 成员实时使用最新绑定，不展开写入 NBT。
- 已保存但不可用的 Tag 保留在 NBT 中，GUI 显示警告。
- 不遍历世界中的容器进行批量更新。

## 22. 已知技术限制

- 不支持 NeoForge 或其他加载器。
- 不支持跨维度容器访问。
- 不支持未加载区块强制访问。
- 客户端配置（位置、autoAvoid 等）仅本地保存，不同步服务器。
- `WorkshopCraftPlanBuilder` 的权限回调在求解前使用原始堆叠数而非求解后的精确提取量。
- 确认面板不绑定预览中的精确变体——重新求解时可能选择不同变体但仍保持 `plannedIterations` 一致。

## 23. 配方查看器合成桥（1.1.0）

### 模块边界

纯客户端、查看器无关的代码位于 `client/compat/recipeviewer/`：

- `RecipeViewerCraftBridge`：验证客户端、玩家、交互管理器、3×3 `CraftingScreen`、真实 Recipe ID、`CraftingRecipe` 类型、非空输出和空合成栏。
- `RecipeViewerSource`：使用稳定 ID `workshop_zone:vanilla`、`workshop_zone:jei`、`workshop_zone:emi`、`workshop_zone:rei`；不使用 enum ordinal 进行网络或持久化。
- `RecipeViewerTransferResult`：给薄适配层返回稳定语义，不作为网络协议。
- `RecipeViewerTransferGuard`：按 source、recipeId、syncId、batch 和客户端 tick 去重；同请求 5 tick 内只发送一次，Screen/syncId 变化或断线时清空。

Bridge 不 import JEI、EMI 或 REI API。各查看器引用只存在于自己的 `client/compat/<viewer>/` 包，common/main 源集和专用服务器不引用任何查看器类。

### 统一调用链

```text
JEI IRecipeTransferHandler / REI TransferHandler
  -> RecipeViewerCraftBridge.request(source, recipeId, batch)
  -> ClientPlayNetworkHandler RecipeManager 解析真实 RecipeEntry
  -> ClientPlayerInteractionManager.clickRecipe(syncId, recipeEntry, batch)
  -> vanilla CraftRequestC2SPacket(syncId, recipeId, craftAll)
  -> ServerPlayNetworkHandlerMixin
  -> WorkshopCraftService.preview
  -> 现有确认 Overlay
  -> ConfirmWorkshopCraftPayload
  -> WorkshopCraftTransactionExecutor
```

没有新增“配方查看器合成”C2S 包。客户端不发送 Ingredient、配方结果或仓库库存，不直接点击/写入槽位，也不直接调用服务端合成服务。

### 模拟检查与真实转移

- JEI `doTransfer == false` 和 REI `isActuallyCrafting() == false` 只调用 Bridge 的无副作用验证，不发送包、不移动物品、不打开确认 Overlay。
- 真实转移只提交一次原版 `clickRecipe`。
- 客户端只验证可安全映射的 Recipe ID 与 Screen；材料数量和仓库可用性始终由服务端最终确认。
- 玩家材料足够时现有服务端 Mixin 放行原版填充；只有仓库提高可制作能力时才产生 Workshop 确认。

### JEI 注册和覆盖顺序

- 使用公开 `IModPlugin#registerRecipeTransferHandlers`，为 `(CraftingScreenHandler.class, RecipeTypes.CRAFTING)` 注册 `WorkshopZoneJeiCraftingTransferHandler`。
- JEI 19.43.0.393 明确将 `VanillaPlugin` 排在插件列表首位；recipe-transfer 注册表对同一 class/type 键使用后注册覆盖前注册。
- Workshop Zone 因而通过公开 API 替换 JEI 原版工作台 Handler，避免 JEI 标准槽位移动与 Workshop 原版请求双重执行。
- JEI 先查精确 Handler，找不到时才查 Universal Handler；因此 Universal Handler 不能可靠覆盖原版工作台 Handler，本实现没有使用它。

### REI 注册、优先级和返回界面

- `WorkshopZoneReiClientPlugin` 通过公开 `TransferHandlerRegistry#register` 注册 Handler，并通过 `ExclusionZones` 注册侧栏区域。
- Handler 只接受 `minecraft:plugins/crafting`、`CraftingScreen`、`CraftingScreenHandler` 且拥有 `Display#getDisplayLocation` 的显示。
- `getPriority() = 100`，高于 REI 默认 Handler 的 `0`；不适用时返回 `createNotApplicable()` 让其他机器/特殊配方继续处理。
- 成功或安全失败时使用 `blocksFurtherHandling(true)`，阻止默认 Handler 重复移动，并返回原工作台 Screen。
- `isStackedCrafting()` 直接映射为 BATCH，普通操作映射为 SINGLE。

### EMI 1.1.24+1.21.1 上游公开 API 阻塞

EMI 可选运行模式和安全共存已验证，但 1.1.0 不注册无效的 Fill Handler：

1. EMI 固定先注册自身 `CraftingRecipeHandler`。
2. 公开 `EmiRegistry#addRecipeHandler` 只能把新 Handler 追加到列表末尾。
3. 运行时只选择第一个 `supportsRecipe` 的 Handler，标准 `EmiCraftingRecipe` 已被内置 Handler 命中。
4. 公开 API 没有优先级、前插、替换或注销机制。

因此，在禁止 `emi.registry`/`emi.runtime`、反射和 Mixin 的约束下，Workshop Handler 不可能可靠接管标准 Fill 按钮。项目选择明确记录该限制，而不是提交永远不会被调用的伪兼容代码。`-Precipe_viewer=emi` 仍用于验证可选依赖缺失/存在时的安全加载。

### 可选依赖与发布产物

- JEI API `modCompileOnly`，JEI 完整模组只在 `recipe_viewer=jei` 时进入 `modLocalRuntime`。
- REI API `modCompileOnly`，REI、Architectury、Cloth Config 和 Error Notifier 只在 `recipe_viewer=rei` 开发运行时解析。
- EMI 只在 `recipe_viewer=emi` 开发运行时解析。
- 正式 JAR 不 `include`、不打包任何查看器或其运行依赖；`fabric.mod.json` 中它们仍位于 `suggests`。

### Forge / NeoForge 预留边界

未来移植只需替换加载器专属的 Viewer entrypoint/adapter 和客户端配方请求调用。Bridge 的请求语义、去重键、服务端 CraftRequest 权威验证、确认 Overlay 协议及事务服务保持独立。本版本没有开始 Forge 或 NeoForge 移植。
