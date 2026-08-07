# Workshop Zone（工坊域）

![Workshop Zone Cover](src/main/resources/assets/workshop_zone/cover.png)

[简体中文](README.md) | [English](README_EN.md)

[![GitHub Release](https://img.shields.io/github/v/release/ikunkk02-afk/Workshop-Zone?label=release)](https://github.com/ikunkk02-afk/Workshop-Zone/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-success)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3%2B-blue)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

Workshop Zone 会将玩家附近的箱子、木桶和工作方块连接成一个临时工坊。玩家可以在原版 GUI 旁快速切换设备、整理带标签的仓库、搜索物品、定位容器，并在合成时安全调用附近仓库中的材料。

它不会加入新的机器或替换原版 GUI，而是在原版容器和工作设备基础上提供统一的工坊管理界面。

## 核心功能

### 工坊侧边栏

- 打开支持的容器或工作设备后自动显示附近工坊。
- 快速切换箱子、木桶、工作台、熔炉、铁砧等设备。
- 大箱作为一个逻辑容器显示，两半库存合并统计。
- 不强制加载未加载区块。

### 容器标签

- **精确物品标签**：只允许放入指定物品。
- **Item Tag 类别标签**：按 Minecraft 物品类别（如所有原木、所有木板）管理。
- **自定义多标签白名单**：混合精确物品和类别标签，最多 32 项。
- 标签限制输入，取出不受限制。
- 支持手动放入、Shift 快速移动、原版漏斗和标准 Fabric Transfer API 输入。
- 大箱两半标签自动同步。

### 一键归仓

- 自动把背包中匹配标签的物品送入对应容器。
- 精确标签优先于类别和白名单。
- 普通点击不整理快捷栏，Shift 点击包含快捷栏。
- 无标签容器不会自动接收物品。

### 工坊库存搜索

- 候选只显示当前工坊中实际存在的物品，不列出全体注册物品。
- 支持中文名称、英文名称和注册 ID 搜索。
- 显示每种物品的工坊总数量和存放容器数量。
- 点击结果打开对应容器。
- 可高亮单个或全部匹配容器（含大箱两半）。

### 仓库辅助合成

- 支持原版工作台 3×3 的所有标准有序和无序 `CraftingRecipe`。
- 玩家背包材料优先，仓库只补缺少部分。
- 普通点击原版配方：背包不足但仓库可补齐时显示确认面板，确认后每格放 1 件材料。
- Shift 点击原版配方：计算背包与仓库联合可安全准备的最大次数（最多 64 次），显示批量确认，确认后每格放入对应批次材料。
- 最多 64 次上限受库存、每格堆叠上限和硬上限共同约束。
- 确认前不移动或预留任何材料；确认时重新验证，材料减少整批失败回滚，材料增加不静默扩大。
- 只填入 3×3 合成栏，不直接生成成品；输出槽、剩余物、统计和解锁继续由原版处理。
- 批量是一次事务完成，不是循环多次单次事务。
- JEI 与 REI 的标准工作台配方转移按钮可进入相同仓库补齐流程；EMI 仍保持可选加载，但其转移接管受上游公开 API 限制。

### 响应式界面

- 支持右侧、左侧、上方、下方和自定义位置。
- 拖动标题栏空白区域可保存自定义位置。
- 适配不同窗口尺寸、GUI Scale 和语言文本宽度。
- 自动检测 JEI、EMI、REI，AUTO 模式检测到配方查看器后优先将侧栏放在上方。
- JEI、EMI 和 REI 不是强制前置。

## 支持的方块

**存储容器：**
- 箱子
- 陷阱箱
- 木桶
- 大箱（逻辑合并）

**工作设备：**
- 工作台
- 熔炉
- 高炉
- 烟熏炉
- 锻造台
- 铁砧 / 开裂的铁砧 / 损坏的铁砧
- 切石机
- 砂轮
- 织布机
- 制图台
- 酿造台
- 附魔台

**当前不支持：**
- 潜影盒
- 漏斗作为工坊存储容器（漏斗输入过滤正常支持）
- 发射器、投掷器、制箭台
- Crafter
- 模组机器

## 安装要求

**必需：**
- Minecraft 1.21.1
- Fabric Loader 0.19.3 或更高的兼容版本
- Fabric API 0.116.15+1.21.1 或更高的兼容版本
- Java 21

**可选：**
- JEI
- EMI
- REI

**安装步骤：**
1. 安装适用于 Minecraft 1.21.1 的 Fabric Loader。
2. 将 Fabric API 和 Workshop Zone JAR 文件放入 `mods` 目录。
3. 启动游戏。
4. 多人服务器需要服务端和所有玩家客户端都安装 Workshop Zone 及 Fabric API。

## 快速使用

1. 在附近放置箱子、木桶和工作设备。
2. 打开任意受支持的 GUI。
3. 在工坊侧边栏中点击切换设备。
4. 打开容器标签编辑器设置存储规则。
5. 点击归仓按钮整理背包。
6. 使用搜索按钮查找工坊物品。
7. 在工作台配方书中点击配方：背包不足时确认调用仓库材料。
8. Shift 点击配方进行批量材料准备。
9. 安装 JEI 或 REI 后，也可使用其标准工作台配方转移按钮触发相同的单次/批量补齐。
10. 在合成栏填充后，从原版输出槽取出成品。

> 当前有效扫描范围以服务端实际逻辑为准（水平 8 格、垂直 4 格）。目标需要处于已加载区块，玩家必须处于允许访问距离。模组不会绕过原版锁和保护回调。

## 配方查看器合成兼容

- **JEI 19.43.0.393** 和 **REI 16.0.799** 的标准 3×3 工作台配方转移按钮已接入 Workshop Zone。
- 普通转移对应一次仓库补齐；JEI 最大转移和 REI stacked transfer 对应批量补齐。
- 所有请求最终调用原版 `ClientPlayerInteractionManager#clickRecipe`，进入 `CraftRequestC2SPacket` 和现有服务端验证/事务链路。
- 玩家背包材料足够时保持普通原版填充，不显示 Workshop Zone 确认；只有需要仓库材料时才显示现有二次确认 Overlay。
- Workshop Zone 只填充原版合成栏，不直接生成成品，也不直接输出到光标或背包。
- 配方查看器仍是可选依赖；不安装 JEI、EMI 或 REI 时不会加载其适配类。
- 只处理拥有真实 Recipe ID 的标准 `CraftingRecipe`；不处理虚拟配方、其他机器加工配方、递归子配方或自动熔炼。
- JEI 保留公开 GUI 排除区域；REI 同样通过公开 API 注册侧栏排除区域。
- **EMI 1.1.24+1.21.1 可与本模组安全共存，但本版本不接管 EMI Fill 按钮。** EMI 公开 API 只能追加 Handler，而原版工作台 Handler 固定先注册并优先命中；在不使用 `internal`/`impl`、反射或 Mixin 的安全约束下无法可靠覆盖。

## 已知限制

- 只支持 Minecraft 1.21.1 Fabric。
- 工坊不跨维度，不访问未加载区块。
- 当前没有固定工坊核心方块。
- 搜索目录在进入搜索界面或手动刷新时更新，不每 tick 扫描。
- 仓库辅助合成只支持工作台 3×3 标准 `CraftingRecipe`。
- EMI Fill 按钮尚未接入仓库补齐；等待 EMI 提供公开的 Handler 优先级/前插 API。
- 合成栏在请求和确认时必须为空。
- 不支持递归子配方、自动熔炼、自动连续补货。
- 不支持模组机器库存。
- 不保证兼容直接绕过 `Inventory` 或 Transfer API 写槽位的模组。
- 服务端领地权限需要对应兼容回调或保护模组适配。

## 问题反馈

- 崩溃请提供 `latest.log` 或 `crash-report` 文件。
- 功能问题请提供 Minecraft 版本、Fabric Loader、Fabric API、模组列表和复现步骤。
- 使用 [GitHub Issues](https://github.com/ikunkk02-afk/Workshop-Zone/issues) 提交。
- 不要在 Issue 中上传 Token、服务器密码或私人信息。

## 开发构建

Windows：
```powershell
.\gradlew.bat clean build
```

Linux / macOS：
```bash
./gradlew clean build
```

输出目录：`build/libs`

开发运行：
```bash
# 客户端
gradlew.bat runClient

# 服务端
gradlew.bat runServer

# JEI 兼容测试
gradlew.bat runClient -Precipe_viewer=jei

# EMI 安全共存测试（本版本不接管 Fill 按钮）
gradlew.bat runClient -Precipe_viewer=emi

# REI 兼容测试
gradlew.bat runClient -Precipe_viewer=rei
```

- 正式运行不强制要求任何配方查看器。
- Java 21 必需构建和运行。

## 技术文档

面向开发者、维护者和兼容模组作者的详细技术说明请参阅 [docs/TECHNICAL.md](docs/TECHNICAL.md)。

## 许可证

Workshop Zone 使用 [MIT License](LICENSE)。

作者：寿云
