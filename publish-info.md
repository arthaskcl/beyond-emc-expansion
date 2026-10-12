# 发布页信息（超越维度-等价交换拓展 / Beyond EMC Expansion）

## Summary

Beyond EMC Expansion links ProjectE's EMC network to the Beyond Dimension UI via a craftable Access Rune.
```

## Class（项目类别）

选 **Mods / Mod（模组）**。
不要选 Modpacks（整合包，指一整套模组打包）、Addons（附属包，指资源包/材质/地图附属）。
本项目是独立的 Forge 模组，虽然配合 ProjectE 与 Beyond 使用，但本体就是 Mod。

## Main category（主分类）

推荐 **Management（管理/存储）**。
理由：这个模组的核心是物品存放路由与优先级管理——和 Applied Energistics 2、Refined Storage
这类存储网络模组同属一个分类。

如果选项里没有 Management，按含义就近选：
- **Technical / Technology（技术）** —— 次选
- **Utility & QoL（实用/生活质量）** —— 再次选

## Additional categories（附加分类，可多选）

勾选以下几项：

- **Technology / Technical（技术）**：接入 ProjectE EMC 科技/魔法体系
- **Utility & QoL（实用/QoL）**：在超越维度界面里直接调用 EMC 合成，属于便利性功能
- **Information（信息）**：面板提供接入开关、状态与网络信息展示

不要勾：Adventure and Roleplaying（冒险RPG）、Maps / Worlds(地图)、Minigames（小游戏）、
Server Utility（服务端管理）、Performance（性能优化）。

## 功能描述（中文）

超越维度-等价交换拓展（Beyond EMC Expansion）把 ProjectE 的 EMC 网络和超越维度（Beyond / Dimensions Net）的网络界面打通：在超越维度网络界面里直接接入、调用和路由 EMC 物品，存取两边账目一致。

**EMC 网络接入符**
- 合成：工作台中心放贤者之石，外围 8 格各放 1 个金锭（无 ProjectE 时可用紫水晶 shard 配方兜底）。
- 右键使用永久解锁 EMC 功能槽；之后再次右键可随时开关接入。
- 带反馈：解锁时金色粒子 + 信标音，开关联动清脆/低沉提示音。

**原生界面接入**
- 解锁后，超越维度网络界面左侧按钮栏出现 "E" 按钮（紧贴原生功能按钮左边），点击展开紧凑控制面板。
- 面板内三行状态可直接点击切换：接入开关、存放优先级、NBT 过滤；开启时金边、关闭时灰边。
- 不显示物品列表，面板极小，不与原界面重合。

**双向 EMC 路由**
- 开启接入后，EMC 网络中的物品同步进超越维度网络，可在维度界面中直接调用取出。
- 取出按物品 EMC 价值 1:1 扣除 EMC，存入则等值增加 EMC，总量始终和转化桌账目一致。
- 在维度网络界面 Shift+点击背包物品，按当前优先级自动存放。

**存放优先级（循环切换）**
- 转化桌优先：物品先进 ProjectE EMC 系统（转化桌），放不下再进超越维度网络；未学习的物品自动学习。
- 网络优先：物品优先存入维度网络，EMC 系统物品仍可正常查看与调用。

**NBT 过滤（默认开启）**
- 带 NBT 数据的物品不会被熔解成 EMC，而是原样物理存入超越维度网络，仍可调用。

**持久化**
- 解锁状态、开关、优先级与过滤设置都保存在玩家数据里，重进服务器依然有效。

依赖：Minecraft 1.20.1 / Forge 47.x，需配合 ProjectE 与 Beyond（超越维度）使用。

## Feature Description (English)

Beyond EMC Expansion connects ProjectE's EMC network with the Beyond mod's Dimensions Net GUI: access, withdraw and route EMC items right from the Beyond network screen, with the EMC balance always kept in sync.

**EMC Network Access Rune**
- Craft the Access Rune with a Philosopher's Stone in the center surrounded by 8 Gold Ingots (an Amethyst Shard fallback recipe is included).
- Right-click it to permanently unlock the EMC function slot; right-click again anytime to toggle network access.
- Feedback included: golden particles and a beacon chime on unlock, distinct sounds when toggling on/off.

**Native UI integration**
- Once unlocked, an "E" button appears on the left button bar of the Beyond Dimensions Net screen, right next to the native buttons. Clicking it opens a compact control panel.
- Three status lines in the panel are click-to-toggle: access on/off, storage priority and NBT filtering. A golden border marks active, grey marks disabled.
- No item list is drawn — the panel stays tiny and never overlaps the original UI.

**Two-way EMC routing**
- With access enabled, items known to the EMC network are synced into the Beyond network and can be called out directly from the dimension screen.
- Withdrawing spends the item's exact EMC value; depositing adds it back — totals always match the Table of Coalescence.
- Shift-click an inventory item inside the dimension screen to deposit it through the current priority rule.

**Storage priority (cycling)**
- Table first: items go into the ProjectE EMC system first and fall back to the Beyond network; unlearned items are learned automatically.
- Network first: items are stored in the dimension network first, while EMC-system items stay visible and callable.

**NBT filtering (on by default)**
- Items carrying NBT data are never emc'd away; they are stored physically in the Beyond network and remain callable.

**Persistence**
- Unlock state, toggles, priority and filter settings are saved per player and survive relogs.

Requires Minecraft 1.20.1 / Forge 47.x, together with ProjectE and Beyond.

## 备注

- 各平台选项措辞可能与 CurseForge 略有出入，按含义选最接近的即可。
- 版本号填 Minecraft 1.20.1 / Forge 47.x，加载器选 Forge。
