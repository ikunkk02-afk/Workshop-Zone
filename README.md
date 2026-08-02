# Workshop Zone（工坊域）

Workshop Zone 是一个处于早期开发阶段的 Fabric 模组。它在服务器端扫描玩家当前打开的工作区域，并在原版容器或工作方块界面旁显示只读设备列表。

## 当前进度

- Phase 1 / Step 1：服务器端附近工作区域扫描器——已完成
- Phase 1 / Step 2：打开支持方块时自动扫描、同步结果并显示只读工坊侧边栏——已完成

当前支持箱子、陷阱箱、木桶、工作台、熔炉、高炉和烟熏炉。相连的大箱子只计为一个逻辑容器；漏斗、潜影盒、发射器、投掷器和模组机器不参与扫描。

## 运行环境

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.19.3 或更高版本
- Fabric API 0.116.15+1.21.1 或兼容的 1.21.1 版本

Fabric API 是唯一必需前置。项目没有增加 Mod Menu、Cloth Config、EMI 或其他运行时依赖。

## 工作方式

玩家成功打开受支持方块的原版 GUI 后，服务器以实际点击方块为中心扫描水平 8 格、垂直 4 格范围。扫描只访问已加载区块，不读取容器物品，也不会定时或逐 tick 重扫。

服务器为每名玩家保存一个仅存在于内存中的临时会话。关闭 GUI、断线、死亡、重生、切换维度、离开有效距离或中心方块失效时，会话会被清理。侧栏条目按打开方块到设备方块中心的距离从近到远显示；点击条目不会打开或切换容器。

侧栏提供收起/展开和手动刷新。刷新请求只能引用当前 session ID 与 ScreenHandler sync ID，服务器会重新验证会话、维度、距离及中心方块，并强制执行一秒冷却。

## Mixin 说明

Fabric API 1.21.1 没有同时提供“方块位置”和“GUI 已成功打开”的服务端事件，因此使用两个最小服务端 Mixin：

- `ServerPlayerInteractionManager#interactBlock`：在 `HEAD` 记录旧 ScreenHandler，在 `RETURN` 确认原版交互确实打开了与受支持方块匹配的新 ScreenHandler。
- `ServerPlayerEntity#onHandledScreenClosed`：在 `HEAD` 清理当前 sync ID 对应的临时会话。

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

人工测试时，在同一区域放置单箱、大箱、陷阱箱、木桶、工作台、熔炉、高炉、烟熏炉、漏斗和潜影盒，分别打开七种支持界面并检查数量、排序、图标、自定义名称、Tooltip、滚动、折叠和刷新。还应检查：

- 背包、潜影盒、漏斗、村民交易和创造物品栏不显示侧栏。
- GUI Scale Auto/2/3/4，以及 16:9、16:10、4:3 窗口下不遮挡原版槽位或配方书。
- 关闭界面、远离、破坏中心方块、死亡/重生和切换维度后旧会话失效。
- 两名玩家同时打开不同工坊时数据互不混用。
- 专用服务器可以启动，控制台无 Mixin、客户端类加载或数据包异常。

## 尚未实现

- 点击侧栏切换或远程打开容器
- 自动补充合成材料
- 箱子标签
- 一键归仓
- 物品搜索
- 自动熔炼
- 固定工坊核心
- EMI 深度集成和模组机器兼容
- 跨区块强制加载或跨维度访问

## License

MIT
