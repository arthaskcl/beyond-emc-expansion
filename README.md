# Beyond EMC Expansion（超越维度-等价交换拓展）

A Minecraft **Forge 1.20.1** mod that connects [ProjectE](https://www.curseforge.com/minecraft/mc-mods/projecte)'s EMC network
to the **Beyond Dimensions** network GUI: one extra slot on the left side of the network screen handles deposit / routing
for you, with toggles for access, storage priority, NBT filtering and conversion feedback.

一个 Minecraft **Forge 1.20.1** 模组，把「等价交换 ProjectE」的 EMC 网络接进「超越维度 Beyond Dimensions」的网络界面：
界面左侧多一个 EMC 功能槽，负责存入与路由，并提供接入开关、存储优先级、NBT 过滤、兑换提示四个开关。

| | |
|---|---|
| Mod id | `emc_emc_nbt_emc_8_emc_em` |
| Version | `1.0.0-r31` |
| Game | Minecraft 1.20.1 |
| Loader | **Forge** `47.4.0` or newer 47.x (`loaderVersion [47,)`) |
| Java | 17 |
| Runtime mods | Beyond Dimensions, ProjectE (bridged **by reflection**, no compile-time dependency) |
| License | All Rights Reserved |

---

## Features

- **EMC Network Access Rune** — craft it (8 gold ingots around the Philosopher's Stone, or a standalone recipe that works
  without ProjectE installed). Right-click once to unlock the slot permanently, right-click again to toggle access.
- **"E" button on the left of the Beyond Dimensions network GUI** opens a small panel with 4 switch rows
  (click a row to toggle it):
  - `Access: On/Off` — whether items are routed into the EMC network at all
  - `Priority: Network / Alchemy Table` — where items go first
  - `NBT Filter: On/Off` — whether items carrying NBT data are skipped
  - `EMC Convert Hint: On/Off` — shows `Sent to alchemy table: <item> xN -> EMC +value` in the action bar
- **Shift-click** deposits a single stack; **double-click the same slot** deposits all matching items.
- **Per-player settings** saved in the player's own data, so they survive relog and server restart.
- Items that cannot enter the network simply stay in your inventory; leftovers from an older temporary storage are
  refunded on login (`message.emc_network.refunded`).

---

## Install

1. Install Minecraft Forge `47.4.0` (or newer 47.x) for Minecraft 1.20.1.
2. Put `beyond_emc_expansion-1.0.0-r31.jar` into `.minecraft/mods/`.
3. Also install Beyond Dimensions and ProjectE for 1.20.1 — the mod loads without them, but the EMC features need them.

## Build from source

```bash
gradle build        # or ./gradlew build if you add a wrapper yourself
```

The jar lands in `build/libs/beyond_emc_expansion-1.0.0-r31.jar`.

Requirements: JDK 17 and Gradle 8.x (this project was built with Gradle 8.12.1 + ForgeGradle 6.0.x, so Gradle must be
able to reach `https://maven.minecraftforge.net/`). This archive intentionally contains **no Gradle Wrapper** and no
`run/` client-launcher configuration — it is a plain source package.

---

## Project layout

```
build.gradle / settings.gradle / gradle.properties   build configuration (ForgeGradle 6.0.x, mojmap official)
src/main/java/cn/blockforge/generated/emcemcnbtemc8emcem/
    GeneratedMod.java            mod entry, item registry, creative tab
    AccessRuneItem.java          the unlock / toggle item
    ModNetwork.java              SimpleChannel, protocol version "4"
    NetworkStore.java            per-player persisted settings, stack keys, legacy refund
    ServerEvents.java            login / player-tick / death-copy events, refund + status push
    OptionalBridges.java         reflection bridges to Beyond Dimensions & ProjectE
    client/BeyondNetworkIntegration.java   the "E" slot and its switch panel
    client/ClientEvents.java     client login hook (asks the server for the switches)
    client/ClientState.java      client copy of the switches + chat keys
src/main/resources/
    META-INF/mods.toml           mod metadata (version placeholders from build.gradle)
    assets/emc_emc_nbt_emc_8_emc_em/  lang (en_us, zh_cn), item model, item texture
    data/emc_emc_nbt_emc_8_emc_em/    crafting recipes
    icon.png / pack.mcmeta
PORTING_1.21.1_NEOFORGE.md       hand-over manual for a 1.21.1 / NeoForge port
publish-info.md                  CurseForge / Modrinth page copy (EN/CN)
```

---

## What changed from r27 to r31

**EN**
- **r29 — new "EMC Convert Hint" switch.** The panel gained a 4th row; turning it on prints the EMC gained for every item
  fed into the alchemy table, turning it off silences only the message (conversion itself is untouched). Saved per player,
  defaults to On, so existing worlds behave exactly as before.
- **r31 — cleanup and safety pass, gameplay unchanged.** Dead code removed (standalone screen, local item buffer and its
  withdraw APIs, item list sync), reflection objects resolved once and reused, protocol slimmed to 5 booleans, status
  messages and language files de-duplicated.

**中文**
- **r29 新增「兑换EMC提示」开关。** 面板多了第 4 行：开启时每件存入转化桌的物品都会在动作栏提示换到了多少 EMC，
  关闭只是不再刷屏，兑换过程和 EMC 数量完全不变。按玩家单独存档，默认开启，老世界行为与升级前一致。
- **r31 清理与加固，玩法不变。** 删除无用代码（独立界面、本地暂存仓库与相关取物接口、物品列表同步），
  反射对象改为解析一次后复用，状态包精简为 5 个布尔值，提示文案与语言文件去重。

---

## Notes

- This package was exported from the repository at commit `69813f9` (`main`), source files only — no `.git`,
  no build output, no run/test data.
- Beyond Dimensions and ProjectE are third-party mods and are **not** included here; the bridges in
  `OptionalBridges.java` reach them purely through reflection.
