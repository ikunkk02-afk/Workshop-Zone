# Workshop Zone（工坊域）

Workshop Zone 是一个处于早期开发阶段的 Fabric 模组。它在服务器端扫描玩家当前打开的工作区域，并允许玩家从原版容器或工作方块界面旁的侧边栏快速切换设备。

## 当前进度

- Phase 1 / Step 1：服务器端附近工作区域扫描器——已完成
- Phase 1 / Step 2：工坊侧边栏——已完成
- Phase 1 / Step 3：补齐原版工作方块——已完成
- Phase 1 / Step 4：侧边栏快速切换容器与工作设备——已完成
- Phase 1 / Step 5：精确物品容器标签——已完成
- Phase 1 / Step 6：物品 Tag 类别容器标签——已完成
- Phase 1 / Step 7：按标签一键归仓——已完成
- Phase 1 / Step 8：响应式工坊 GUI——已完成
- Phase 1 / Step 9：自定义多标签白名单——已完成
- Phase 1 / Step 10：配方查看器兼容与侧边栏位置设置——已完成
- Phase 1 / Step 11：工坊全局物品搜索与容器高亮——已完成
- Phase 1 / Step 12：仓库材料补齐合成与二次确认——已完成
- Phase 1 / Step 13：Shift 批量仓库补齐合成——已完成

第一阶段状态：**Core feature set complete**。

当前支持箱子、陷阱箱、木桶、工作台、熔炉、高炉、烟熏炉、锻造台、三种铁砧、切石机、砂轮、织布机、制图台、酿造台和附魔台。相连的大箱子只计为一个逻辑容器；制箭台、漏斗、潜影盒和模组机器不参与扫描或切换。

侧边栏与标签编辑器会根据窗口宽高、GUI Scale、语言文字宽度、原版 GUI、配方书占用空间和客户端位置设置，在右、左、上、下或自定义位置之间解析同一份最终布局，并在 `154–280` 像素范围内切换标准、紧凑或窄布局。模式按钮和标签操作按钮按当前翻译文本的实际宽度选择列数；宽面板优先把三个模式放在同一行、把七个白名单操作排为三列，并优先为白名单列表保留至少四个完整条目的可视空间。渲染、点击、滚轮、拖动、Tooltip 与配方查看器排除区域共享同一份最终矩形；放不下的文字显示省略号，完整内容保留在 Tooltip 与旁白中。

## 仓库材料补齐合成（Phase 1 / Step 12–13）

本阶段支持原版工作台 3×3 的标准有序与无序 `CraftingRecipe`。玩家普通点击原版配方书配方时仍只准备一次材料：背包能独立完成一次时完全保留原版流程，只有背包不足、当前有效工坊的可访问容器可以补齐时才显示单次确认。Shift 点击由 Minecraft 1.21.1 原版 `CraftRequestC2SPacket#shouldCraftAll()` 标志识别；服务端分别计算 `playerOnlyMaxIterations` 与 `combinedMaxIterations`，仅当仓库能提高最大安全次数时显示批量确认。面板确认前不移动、不锁定、不预留任何物品，取消也不会改变背包、仓库或合成栏。

确认后服务端从自己的 `RecipeManager` 重新解析配方，重新验证工作台、空的 3×3 合成栏、会话、revision、sync ID、维度、区块、距离、锁和保护回调，再按“玩家主背包与快捷栏优先、仓库只补缺失部分”重建合成计划。批量最大次数通过不超过 64 次的二分搜索确定；每轮使用同一个确定性最小费用流求解器处理 Tag、重复与重叠 Ingredient。求解顺序先最大化可制作次数，再在该次数下最小化仓库提取量；玩家同一实际 `ItemVariant` 的较小堆优先用作来源。每个真实合成槽只选择一种精确 `ItemVariant`，不同 Data Component 不会混入同一格。有序与无序配方继续复用单次流程的原版对齐/稳定槽位映射。

批量次数同时受联合库存可行分配、所选物品 `ItemStack#getMaxCount`、工作台槽位 `Slot#getMaxItemCount` 与 64 次硬安全上限约束；最大堆叠 16 或 1 的材料会自然把批次限制为 16 或 1。批量预览发送单次输出图标，把 `outputPerIteration`、`plannedIterations` 与使用 `long` 的 `totalOutputCount` 分开发送，因此预计总输出超过单堆上限也不会构造非法超量 `ItemStack`。确认时材料减少会整批失败并要求重新选择，不会静默降级；材料增加也仍按预览次数执行，不会静默扩大。

提取玩家槽、逻辑仓库槽并填入工作台输入槽始终使用同一个 Fabric Transfer API 外层 Transaction；批量填充不是循环执行多次单次事务。任何来源数量、权限或目标插入异常都会整体回滚。成功后只向每个真实 3×3 输入槽放入 `plannedIterations` 件材料，不直接生成成品；玩家再 Shift 点击原版输出槽完成批量制作。结果槽的连续制作、输出背包空间、配方统计、解锁和容器剩余物全部继续由原版处理。合成栏在请求和确认时都必须为空。

JEI 仍是可选依赖。单次与批量确认共用同一个可滚动 Overlay、侧边栏 Placement 和 JEI 排除区域；AUTO、RIGHT、LEFT、TOP、BOTTOM 与 CUSTOM 位置共享同一布局。JEI 的公开 Transfer API 未被反射或 internal 包强行接入。若配方查看器没有自然调用原版 `CraftRequestC2SPacket` 流程，本阶段不承诺其独立 Transfer 按钮会触发仓库补齐；原版配方书普通点击与 Shift 点击是核心验收路径。

## 容器标签

玩家实际打开普通箱子、陷阱箱、木桶或大箱后，可以从工坊侧边栏标题区进入“容器标签”编辑器。将鼠标当前携带物品设为候选不会消耗、移动或复制该物品；保存和清除请求均由服务器重新验证当前 session、revision、sync ID、维度、已打开条目的稳定代表位置、距离、已加载区块、方块结构、原版 ScreenHandler 和编辑冷却。客户端不能通过这个协议指定其他维度或任意容器坐标。

当前支持 `workshop_zone:none`、`workshop_zone:exact_item`、`workshop_zone:item_tag` 与 `workshop_zone:whitelist` 四种稳定模式。精确标签只按 `Item` 类型/注册 ID 匹配；类别标签使用 Minecraft 当前加载的动态 Item Tag 成员关系；白名单可以同时包含多个精确物品项和 Item Tag 项，任意一项匹配即允许插入。白名单最多 32 项，保存时按“精确物品在前、Item Tag 在后，同类型按完整 ID 排序”规范化并去重，因此输入顺序不影响规则相等性。所有模式都不比较数量、耐久、附魔、自定义名称、NBT 或 Data Component。例如，一个白名单可以同时允许铁锭、金锭和 `#minecraft:logs`，但仍拒绝铜锭、木板与面包。

类别编辑器提供当前服务端实际存在且非空的常用类别，也可以根据鼠标物品向服务端查询其所属 Tag。查询最多返回 64 个去重候选，常用预设优先、`minecraft` 命名空间其次，其余按完整 Tag ID 稳定排序。常用预设会按当前环境过滤；本版本验证并声明的预设为 `minecraft:logs`、`minecraft:planks`、`minecraft:leaves`、`minecraft:saplings`、`minecraft:wool`、`minecraft:coals`、`minecraft:arrows`、`c:ores`、`c:ingots`、`c:gems`、`c:foods` 和 `c:seeds`。未知数据包或模组 Tag 在 GUI 中原样显示为 `#namespace:path`，不会伪造本地化名称。

白名单编辑器支持“添加鼠标物品”“查找类别”“添加所选类别”“删除所选标签”“保存”“清除”和“取消”。未保存的候选列表只存在于客户端，顶部紧凑图标概览会立即反映这些候选修改，关闭 GUI 不会改动服务器。普通工坊快照只发送白名单模式、条目数量、最多 4 个确定性预览物品 ID、不可用条目数量和冲突摘要，不发送完整规则；设备列表最多绘制 3 个缩放物品图标，并用 `+N` 表示尚未在条目中绘制的标签数量。完整的最多 32 项仍仅在打开当前容器标签编辑器时通过独立详情查询协议按需返回。详情请求同样校验 session、revision、sync ID 和当前打开容器的稳定代表位置，过期或伪造响应不会覆盖客户端候选。

标签限制玩家手动放入、Shift 快速移动、原版漏斗和标准 Fabric Transfer API 输入，已有物品始终可以自由取出。Fabric API 0.116.15 的标准 `InventoryStorage` 在事务插入前调用 `Inventory.isValid`，因此本模组复用同一规则入口，没有注册第二套冲突 Storage；模拟事务不会修改容器，只有已提交事务才生效。直接绕过 `Inventory`/Transfer API 修改内部槽位的模组不保证兼容。

设置或缩减新规则前，服务器检查整个逻辑容器；新 Item Tag 项必须当前存在且非空，精确物品项必须是已注册且非 `minecraft:air` 的物品，白名单还会拒绝空列表、超过 32 项、重复项、未知类型和错误字段组合。发现任意现有物品不匹配新规则时拒绝保存并保留原规则与所有物品原位。大箱沿用扫描器的稳定代表位置，两半原子地保存相同的规范化规则。两个单箱合并时，一边有规则、一边无规则且全部内容兼容会在打开或扫描时同步；两半规则集合不同、模式不同、或单边规则与内容不兼容都视为冲突，不会取并集或静默扩大/缩小规则。规则冲突时禁止输入但允许取出和清除；内容冲突时仍允许符合现有规则的新物品进入，只拒绝不匹配物品。拆掉一半后，剩余单箱保留自身规则，掉落的箱子物品不携带标签。

Item Tag 成员不会展开写入 NBT，也不会缓存成永久成员列表。数据包重载后，插入、快照和编辑验证直接使用最新 Tag 绑定；只清理候选代表图标等小型缓存，不遍历世界中的容器。如果已保存 Tag 因数据包或模组变化而不存在/为空，该条目不会被自动删除，也不会移动或删除容器物品；GUI 会显示不可用警告。单一 Item Tag 规则或所有条目都不可用的白名单会禁止新输入；白名单中只要仍有有效精确物品或有效 Tag 项，这些有效项仍可继续匹配。失效项本身永远不会匹配任意物品。新增或重新保存时，服务器仍拒绝不存在或空的 Tag。若现有内容不再符合规则，则显示“内容冲突”；取出所有不匹配物品后会在下次检查时自动恢复。

标签存入箱子或木桶 BlockEntity 的独立 NBT 复合项：

```text
"workshop_zone:container_label": {
  version: 3,
  mode: "workshop_zone:exact_item",
  item: "minecraft:iron_ingot"
}
```

类别标签使用同一个 NBT 根键：

```text
"workshop_zone:container_label": {
  version: 3,
  mode: "workshop_zone:item_tag",
  tag: "minecraft:logs"
}
```

白名单在同一根键下保存规范化条目列表；条目类型使用稳定 ID `workshop_zone:item` 与 `workshop_zone:item_tag`：

```text
"workshop_zone:container_label": {
  version: 3,
  mode: "workshop_zone:whitelist",
  entries: [
    { type: "workshop_zone:item", value: "minecraft:gold_ingot" },
    { type: "workshop_zone:item", value: "minecraft:iron_ingot" },
    { type: "workshop_zone:item_tag", value: "minecraft:logs" }
  ]
}
```

无标签时不写这个复合项。版本 1 与版本 2 的精确物品、Item Tag 标签仍会读取，并在下一次正常保存时自动写为版本 3；迁移保留原来的 `exact_item` / `item_tag` 模式，不会强制转换为白名单。未知版本、模式、条目类型、物品 ID、`minecraft:air`、超过 32 项或损坏字段组合会记录一次合理警告并安全回退，不会阻止世界加载。已保存但当前不可用的合法 Tag ID 则会原样保留。

当前模组本身不提供箱子所有权，服务器应通过保护模组或 `ContainerLabelEditCallback` 兼容回调管理编辑权限。

## 按标签一键归仓

侧边栏标题区的归仓按钮会把玩家背包主存储区（槽位 9–35）中能匹配标签的物品送入当前工坊范围内的逻辑容器；按住 Shift 再点击会在主存储区之后追加快捷栏槽位 0–8。盔甲、副手、鼠标栈、配方输出和任何非玩家存储槽都不会成为来源。

同一物品有多个目标时，优先级固定为：

1. 单一精确物品标签；
2. 白名单中的精确物品项；
3. 单一 Item Tag 标签；
4. 白名单中的 Item Tag 项。

同一级继续按“已有同类堆叠可合并、距离、扫描顺序、稳定代表位置”确定性排序。大箱始终是一个逻辑目标，不会让两半各自参与排序。归仓仍使用每个来源槽的外层 Fabric Transaction：先模拟/执行目标插入，再从玩家槽提取完全相同数量，只有两边数量一致才提交；目标无空间、规则在过程中变化或任一提取不一致都会回滚，避免吞物和复制。

## 工坊物品搜索与容器高亮

侧边栏标题区的 `S` 按钮会在当前原版容器或工作设备 GUI 内进入工坊搜索模式。搜索文本只在客户端匹配当前语言的物品显示名称、完整注册 ID、路径和命名空间；支持 `@namespace` 筛选，完整 ID 精确匹配优先，候选最多显示 50 项。客户端使用原版 `TextFieldWidget`，因此保留中文输入法、复制粘贴、Home/End、方向键和退格等编辑行为。玩家选择一个明确候选后，客户端才向服务器发送该物品注册 ID，不会把中文本地化名称交给服务端匹配，也不会在每次输入字符时发起库存请求。

服务器只遍历当前 `WorkshopSession` 扫描结果中的普通箱子、陷阱箱、木桶和逻辑大箱。每个目标都会重新检查当前维度、已加载区块、稳定代表位置、方块与大箱结构、玩家 8 格距离、原版锁、原有远程打开保护回调以及独立的 `WorkshopSearchAccessCallback`。请求不能指定搜索中心、维度、容器坐标、大箱成员或任意搜索区域；未加载区块不会被强制加载。工作设备内部库存、玩家背包、其他玩家库存、潜影盒、漏斗、发射器、投掷器和模组机器均不参与搜索。

搜索按 `Item` 类型统计，精确数量使用 `long`。不同 Data Component 的同一种物品（例如不同效果药水）会合并到同一物品总数，但结果会提示“包含多个物品变体”；搜索不会修改、移动、提取、合并或标记任何 `ItemStack`。大箱复用现有逻辑容器解析器，完整统计 54 格，只产生一个结果，并保留两半坐标用于高亮。结果按距离、容器内数量、扫描顺序和稳定代表位置确定性排序，最多返回 64 个逻辑容器，超出时保留真实匹配总数并标记截断。

结果行显示工坊总数量、匹配容器数量、每个容器的名称、数量、距离和坐标。点击结果继续复用现有 `OpenWorkshopTargetPayload` 与服务端完整安全验证，不创建新的远程打开协议。独立定位按钮可以高亮单个容器，“全部定位”只使用当前已返回结果；默认高亮持续 5 秒，最多 64 个逻辑容器/128 个方块位置，大箱两半都会描边。高亮使用 Fabric API 1.21.1 的 `WorldRenderEvents.AFTER_ENTITIES` 和独立线框 `RenderLayer`，轮廓可穿墙显示；渲染层在结束阶段恢复深度写入、深度测试、混合、剔除和线宽。关闭容器 GUI 后高亮可继续到期，切换世界、切换维度或断线会立即清除。

搜索面板与输入框使用侧边栏已经解析出的最终 Placement 矩形，因此 RIGHT、LEFT、TOP、BOTTOM、CUSTOM、拖动位置、GUI Scale 和窗口变化共享同一布局。JEI 的排除区域仍返回实际完整面板；AUTO 模式检测到 JEI、EMI 或 REI 时继续优先放在上方。搜索不会读取或复用配方查看器内部搜索内容，也不会执行配方转移、自动合成、材料补充或永久仓库索引。

## 运行环境

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.19.3 或更高版本
- Fabric API 0.116.15+1.21.1 或兼容的 1.21.1 版本

Fabric API 是唯一必需前置。JEI、EMI 和 REI 都不是强制前置；`fabric.mod.json` 只在 `suggests` 中声明可选兼容。项目没有增加 Mod Menu、Cloth Config 或新的正式运行时依赖。

## 配方查看器兼容与面板位置

客户端使用 Fabric Loader 的公开 API 精确检测 `jei`、`emi` 和 `roughlyenoughitems`。AUTO 模式检测到任一配方查看器时按“上方 → 左侧 → 下方 → 折叠按钮”的顺序避开右侧物品列表；未检测到配方查看器，或关闭 `autoAvoidRecipeViewers` 时，继续保持原来的“右侧 → 左侧 → 上方 → 下方 → 折叠按钮”顺序。玩家明确选择右、左、上或下时会优先尊重该选择，但最终矩形仍会限制在屏幕内，空间不足时安全回退。

侧边栏标题区的 `P` 按钮可以选择自动、右侧、左侧、上方、下方、自定义或重置位置。TOP/BOTTOM 都以原版 GUI 的水平中心为基准，并分别放在 GUI 上边缘或下边缘之外；CUSTOM 模式只能从标题栏空白区域开始拖动，归仓、标签、刷新、位置和折叠按钮不会触发拖动。拖动期间只更新内存中的预览矩形，释放鼠标后才保存归一化坐标；按 Esc 会取消本次拖动。

客户端配置保存在 `config/workshop_zone-client.json`：

```json
{
  "version": 1,
  "sidebarPosition": "auto",
  "autoAvoidRecipeViewers": true,
  "customX": 0.5,
  "customY": 0.1
}
```

配置缺失时会生成默认文件；未知位置回退 AUTO，非有限数和越界坐标会安全限制。损坏 JSON 会被重命名为带时间戳的 `.corrupt-*.bak` 备份并恢复默认值，保存使用同目录临时文件和原子替换。该配置只在客户端初始化和设置改变时读写，不发送到服务器，也不会由专用服务器创建。

JEI 1.21.1 的公开 `IGuiContainerHandler#getGuiExtraAreas` 已接入，返回当前实际绘制矩形；折叠时只返回折叠按钮。EMI 的 `EmiRegistry#addGenericExclusionArea` 与 REI 的 `REIClientPlugin#registerExclusionZones` 也存在公开 API，但本阶段没有增加它们的 compile-only API 依赖或插件入口，AUTO 位置避让仍对 EMI/REI 生效。AUTO 不实时判断 JEI 物品列表是否被玩家隐藏；需要时可在位置菜单中手动强制选择右侧。

## 工作方式

玩家成功打开受支持方块的原版 GUI 后，服务器以实际点击方块为中心扫描水平 8 格、垂直 4 格范围。扫描只访问已加载区块，也不会定时或逐 tick 重扫。类别状态采用懒检查：打开/刷新生成快照、修改标签或尝试插入时才检查当前逻辑容器，不会每 tick 扫描世界中的标签箱。

服务器为每名玩家保存一个仅存在于内存中的临时会话。关闭 GUI、断线、死亡、重生、切换维度、离开有效距离或中心方块失效时，会话会被清理。侧栏条目按打开方块到设备方块中心的距离从近到远显示。

点击侧栏条目时，客户端只发送当前 session ID、revision、ScreenHandler sync ID 和目标位置。服务器要求目标仍在当前扫描结果中、目标区块已加载、玩家距离目标不超过 8 格，并重新确认方块类型、维度、会话和原版 ScreenHandler。服务器不会强制加载区块，也不允许跨维度访问。通过验证后，目标方块自己的原版 `NamedScreenHandlerFactory` 和 `openHandledScreen` 负责关闭旧界面并打开新界面。

侧栏还提供收起/展开和手动刷新。刷新与界面切换使用独立冷却。远程访问回调允许兼容模组拒绝打开请求，但当前不保证兼容所有领地保护模组。

## Mixin 说明

Fabric API 1.21.1 没有同时提供“方块位置”和“GUI 已成功打开”的服务端事件，原版玩家槽位也不会自动调用 `Inventory.isValid`，因此使用以下限定目标 Mixin：

- `ServerPlayerInteractionManager#interactBlock`：在 `HEAD` 记录旧 ScreenHandler，在 `RETURN` 确认原版交互确实打开了与受支持方块匹配的新 ScreenHandler。
- `ServerPlayerEntity#onHandledScreenClosed`：在 `HEAD` 清理当前 sync ID 对应的临时会话。
- `ChestBlockEntity` 与 `BarrelBlockEntity`：以 `@Unique` 字段实现 `ContainerLabelHolder`；在 `readNbt(..., WrapperLookup)` / `writeNbt(..., WrapperLookup)` 的 `TAIL` 读取和写入共享格式，并仅为这两类 BlockEntity 提供标签感知 `isValid`。
- `DoubleInventory`：只为大箱逻辑 Inventory 暴露标签感知接口，不保存额外数据。
- `Slot#canInsert`：在 `HEAD` 仅检查实现标签感知接口的容器槽位，覆盖手动、拖拽、数字键和 Shift 插入路径。
- `ScreenHandler#onSlotClick`：在 `HEAD` 拒绝针对标签容器的错误手动放入，并以 20 tick 冷却发送 Action Bar 和轻微失败音效；自动化失败不提示也不记录普通日志。

- `ServerPlayNetworkHandler#onCraftRequest`：在原版已完成主线程切换、sync ID、ScreenHandler 和 `canUse` 校验后、读取服务端 `RecipeManager` 前接入。背包材料足够或不属于本阶段流程时不取消原版处理；服务端确认仓库可补齐并已生成待确认预览时取消本次原版放置，背包不足且真实合成栏非空时也取消该次放置以避免原版重排已有物品。

- `CraftingScreen#render`：在 `TAIL` 绘制工作台内的二次确认 Overlay，不替换原版 Screen 或渲染流程。

- `CraftingScreen#mouseClicked`、`keyPressed` 与 `charTyped`：仅在确认 Overlay 可见时于 `HEAD` 消费输入，防止点击穿透到配方书、合成槽、数字键或搜索框；Overlay 不可见时完全不干预。

- `ClientPlayNetworkHandler#onSynchronizeRecipes`：在 `HEAD` 清理客户端旧配方预览，避免数据包重载后继续显示过期确认。

客户端另有一个限定的 `HandledScreen` accessor，用于读取原版 GUI 的 `x`、`y`、宽和高，并在确认面板主动关闭配方书后按原版 `findLeftEdge` 结果同步 `x`；它不改写槽位、库存或 ScreenHandler。服务端源码不引用任何客户端类。

## 命令与测试

扫描器调试命令仍可使用：

```text
/workshopzone scan
```

Windows 构建和测试：

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTest
.\gradlew.bat clean build
.\gradlew.bat runServer
.\gradlew.bat runClient
.\gradlew.bat runClient -Precipe_viewer=jei
```

JEI `19.43.0.392` 来自官方 BlameJared Maven（`https://maven.blamejared.com`），坐标为 `mezz.jei:jei-1.21.1-fabric:19.43.0.392`。其 API 仅以 `modCompileOnly` 参与编译，完整 JEI 只在显式传入 `-Precipe_viewer=jei` 时通过 `modLocalRuntime` 加入开发运行，不会打包进 Workshop Zone JAR。

人工测试时，在同一区域放置全部支持方块以及制箭台、漏斗和潜影盒，检查扫描、图标、Tooltip、滚动、折叠、刷新和侧边栏切换。还应检查：

- 背包、潜影盒、漏斗、村民交易和创造物品栏不显示侧栏。
- GUI Scale Auto/2/3/4，以及 16:9、16:10、4:3 窗口下不遮挡原版槽位或配方书。
- 在原版配方书分别普通点击与 Shift 点击工作台、木板、无序配方：确认单次只填一份，批量显示/填入最大安全次数，随后由原版输出槽 Shift 制作。
- 用“背包 8 木板 + 仓库 120 木板”验证 32 次预览、背包/仓库贡献统计和四个输入格各 32；再验证预览后材料减少整批失败、材料增加不扩大、玩家材料变化重新分配。
- 验证最大堆叠 16、最大堆叠 1、蛋糕桶剩余物、输出总数大于 64、大箱两半、两名玩家竞争同一批材料、输出背包空间不足时的原版停止行为。
- 使用 `runClient -Precipe_viewer=jei` 验证 AUTO/RIGHT/LEFT/TOP/BOTTOM/CUSTOM、中文/英文、不同 GUI Scale、确认面板滚动/Tooltip/Esc/无点击穿透；JEI 独立 Transfer 按钮仍按上文记录为非核心兼容路径。
- 在精确物品、Item Tag 与白名单三种模式间切换，验证白名单添加、去重、删除、滚动、32 项上限、未保存关闭、详情按需加载和失效 Tag 警告。
- 用手动放入、Shift 快速移动、漏斗、Fabric Transfer API 和一键归仓分别验证白名单允许项与拒绝项，并确认取出始终不受限制。
- 合并/拆分大箱，确认两半规则同步、不同集合冲突、归仓只出现一个逻辑目标。
- 关闭界面、远离、破坏中心方块、死亡/重生和切换维度后旧会话失效。
- 两名玩家同时打开不同工坊时数据互不混用。
- 专用服务器可以启动，控制台无 Mixin、客户端类加载或数据包异常。

## 尚未实现

- 递归子配方与自动连续补货/加工
- 黑名单与首件自动学习
- 跨维度物品搜索与永久库存索引
- 自动熔炼
- 制作计划
- 固定工坊核心
- EMI 深度集成、其他工作设备自动加工和模组机器兼容
- 跨维度访问或未加载区块访问

## License

MIT

## 工坊库存目录搜索（当前版本）

- 搜索候选只来自当前工坊实际存放的物品，不再从整个 Minecraft 物品注册表生成候选。
- 进入搜索模式时由服务器依据当前 `WorkshopSession` 生成可访问库存目录；客户端只按本地化名称、完整注册 ID、path 和 namespace 过滤。
- 目录条目仅包含 Item 注册 ID、工坊总数量、包含该物品的逻辑容器数量和多个 Data Component 变体标记；不会发送容器位置、名称、槽位、完整 `ItemStack` 或 Data Component 内容。
- 锁定和无权限容器不会泄露物品目录。选择物品后仍由服务端重新执行详细搜索和最终权限验证，目录不能用于打开容器、移动物品或自动归仓。
- 目录最多 4096 项；普通搜索最多显示 50 个候选，空输入只显示当前工坊数量最多的前 20 种物品。
- 目录在进入搜索和手动刷新时更新；玩家手动修改箱子后可点击刷新更新候选，不进行每 tick 库存扫描。
- 一键归仓成功或部分成功时，打开的搜索页会使目录失效并请求更新；详细搜索协议和容器高亮功能继续保留。
