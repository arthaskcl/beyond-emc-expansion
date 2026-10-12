# 超越维度 - 等价交换拓展（Beyond EMC Expansion）

Minecraft **1.21.1** + **NeoForge** 模组源码包。为「超越维度（Beyond Dimensions）」网络接入「等价交换（ProjectE）」的 EMC：物品兑换 EMC、EMC 网络面板、功能开关（含兑换提示开关）。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.244（或同主版本更新版） |
| Java | 21 |
| Gradle | 8.12+（本包不含 wrapper，需用本机 Gradle） |
| ModDev 插件 | net.neoforged.moddev 2.0.143 |

前置模组均为**可选**：`beyonddimensions`（≥0.7）、`projecte`（≥1.1.0）。没装也能进游戏，只是对应功能不生效。

## 编译

```bash
gradle build
```

产物：`build/libs/beyond_emc_expansion-1.0.0-r3.jar`，放进游戏目录的 `mods/` 文件夹即可（需同时装 NeoForge 1.21.1）。

> 提示：`gradle.properties` 里的 `org.gradle.java.installations.paths` 是本容器内的 JDK 路径，你自己机器上可以直接删掉这一行，Gradle 会自动找本机 JDK。

## 目录结构

```
beyond-emc-expansion-main/
├── build.gradle / settings.gradle / gradle.properties   构建配置
├── src/main/java/cn/blockforge/generated/emcemcnbtemc8emcem/
│   ├── GeneratedMod.java            模组入口、注册
│   ├── ModNetwork.java              网络通道与包（客户端↔服务端）
│   ├── NetworkStore.java            每个玩家的网络/开关存档数据
│   ├── OptionalBridges.java         对超越维度、等价交换的可选桥接
│   ├── AccessRuneItem.java         「访问符文」物品
│   ├── ServerEvents.java            服务端事件（EMC 入账、消耗）
│   └── client/                      客户端：界面、状态、集成
│       ├── BeyondNetworkIntegration.java
│       ├── ClientEvents.java
│       ├── ClientState.java
├── src/main/resources/
│   ├── META-INF/neoforge.mods.toml  模组元数据
│   └── assets/ + data/              中英文语言文件、模型、贴图、配方
├── docs/migration-verification.md   移植核对记录
└── ref/port-kit/                    原版（r24）移植参考包与移植手册
```

## 包 ID / 命名空间

- mod id：`emc_emc_nbt_emc_8_emc_em`
- 语言键前缀、资源路径均使用该命名空间

## 当前版本

`1.0.0-r3`

- 超越维度网络界面的「E」面板增加第 4 个开关：**显示 EMC 兑换提示**（关闭后只隐藏动作条提示，EMC 入账、物品消耗与其余开关不受影响）
- 通道版本 `2 → 3`；新增客户端→服务端 action id `7`；`StatusPacket` 多带一个布尔值
- 全量清理无用代码与资源，精简打包体积
- 旧存档升级后该开关**默认开启**，行为与升级前一致

## 许可

All Rights Reserved。
