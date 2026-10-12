# 超越维度-等价交换拓展（Beyond EMC Expansion）→ NeoForge 1.21.1 移植手册

> 本文件与 `src/` 源码、r24 旧 jar 打包交付。
> 用途：在 **新建的「Minecraft 1.21.1 + NeoForge」AutoMods 工程**里，把本模组从
> Forge 1.20.1 移植过去。旧工程（本包来源）的工具链已锁死在 Forge 1.20.1，无法中途换版本。

## 0. 目标与配套依赖

> **移植前必读（本仓库 r31 清理记录）**：本手册正文是按 r24 源码写的，
> 当前 `src/` 已做过一次整体清理，以下几项**已经不存在**，按新工程落地时不要再找：
> - `client/NetworkScreen.java`（独立功能槽界面）及 `ClientState.depositHeld()`：
>   已删除，EMC 功能槽只挂在超越维度原生界面（`BeyondNetworkIntegration`）上；
> - `NetworkStore` 的本地暂存仓库 `items[]`、`totalCount()`、按来源取出的
>   `withdraw(player, request, source)`：已删除。存不进网络的物品现在直接留在玩家背包，
>   老存档遗留的暂存条目在登录时由 `NetworkStore.refundLegacyItems` 退还；
> - `ModNetwork` 的 `ItemEntry`/`MAX_SYNC_ITEMS`/`StatusPacket.items`：状态包只发 5 个
>   布尔开关，不再同步物品列表；`ActionPacket` 动作 3、4 已废弃（协议版本 "4"）；
> - `OptionalBridges` 里的旧 API 名：大写 `Api.*` 包名候选与 `ItemStackType` 兼容分支
>   （正文 §2.1 提到的"可选删除"已经删掉了）、`withdrawProjectE`/`withdrawBeyond`/
>   `beyondItems` 取物接口也一并删除（取出统一走超越维度原生界面 + 存/取拦截器）；
>   反射构造器（ItemStackKey / KeyAmount / 两个 ReturnInfo）改为解析一次后复用。
> - `stackKey` 收敛到 `NetworkStore.stackKey`（数字注册 ID + NBT 文本，不落盘）。

- 目标版本：**Minecraft 1.21.1 / NeoForge 21.1.x / Java 21**。
- 配套模组（模组作者已上传、新工程应继续引用同样的 jar 做 API 核对）：
  - `beyonddimensions-1.21.1-neoforge-0.7.30.jar`（超越维度，下称 BD）
  - `ProjectE-1.21.1-PE1.1.0.jar`（等价交换重制版，下称 PE）
- 建议版本号从 `1.0.0-r25` 起编，产物名保持 `beyond_emc_expansion`。
- 模组对 BD/PE **没有编译期依赖**，全部走反射桥接（`OptionalBridges.java`），
  因此移植工作量集中在：构建脚本、Forge→NeoForge 事件/网络包名、1.21 数据组件三块。

## 1. 模组功能速览（给新会话的 AI 看）

- 合成「Access Rune（通行符文）」解锁 EMC 网络功能（`AccessRuneItem`，右键解锁/开关）。
- 在超越维度原生网络界面（`DimensionsNetGUI`）左侧挂一个「E」功能按钮 + 弹出面板
  （`BeyondNetworkIntegration`）：开关、优先级（网络/转化桌）、NBT 过滤三个切换。
- 存入路由（`NetworkStore.deposit` → `OptionalBridges`）：
  - 转化桌优先：物品换成 PE 的 EMC 入账并学习知识，余额进「影子账本」写回 BD 网络，
    让 BD 界面里能直接取出 EMC 兑换出来的物品；
  - NBT 过滤开：带附加数据的物品不进转化桌；关：交给 PE 自己定价兑换。
  - 兑换规则（`resolveExchange`）：附魔/耐久这类不增值数据→按裸物品学习入账；
    药水之箭这类"NBT/组件就是物品本身"→原样学习（防止被抹成空白箭）。
- 每次兑换在动作栏提示「已送入转化桌：×n → EMC +x」（`chat.emc_network.converted`）。
- 玩家存档数据全在 `player.getPersistentData()` 的 `PlayerPersisted` 子标签里：
  `emc_network_access`（开关/优先级/NBT过滤/本地仓库 items[]）+
  `emc_virtual_items`、`emc_virtual_physical_items`（影子/实体账本）。
- 周期同步：`ServerEvents.onPlayerTick` 每 20 tick 调 `syncProjectEIntoBeyond`，
  检测 BD 原生界面取出（影子减少）并按 EMC 扣账，防止物品被同步补回。

## 2. 已核实事实（来自反编译上面两个 1.21.1 jar，可直接信任）

### 2.1 超越维度 BD 0.7.30：反射桥接目标全部存在，包名走小写 `api.*` 分支

`OptionalBridges.java` 里每个候选类名数组的**第二个**（小写包名）与 0.7.30 完全一致：

| 用途 | 0.7.30 实际类/签名（已核实） |
|---|---|
| 取网络 | `com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet`：静态 `getNetFromPlayer(Player)`、`getPrimaryNetFromPlayer(Player)`；实例 `getUnifiedStorage()`、`getPlayers()`→`Set<UUID>` |
| 存储 | `...api.dimensionnet.UnifiedStorage extends ...impl.UnorderedStackHandlerRemoveZero`：`getStorage()`→`List<KeyAmount>`；`insert(IStackKey,long,boolean)`→KeyAmount(剩余)；`extract(IStackKey,long,boolean,boolean)`→KeyAmount；`getStackByKey(IStackKey)`→KeyAmount；`setAmountByKey(IStackKey,long)`→long；`onChange()` |
| 键 | `...api.storage.key.impl.ItemStackKey(ItemStack)` 公共构造器；`...api.storage.key.KeyAmount(IStackKey,long)` record：`key()`/`amount()`/`toStack()`/`isEmpty()` |
| 存前拦截 | `...api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler`：静态 `addHandler(BeforeInsertHandler)`；回调 `beforeInsert(KeyAmount original, KeyAmount rolling, DimensionsNet net)`；`BeforeInsertHandlerReturnInfo(KeyAmount, boolean cancel)`。注意：**第一个被调到的 handler 里 args[0]==args[1]**，现有 `routeNativeInsert` 取 `args[1]`/`args[2]` 的写法继续成立；返回 amount=0 的 KeyAmount 即"整单吞掉"（`isEmpty()` 时 BD 停止插入） |
| 取前拦截 | `...helper.UnifiedStorageBeforeExtractHandler`：同上，`beforeExtract(KeyAmount, KeyAmount, DimensionsNet)`，ReturnInfo 构造器 `(KeyAmount, boolean)` |
| 界面类名 | `com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI`（`BeyondNetworkIntegration.isDimensionsNetwork` 的匹配串不变） |

→ BD 侧反射名单**不需要改**。可选：把 1.20.1 大写 `Api.*` 候选名删掉减小噪音（不删也无害）。

### 2.2 ProjectE PE1.1.0：**取代理的入口变了（移植必改点）**

- `moze_intel.projecte.api.ProjectEAPI` 现在只剩常量，**没有 `getEMCProxy()`/`getTransmutationProxy()` 静态方法**。
  现有 `projectEProxy()`/`projectEProvider()` 在 1.20.1-PE 上靠这两个方法，到 PE1.1.0 必须加新获取路径。
- 新入口（已核实均存在）：
  - `moze_intel.projecte.impl.EMCProxyImpl`：**公共无参构造器**，无字段；
    `getValue(ItemInfo)`→long（内部 `DataComponentManager.getEmcValue`）；
    接口 default 方法 `getValue(ItemStack)`/`getValue(ItemLike)` 仍可用。
  - `moze_intel.projecte.impl.TransmutationProxyImpl`：**公共无参构造器**，无字段；
    `getKnowledgeProviderFor(UUID)`→`IKnowledgeProvider`（服务端内部：`ServerLifecycleHooks.getCurrentServer()`
    →`PlayerList#getPlayer(uuid)`→`player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)`；
    注意它要求 `EffectiveSide.isServer()`，本模组只在服务端调用，成立）。
  - 兜底入口：`moze_intel.projecte.api.capabilities.PECapabilities.KNOWLEDGE_CAPABILITY`
    （`EntityCapability<IKnowledgeProvider, Void>` 公共静态字段）。
- `moze_intel.projecte.api.capabilities.IKnowledgeProvider`（已核实签名）：
  - `Set<moze_intel.projecte.api.ItemInfo> getKnowledge()`（不再是 ItemStack 集合）
  - `boolean hasKnowledge(ItemStack)`、`boolean addKnowledge(ItemStack)`（default 方法，反射按名解析可命中）
  - `BigInteger getEmc()` / `void setEmc(BigInteger)` / `void sync(ServerPlayer)`（与现有反射调用一致）
- `moze_intel.projecte.api.ItemInfo`：`createStack()`、`itemOnly()`、`fromStack(ItemStack)`、
  `hasModifiedComponents()`、`getComponentsPatch()` —— 现有 `projectEItems()` 的
  `tryInvoke(value,"createStack")` 分支正好命中，学习/取出逻辑不用重设计。
- **移植写法**：`projectEProxy()` 依次尝试①旧版 `ProjectEAPI.getEMCProxy`（保兼容）②反射
  `Class.forName("moze_intel.projecte.impl.EMCProxyImpl").getConstructor().newInstance()`；
  `projectEProvider(player)` 同理①`ProjectEAPI.getTransmutationProxy`②`TransmutationProxyImpl` 实例。
  `EMC/知识` 的方法名候选（`addKnowledge`/`hasKnowledge`/`getEmc`/`setEmc`/`sync`）都不用改。

## 3. 逐文件移植清单（新工程里做）

### 3.1 构建与元数据

- `build.gradle`/`settings.gradle`/`gradle.properties`：**用新工程骨架生成的 NeoForge 21.1 脚本**，
  只需把 `version='1.0.0-r25'`、`archivesName='beyond_emc_expansion'`、Java 21 保留；
  `processResources` 里的 expand 目标改成 `META-INF/neoforge.mods.toml`。
- `META-INF/mods.toml` → 改名 `META-INF/neoforge.mods.toml`：
  `modLoader="javafml"` 保留；`loaderVersion="[4,)"`；
  依赖里 `modId="forge"` → `modId="neoforge"`，`versionRange="[21.1.x,)"`（跟新骨架给的值）；
  `minecraft versionRange="[1.21.1]"`。
- `pack.mcmeta` 用新骨架生成的（1.20.1 手写的 `pack_format 15` 不要带过去）。
- 配方 JSON（两份）：1.20.5+ 产物字段改名——`"result": {"item": ...}` → `"result": {"id": ...}`，
  `key`/`pattern` 里的 `{"item": ...}` 不变。其余 data 路径不变。
- `assets/emc_emc_nbt_emc_8_emc_em/**`（lang、models、textures）与 icon.png/logo.png 原样复制。

### 3.2 Java：包名/事件迁移（全部走 mc_api 查证后再写，下列为方向）

- `net.minecraftforge.fml.common.Mod` → `net.neoforged.fml.common.Mod`；
  构造器改为注入 `(IEventBus, ModContainer)`；`@Mod.EventBusSubscriber` →
  NeoForge 的 `@EventBusSubscriber`（bus 常量、Dist 用法用 `mc_api guide`/`example` 查）。
- `GeneratedMod.java`：`ForgeRegistries.ITEMS` → `Registries.ITEM`；
  `RegistryObject` → `DeferredHolder`/`DeferredItem`；`BuiltInRegistries.CREATIVE_MODE_TAB.key()` 用法不变。
- `ServerEvents.java`：
  - `TickEvent.PlayerTickEvent(END)` → `net.neoforged.neoforge.event.tick.PlayerTickEvent.Post`；
  - `PlayerEvent.Clone` → NeoForge 1.21 已改为实体转换事件（查 `mc_api search-method Clone / ConvertIntoPlayer`，
    取旧玩家 `getPersistentData()` 的逻辑不变，只换事件与 `getOriginal()` 访问名）；
  - `PlayerLoggedInEvent` 包名换 `net.neoforged.neoforge.event.entity.player.PlayerEvent$...`。
- `ModNetwork.java`（工作量最大）：`DistExecutor.unsafeRunWhenOn` 在 1.21 已移除。
  推荐直接换 NeoForge payload API：
  - `RegisterPayloadHandlersEvent`（mod 总线）里 `registrar.playToServer/playToClient(...)`；
  - `ActionPacket`/`StatusPacket` 增加 `CustomPacketPayload.Type<...>` + `StreamCodec`
    （内容就是现有 encode/decode 的字段顺序）；
  - 处理器 `RegistrationPayloadContext`：`ctx.enqueueWork(...)`，发送方 `ctx.player()`；
  - 发状态包：`serverPlayer.connection.send(payload)`；不再需要 SimpleChannel/PacketDistributor；
  - 客户端处理器类保持 @DistExecutor-safe：playToClient 的 handler 引用只写在客户端类
    （新工程骨架示例里应有标准写法，照抄）。
- `AccessRuneItem.java`：`appendHoverText` 新签名多了 `Item.TooltipContext`
  （用 mc_api class net.minecraft.world.item.Item 查证参数表）；其余不变。
- `BeyondNetworkIntegration.java` / `NetworkScreen.java`：
  - 事件包 `net.neoforged.neoforge.client.event.ScreenEvent$Init$Post / MouseButtonPressed$Pre /
    ClientPlayerNetworkEvent$LoggingIn`；`setCanceled`、`getListenersList`、`addListener`
    逐个用 mc_api 验证 21.1 的名字（个别可能改为 `ScreenEventData`/非可取消风格）；
  - `EditBox.setHint`：Forge 扩展，NeoForge 是否保留要查；不保留就自己画占位文本；
  - `AbstractWidget.getHeight()`：1.21.1 应还在（1.21.2 才移除），确认后不用改；
  - 布局常量、面板尺寸逻辑与 1.20.1 版完全一致，别顺手"优化"。

### 3.3 Java：物品"附加数据"从 NBT 改为数据组件（核心语义）

1.21.1 的 `ItemStack` 没有 `hasTag()/getTag()/setTag()`。本模组语义映射：

- 「这件物品带附加数据吗」（NBT 过滤的判据）：
  `hasTag()` → `!ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()))`
  （附魔、耐久损耗、药水之箭的 potion_contents、重命名全部命中）。
- 「裸物品版本」：`copyWithCount(1)` + `setTag(null)` → 直接 `new ItemStack(input.getItem())`。
- 匹配：`ItemStack.isSameItemSameTags(a,b)` → `isSameItemSameComponents(a,b)`（NetworkStore.matches、
  ModNetwork 存入比对两处）。
- 影子账本序列化（NetworkStore、OptionalBridges.syncProjectEIntoBeyond、ServerEvents.onClone）：
  `stack.save(new CompoundTag())`/`ItemStack.of(tag)` 在 1.21 需要注册表上下文——
  用 `stack.saveOptional(player.registryAccess())`（DataResult）与
  `ItemStack.parseOptional(player.registryAccess(), entry.getCompound("stack"))`，
  **确切方法名以新工程 mc_api class ItemStack 为准**（1.20.5+ 走 CODEC 路线）。
  账本 CompoundTag 本身（PlayerPersisted 里那些 ListTag）仍是普通 NBT，逻辑不变。
- `NetworkStore.stackKey`：改用「物品ID + 组件序列化串」做去重键
  （可用 `ItemStack.saveOptional(...)` 结果 toString；保证同物品同数据同键）。
- 其他 1.21.1 常识项（逐项 mc_api 验证，别凭记忆写）：
  `new ResourceLocation(ns,path)` 构造器私有 → `ResourceLocation.fromNamespaceAndPath`；
  `net.minecraftforge.server.ServerLifecycleHooks` → `net.neoforged.neoforge.server.ServerLifecycleHooks`；
  `SoundEvents.NOTE_BLOCK_CHIME` 现在直接是 `Holder<SoundEvent>`，确认 `.value()` 的适用面。

## 4. 移植完成后必做的游戏内回归（给用户的验收表）

1. 创造模式拿 Access Rune 右键解锁；合成配方（金块+贤者之石/紫水晶）能出符文。
2. 打开超越维度网络界面：左侧出现 E 按钮，面板三项开关可用、不遮挡原生按钮。
3. Shift+点击背包物品存入：转化桌优先时 EMC 增加、动作栏出现「已送入转化桌」提示。
4. **药水之箭一组**存→在 BD 界面仍能看到并取回（不能消失）。
5. 附魔剑（NBT 过滤开=不进转化桌；关=按裸剑价入账）。
6. BD 原生界面取出影子物品后，EMC 相应扣减，且不会一分钟后被同步补回。
7. 重进世界/死亡重生后（PlayerEvent.Clone→1.21 转换事件），解锁状态与账本保留。

——打包于 Forge 1.20.1 工程（r24）。新工程拿到本包后：先复制 `src/`，
按 §3 逐项迁移，§2 的依赖事实无需重查，其余不确定项全部用新工程 mc_api 验证。
