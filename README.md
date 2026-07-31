# Workshop Zone（工坊域）

Workshop Zone 是一个处于早期开发阶段的 Fabric 模组。目前仅实现服务器端附近工作区域扫描器，用于发现玩家附近已加载区块中的常用容器和工作设备。

## 当前进度

Phase 1 / Step 1：服务器端工作区域扫描器。

## 运行环境

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.19.3 或更高版本
- Fabric API 0.116.15+1.21.1 或更高的 1.21.1 兼容版本

Fabric API 是唯一必需的模组前置。本项目没有加入 Mod Menu、Cloth Config、EMI 或其他大型运行时库。

## 测试命令

在单人世界中，或以专用服务器权限等级 2 的玩家身份运行：

```text
/workshopzone scan
```

命令以玩家脚部方块位置为中心，扫描水平方向前后左右各 8 格、垂直方向上下各 4 格。最多显示前 20 个结果。

## 当前支持的方块

容器：

- 箱子
- 陷阱箱
- 木桶

工作设备：

- 工作台
- 熔炉
- 高炉
- 烟熏炉

相连的大箱子会作为一个逻辑容器返回。漏斗、潜影盒、发射器、投掷器和模组机器不会被扫描。

## 尚未实现

- GUI 快速切换
- 箱子标签、物品搜索、自动归仓
- 自动补充配方、自动熔炼
- 固定工坊核心、远程或跨维度访问
- EMI 集成和多人权限系统

## 构建

Windows：

```powershell
.\gradlew.bat clean build
.\gradlew.bat runGameTest
```

开发客户端：

```powershell
.\gradlew.bat runClient
```

## License

MIT
