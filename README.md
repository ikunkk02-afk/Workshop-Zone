# Workshop Zone（工坊域）

Workshop Zone 是一个处于早期开发阶段的 Fabric 模组。它在服务器端扫描玩家当前打开的工作区域，并允许玩家从原版容器或工作方块界面旁的侧边栏快速切换设备。

## 当前进度

- Phase 1 / Step 1：服务器端附近工作区域扫描器——已完成
- Phase 1 / Step 2：工坊侧边栏——已完成
- Phase 1 / Step 3：补齐原版工作方块——已完成
- Phase 1 / Step 4：侧边栏快速切换容器与工作设备——已完成
- Phase 1 / Step 5：精确物品容器标签——已完成

当前支持箱子、陷阱箱、木桶、工作台、熔炉、高炉、烟熏炉、锻造台、三种铁砧、切石机、砂轮、织布机、制图台、酿造台和附魔台。相连的大箱子只计为一个逻辑容器；制箭台、漏斗、潜影盒和模组机器不参与扫描或切换。

## 精确物品容器标签

玩家实际打开普通箱子、陷阱箱、木桶或大箱后，可以从工坊侧边栏标题区进入“容器标签”编辑器。将鼠标当前携带物品设为候选不会消耗、移动或复制该物品；保存和清除请求均由服务器重新验证当前 session、revision、sync ID、维度、已打开条目的稳定代表位置、距离、已加载区块、方块结构、原版 ScreenHandler 和编辑冷却。客户端不能通过这个协议指定其他维度或任意容器坐标。

当前只有 `workshop_zone:none` 与 `workshop_zone:exact_item` 两种稳定模式。精确标签只按 `Item` 类型/注册 ID 匹配，不比较数量、耐久、附魔、自定义名称、NBT 或 Data Component。例如，不同耐久和附魔的钻石剑都属于 `minecraft:diamond_sword`，不同效果的药水目前都属于 `minecraft:potion`。后续才可能考虑严格组件匹配，本阶段不支持物品 Tag 类别、白名单、黑名单或首件自动学习。

标签限制玩家手动放入、Shift 快速移动、原版漏斗和标准 Fabric Transfer API 输入，已有物品始终可以自由取出。Fabric API 0.116.15 的标准 `InventoryStorage` 在事务插入前调用 `Inventory.isValid`，因此本模组复用同一规则入口，没有注册第二套冲突 Storage；模拟事务不会修改容器，只有已提交事务才生效。直接绕过 `Inventory`/Transfer API 修改内部槽位的模组不保证兼容。

设置新标签前，服务器检查整个逻辑容器；发现任意不匹配物品时拒绝并保留所有物品原位。大箱沿用扫描器的稳定代表位置，两半保存相同规则。两个单箱合并时，一边有标签、一边无标签且全部内容兼容会在打开或扫描时同步；内容不兼容或两边标签不同则进入冲突状态，禁止输入但允许取出，并且只能清除标签。拆掉一半后，剩余单箱保留自身标签，掉落的箱子物品不携带标签。

标签存入箱子或木桶 BlockEntity 的独立 NBT 复合项：

```text
"workshop_zone:container_label": {
  version: 1,
  mode: "workshop_zone:exact_item",
  item: "minecraft:iron_ingot"
}
```

无标签时不写这个复合项。未知版本、模式、物品 ID、`minecraft:air` 或损坏数据会记录警告并安全回退为无标签，不会阻止世界加载。

当前模组本身不提供箱子所有权，服务器应通过保护模组或 `ContainerLabelEditCallback` 兼容回调管理编辑权限。

## 运行环境

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.19.3 或更高版本
- Fabric API 0.116.15+1.21.1 或兼容的 1.21.1 版本

Fabric API 是唯一必需前置。项目没有增加 Mod Menu、Cloth Config、EMI 或其他运行时依赖。

## 工作方式

玩家成功打开受支持方块的原版 GUI 后，服务器以实际点击方块为中心扫描水平 8 格、垂直 4 格范围。扫描只访问已加载区块，也不会定时或逐 tick 重扫。标签摘要通常只读取各成员的常数大小规则；仅在合并箱标签需要自动同步时检查该逻辑容器的现有内容。

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

客户端另有一个只读 `HandledScreen` accessor，仅获取原版 GUI 的 `x`、`y`、宽和高以放置侧栏，不修改原版字段。服务端源码不引用任何客户端类。

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
```

人工测试时，在同一区域放置全部支持方块以及制箭台、漏斗和潜影盒，检查扫描、图标、Tooltip、滚动、折叠、刷新和侧边栏切换。还应检查：

- 背包、潜影盒、漏斗、村民交易和创造物品栏不显示侧栏。
- GUI Scale Auto/2/3/4，以及 16:9、16:10、4:3 窗口下不遮挡原版槽位或配方书。
- 关闭界面、远离、破坏中心方块、死亡/重生和切换维度后旧会话失效。
- 两名玩家同时打开不同工坊时数据互不混用。
- 专用服务器可以启动，控制台无 Mixin、客户端类加载或数据包异常。

## 尚未实现

- 自动补充合成材料
- 一键归仓
- 物品 Tag 类别标签、白名单与黑名单
- 物品搜索
- 自动熔炼
- 制作计划
- 固定工坊核心
- EMI 深度集成和模组机器兼容
- 跨维度访问或未加载区块访问

## License

MIT
