package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** ProjectE 与 Beyond Dimensions 的运行时桥接。 */
public final class OptionalBridges {
    private static final Object NO_RESULT = new Object();
    /** 反射解析失败的哨兵，避免同一签名反复扫描方法表。 */
    private static final Object MISS = new Object();
    private static final String VIRTUAL_ITEMS = "emc_virtual_items";
    private static final String PHYSICAL_ITEMS = "emc_virtual_physical_items";
    private static final String PROJECTE_API = "moze_intel.projecte.api.ProjectEAPI";
    /** 数量读取器候选名（KeyAmount.amount() 优先），提为常量避免每次调用重建数组。 */
    private static final String[] AMOUNT_ACCESSORS = {"amount", "getAmount", "getStackAmount"};
    // 以下类名按超越维度 0.7.30（1.20.1 与 1.21.1 两个构建实测一致）写死；
    // 早期大写包名 Api.DataBase.* 与旧 ItemStackType 接口在本模组支持的目标里都不存在，已删除。
    private static final String BD_NET_CLASS = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet";
    private static final String BD_ITEM_KEY_CLASS = "com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey";
    private static final String BD_KEY_AMOUNT_CLASS = "com.wintercogs.beyonddimensions.api.storage.key.KeyAmount";
    private static final String BD_INSERT_HANDLER_CLASS =
            "com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler";
    private static final String BD_EXTRACT_HANDLER_CLASS =
            "com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeExtractHandler";
    private static boolean beyondInsertHandlerRegistered;
    private static boolean beyondExtractHandlerRegistered;
    /** 点击/存取的热点路径上每次都要用的构造器，解析一次后长期复用。 */
    private static Constructor<?> itemKeyConstructor;
    private static Constructor<?> keyAmountConstructor;
    private static Constructor<?> insertReturnConstructor;
    private static Constructor<?> extractReturnConstructor;

    /** 类查找结果缓存（含"不存在"结果），避免每秒反复触发 ClassNotFoundException 构建堆栈。 */
    private static final Map<String, Object> CLASS_CACHE = new ConcurrentHashMap<>();
    /** 反射方法解析缓存：按 目标类+方法名+参数类型 缓存已解析的 Method。 */
    private static final Map<String, Object> INVOKE_CACHE = new ConcurrentHashMap<>();
    /** 构造器数组缓存：getConstructors() 每次调用都会克隆数组，这里只解析一次。 */
    private static final Map<Class<?>, Constructor<?>[]> CTOR_CACHE = new ConcurrentHashMap<>();

    private OptionalBridges() {}

    private static Class<?> loadClass(String name) {
        Object cached = CLASS_CACHE.get(name);
        if (cached instanceof Class<?> type) return type;
        if (cached != null) return null;
        try {
            Class<?> type = Class.forName(name);
            CLASS_CACHE.put(name, type);
            return type;
        } catch (Throwable ignored) {
            CLASS_CACHE.put(name, MISS);
            return null;
        }
    }

    /** 超越维度是否安装；未安装时所有桥接逻辑直接跳过。 */
    public static boolean beyondAvailable() {
        return loadClass(BD_NET_CLASS) != null;
    }

    private static boolean projectEAvailable() {
        return loadClass(PROJECTE_API) != null;
    }

    /** 读取玩家已学习的全部 ProjectE 物品（数量 1 的副本）。 */
    private static List<ItemStack> learnedItems(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();
        if (!projectEAvailable()) return result;
        try {
            Object provider = projectEProvider(player);
            Object knowledge = provider == null ? null : tryInvoke(provider, "getKnowledge");
            if (!(knowledge instanceof Iterable<?> iterable)) return result;
            for (Object value : iterable) {
                ItemStack stack = asStack(value);
                if (stack.isEmpty()) stack = asStack(tryInvoke(value, "createStack"));
                if (stack.isEmpty()) continue;
                result.add(stack.copyWithCount(1));
            }
        } catch (Throwable ignored) {
            // ProjectE 未安装或 API 不兼容时保持空列表。
        }
        return result;
    }

    /**
     * 向 ProjectE 存入普通物品，并自动学习尚未学习的物品。
     * 带 NBT（附魔、耐久损耗等）的物品是否允许兑换由「NBT过滤」开关决定：
     * 过滤开启时原样退回；过滤关闭时按 ProjectE 自己的定价兑换。
     * 兑换学哪个版本由 resolveExchange 判断：附魔/耐久这类不增值的 NBT 按裸物品学习，
     * 药水之箭这类「NBT 就是物品本身」的条目按原样学习，不会被抹成没有 EMC 的空白物品。
     */
    public static ItemStack depositProjectE(ServerPlayer player, ItemStack input) {
        if (input.isEmpty()) return input;
        if (input.hasTag() && NetworkStore.isNbtFilter(player)) return input;
        try {
            Object provider = projectEProvider(player);
            Object proxy = projectEProxy();
            if (provider == null || proxy == null) return input;
            Exchange exchange = resolveExchange(proxy, input);
            if (exchange == null) return input;
            long value = exchange.value();

            learn(provider, exchange.knowledge());
            long available = emc(provider);
            long capacity = (Long.MAX_VALUE - available) / value;
            int amount = (int) Math.min(input.getCount(), Math.min(Integer.MAX_VALUE, capacity));
            if (amount <= 0) return input;
            setEmc(provider, available + value * amount);
            syncProjectE(provider, player);
            notifyConverted(player, input, value, amount);
            return input.copyWithCount(input.getCount() - amount);
        } catch (Throwable ignored) {
            return input;
        }
    }

    /** 一次兑换的结果：入账的 EMC 单价，以及要在转化桌里学会的那个物品。 */
    private record Exchange(long value, ItemStack knowledge) {}

    /**
     * 照 ProjectE 自己的定价顺序（NBTManager.getEmcValue）决定兑换方式：
     * 裸物品有价且完整版本不比裸物品更贵 → 附魔/耐久只是消耗，按裸物品学习、按查到的价入账；
     * 只有带 NBT 的完整版有价（或明显更贵） → NBT 就是这件物品本身，原样学习，
     * 否则会学出一张没有 EMC 的空白药水之箭，玩家既看不到也取不回来。
     */
    private static Exchange resolveExchange(Object proxy, ItemStack input) {
        long fullValue = rawEmcValue(proxy, input);
        if (!input.hasTag()) return fullValue > 0L ? new Exchange(fullValue, input.copyWithCount(1)) : null;
        ItemStack clean = input.copyWithCount(1);
        clean.setTag(null);
        long cleanValue = rawEmcValue(proxy, clean);
        if (cleanValue > 0L && fullValue <= cleanValue) {
            return new Exchange(fullValue > 0L ? fullValue : cleanValue, clean);
        }
        if (fullValue > 0L) return new Exchange(fullValue, input.copyWithCount(1));
        return null;
    }

    /** 兑换成功后在动作栏提示一句，避免玩家以为物品「凭空消失」。开关关闭时只静音提示。 */
    private static void notifyConverted(ServerPlayer player, ItemStack input, long value, int amount) {
        if (!NetworkStore.isConvertHint(player)) return;
        player.displayClientMessage(Component.translatable(
                "chat.emc_network.converted", input.getHoverName(), amount, value * amount), true);
    }

    /**
     * 存入超越维度网络。网络收不下的部分原样返回，由调用方留在玩家背包里；
     * 影子 EMC 物品不进原生存储（它们由 {@link #syncProjectEIntoBeyond} 维护）。
     */
    public static ItemStack depositBeyond(ServerPlayer player, ItemStack input) {
        if (input.isEmpty()) return input;
        try {
            Object storage = beyondStorage(player);
            if (storage == null) return input;
            Object key = newItemKey(input.copyWithCount(1));
            if (key == null) return input;
            Object result = tryInvoke(storage, "insert", key, (long) input.getCount(), false);
            if (result != NO_RESULT) return input.copyWithCount(toItemCount(remainingAmount(result)));
        } catch (Throwable ignored) {}
        return input;
    }

    /** 将 ProjectE 的知识物品同步为超越维度中的影子数量。 */
    public static void syncProjectEIntoBeyond(ServerPlayer player) {
        try {
            if (!beyondAvailable()) return;
            CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
            ListTag oldList = persisted.getList(VIRTUAL_ITEMS, Tag.TAG_COMPOUND);
            ListTag oldPhysicalList = persisted.getList(PHYSICAL_ITEMS, Tag.TAG_COMPOUND);
            boolean active = NetworkStore.isUnlocked(player) && NetworkStore.isEnabled(player);
            // 未解锁（或未开启）且没有遗留影子账目时，同步没有任何可做的事，直接跳过。
            if (!active && oldList.isEmpty() && oldPhysicalList.isEmpty()) return;

            Object storage = beyondStorage(player);
            Object probeKey = newItemKey(ItemStack.EMPTY);
            if (storage == null || probeKey == null || tryInvoke(storage, "getStackByKey", probeKey) == NO_RESULT) return;

            Map<String, Long> oldVirtual = new HashMap<>();
            Map<String, Long> oldPhysical = new HashMap<>();
            Map<String, ItemStack> oldStacks = new HashMap<>();
            for (Tag tag : oldList) {
                CompoundTag entry = (CompoundTag) tag;
                ItemStack stack = ItemStack.of(entry.getCompound("stack"));
                if (stack.isEmpty()) continue;
                String key = stackKey(stack);
                oldStacks.put(key, stack.copyWithCount(1));
                oldVirtual.put(key, Math.max(0L, entry.getLong("amount")));
            }
            for (Tag tag : oldPhysicalList) {
                CompoundTag entry = (CompoundTag) tag;
                ItemStack stack = ItemStack.of(entry.getCompound("stack"));
                if (stack.isEmpty()) continue;
                String key = stackKey(stack);
                oldStacks.putIfAbsent(key, stack.copyWithCount(1));
                oldPhysical.put(key, Math.max(0L, entry.getLong("amount")));
            }

            List<ItemStack> learned = active ? learnedItems(player) : List.of();
            Map<String, ItemStack> learnedByKey = new HashMap<>();
            // learnedItems 返回的已是数量 1 的副本，可直接入表，省掉一次复制。
            for (ItemStack stack : learned) learnedByKey.put(stackKey(stack), stack);

            // provider / proxy / EMC 总量在循环外解析一次；循环内不再重复反射取回。
            Object provider = active && projectEAvailable() ? projectEProvider(player) : null;
            Object proxy = provider == null ? null : projectEProxy();
            long emcTotal = provider == null ? 0L : emc(provider);

            ListTag newList = new ListTag();
            ListTag newPhysicalList = new ListTag();
            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(oldVirtual.keySet());
            allKeys.addAll(oldPhysical.keySet());
            allKeys.addAll(learnedByKey.keySet());
            boolean storageChanged = false;
            for (String key : allKeys) {
                ItemStack stack = learnedByKey.getOrDefault(key, oldStacks.get(key));
                if (stack == null || stack.isEmpty()) continue;
                Object itemKey = newItemKey(stack.copyWithCount(1));
                if (itemKey == null) continue;
                long actual = storageAmount(storage, itemKey);
                long previousVirtual = oldVirtual.getOrDefault(key, 0L);
                long previousPhysical = oldPhysical.containsKey(key)
                        ? oldPhysical.get(key) : Math.max(0L, actual - previousVirtual);
                long expected = safeAdd(previousPhysical, previousVirtual);
                long delta = actual >= expected ? actual - expected : -(expected - actual);

                // 原生超越维度界面取出时，影子数量会先减少。把这部分识别为 EMC 兑换，
                // 扣除 ProjectE EMC，避免下一次同步把影子物品凭空补回去。
                long virtualLoss = 0L;
                long physical = previousPhysical;
                if (delta > 0L) {
                    physical = safeAdd(physical, delta);
                } else if (delta < 0L) {
                    long removed = -delta;
                    long physicalLoss = Math.min(previousPhysical, removed);
                    physical = previousPhysical - physicalLoss;
                    virtualLoss = Math.min(previousVirtual, removed - physicalLoss);
                }
                if (virtualLoss > 0L) {
                    // 实体库存先被取出；只有取出量超过实体库存时，才算兑换 EMC。
                    deductProjectE(player, stack, virtualLoss);
                    emcTotal = provider == null ? 0L : emc(provider);
                }

                long virtualAmount = active ? projectEAmount(stack, provider, proxy, emcTotal) : 0L;
                long desired = safeAdd(physical, virtualAmount);
                // 数量没变时跳过写入，避免无意义的存储变更与全量 onChange 重建。
                if (desired != actual) {
                    if (!setStorageAmount(storage, itemKey, desired)) continue;
                    storageChanged = true;
                }
                if (virtualAmount > 0L) {
                    CompoundTag entry = new CompoundTag();
                    entry.put("stack", stack.copyWithCount(1).save(new CompoundTag()));
                    entry.putLong("amount", virtualAmount);
                    newList.add(entry);
                }
                if (physical > 0L) {
                    CompoundTag entry = new CompoundTag();
                    entry.put("stack", stack.copyWithCount(1).save(new CompoundTag()));
                    entry.putLong("amount", physical);
                    newPhysicalList.add(entry);
                }
            }
            persisted.put(VIRTUAL_ITEMS, newList);
            persisted.put(PHYSICAL_ITEMS, newPhysicalList);
            player.getPersistentData().put("PlayerPersisted", persisted);
            if (storageChanged) tryInvoke(storage, "onChange");
        } catch (Throwable ignored) {
            // 不同版本没有影子条目接口时，功能槽仍直接显示 ProjectE 物品。
        }
    }

    /** 转化桌优先时拦截超越维度原生存入，并在原生取出前扣除 EMC。 */
    public static void registerBeyondRoutingHandler() {
        if (!beyondAvailable()) return;
        registerBeyondInsertHandler();
        registerBeyondExtractHandler();
    }

    private static void registerBeyondInsertHandler() {
        if (beyondInsertHandlerRegistered) return;
        try {
            Class<?> owner = loadClass(BD_INSERT_HANDLER_CLASS);
            if (owner == null) return;
            Class<?> handlerType = loadClass(BD_INSERT_HANDLER_CLASS + "$BeforeInsertHandler");
            if (handlerType == null) return;
            Object handler = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("beforeInsert") && args != null && args.length >= 2) {
                            return routeNativeInsert(args);
                        }
                        return null;
                    });
            if (tryInvokeStatic(owner, "addHandler", handler) != NO_RESULT) {
                beyondInsertHandlerRegistered = true;
            }
        } catch (Throwable ignored) {}
    }

    private static void registerBeyondExtractHandler() {
        if (beyondExtractHandlerRegistered) return;
        try {
            Class<?> owner = loadClass(BD_EXTRACT_HANDLER_CLASS);
            if (owner == null) return;
            Class<?> handlerType = loadClass(BD_EXTRACT_HANDLER_CLASS + "$BeforeExtractHandler");
            if (handlerType == null) return;
            Object handler = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("beforeExtract") && args != null && args.length >= 3) {
                            return routeNativeExtract(args);
                        }
                        return null;
                    });
            if (tryInvokeStatic(owner, "addHandler", handler) != NO_RESULT) {
                beyondExtractHandlerRegistered = true;
            }
        } catch (Throwable ignored) {}
    }

    private static Object routeNativeInsert(Object[] args) {
        try {
            Object current = args.length >= 3 ? args[1] : args[0];
            Object net = args.length >= 3 ? args[2] : null;
            ServerPlayer player = playerForNet(net);
            if (player == null || NetworkStore.networkFirst(player) || !NetworkStore.isEnabled(player)) {
                return beforeInsertResult(current, false);
            }
            ItemStack stack = stackFromKeyAmount(current);
            if (stack.isEmpty()) return beforeInsertResult(current, false);
            if (NetworkStore.isNbtFilter(player) && stack.hasTag()) {
                return beforeInsertResult(current, false);
            }
            ItemStack remaining = depositProjectE(player, stack);
            if (remaining.getCount() == stack.getCount()) {
                return beforeInsertResult(current, false);
            }
            Object key = tryInvoke(current, "key");
            Object adjusted = newKeyAmount(key, Math.max(0L, remaining.getCount()));
            return adjusted == null ? beforeInsertResult(current, false) : beforeInsertResult(adjusted, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 原生网络取出发生前直接扣 EMC，并同步账本，防止周期同步把虚拟物品补回去。 */
    private static Object routeNativeExtract(Object[] args) {
        try {
            Object current = args.length >= 3 ? args[1] : args[0];
            Object net = args.length >= 3 ? args[2] : null;
            ServerPlayer player = playerForNet(net);
            if (player == null || !NetworkStore.isEnabled(player)) {
                return beforeExtractResult(current, false);
            }
            ItemStack stack = stackFromKeyAmount(current);
            long requested = amountOf(current);
            if (stack.isEmpty() || requested <= 0L) return beforeExtractResult(current, false);

            String key = stackKey(stack);
            CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
            // 实体与影子账目各扫描一次即可（取出量优先扣实体库存）。
            long physicalTaken = takeFromLedger(persisted, PHYSICAL_ITEMS, key, requested);
            long virtualTaken = takeFromLedger(persisted, VIRTUAL_ITEMS, key, requested - physicalTaken);
            if (virtualTaken > 0L) {
                deductProjectE(player, stack, virtualTaken);
            }
            return beforeExtractResult(current, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 在账目列表中定位 key 对应条目并扣除至多 requested 个，返回实际扣减量；条目清零时移除。 */
    private static long takeFromLedger(CompoundTag persisted, String listName, String key, long requested) {
        if (requested <= 0L) return 0L;
        ListTag list = persisted.getList(listName, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stored = ItemStack.of(entry.getCompound("stack"));
            if (stored.isEmpty() || !stackKey(stored).equals(key)) continue;
            long available = Math.max(0L, entry.getLong("amount"));
            long taken = Math.min(available, requested);
            if (available - taken <= 0L) list.remove(i);
            else entry.putLong("amount", available - taken);
            persisted.put(listName, list);
            return taken;
        }
        return 0L;
    }

    private static Object beforeInsertResult(Object keyAmount, boolean cancel) {
        try {
            if (insertReturnConstructor == null) {
                insertReturnConstructor = twoArgumentConstructor(BD_INSERT_HANDLER_CLASS + "$BeforeInsertHandlerReturnInfo");
            }
            return insertReturnConstructor == null ? null : insertReturnConstructor.newInstance(keyAmount, cancel);
        } catch (Throwable ignored) { return null; }
    }

    private static Object beforeExtractResult(Object keyAmount, boolean cancel) {
        try {
            if (extractReturnConstructor == null) {
                extractReturnConstructor = twoArgumentConstructor(BD_EXTRACT_HANDLER_CLASS + "$BeforeExtractHandlerReturnInfo");
            }
            return extractReturnConstructor == null ? null : extractReturnConstructor.newInstance(keyAmount, cancel);
        } catch (Throwable ignored) { return null; }
    }

    private static Object newKeyAmount(Object key, long amount) {
        if (key == null) return null;
        try {
            if (keyAmountConstructor == null) keyAmountConstructor = twoArgumentConstructor(BD_KEY_AMOUNT_CLASS);
            return keyAmountConstructor == null ? null : keyAmountConstructor.newInstance(key, amount);
        } catch (Throwable ignored) { return null; }
    }

    private static Constructor<?> twoArgumentConstructor(String className) {
        Class<?> type = loadClass(className);
        if (type == null) return null;
        for (Constructor<?> constructor : constructors(type)) {
            if (constructor.getParameterCount() == 2) return constructor;
        }
        return null;
    }

    private static Object newItemKey(ItemStack stack) {
        try {
            if (itemKeyConstructor == null) {
                Class<?> type = loadClass(BD_ITEM_KEY_CLASS);
                if (type == null) return null;
                for (Constructor<?> constructor : constructors(type)) {
                    if (constructor.getParameterCount() == 1
                            && compatible(constructor.getParameterTypes()[0], stack)) {
                        itemKeyConstructor = constructor;
                        break;
                    }
                }
            }
            return itemKeyConstructor == null ? null : itemKeyConstructor.newInstance(stack);
        } catch (Throwable ignored) { return null; }
    }

    private static Object beyondStorage(ServerPlayer player) {
        try {
            Class<?> dimensionsNet = loadClass(BD_NET_CLASS);
            if (dimensionsNet == null) return null;
            Object net = tryInvokeStatic(dimensionsNet, "getNetFromPlayer", player);
            if (net == NO_RESULT) net = tryInvokeStatic(dimensionsNet, "getPrimaryNetFromPlayer", player);
            if (net == NO_RESULT || net == null) return null;
            Object storage = tryInvoke(net, "getUnifiedStorage");
            if (storage == NO_RESULT) storage = tryInvoke(net, "getStorage");
            return storage == NO_RESULT ? null : storage;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long storageAmount(Object storage, Object key) {
        Object value = tryInvoke(storage, "getStackByKey", key);
        if (value == NO_RESULT) value = tryInvoke(storage, "getStack", key);
        return amountOf(value);
    }

    private static boolean setStorageAmount(Object storage, Object key, long amount) {
        Object result = tryInvoke(storage, "setAmountByKey", key, Math.max(0L, amount));
        if (result != NO_RESULT) return true;
        result = tryInvoke(storage, "setAmount", key, Math.max(0L, amount));
        return result != NO_RESULT;
    }

    /**
     * 直接向 ProjectE 查一件物品的 EMC。ProjectE 内部已经处理了「带 NBT 查不到就退回裸物品」，
     * 所以这里不再自行猜测，只把结果取回来。
     */
    private static long rawEmcValue(Object proxy, ItemStack stack) {
        Object valueObject = tryInvoke(proxy, "getValue", stack);
        return valueObject instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private static long projectEAmount(ItemStack stack, Object provider, Object proxy, long emcTotal) {
        try {
            if (provider == null || proxy == null || emcTotal <= 0L) return 0L;
            long value = rawEmcValue(proxy, stack);
            if (value <= 0L || !hasKnowledge(provider, stack)) return 0L;
            return emcTotal / value;
        } catch (Throwable ignored) { return 0L; }
    }

    private static void deductProjectE(ServerPlayer player, ItemStack stack, long amount) {
        try {
            Object provider = projectEProvider(player);
            Object proxy = projectEProxy();
            if (provider == null) return;
            long value = proxy == null ? 0L : rawEmcValue(proxy, stack);
            if (value <= 0L || amount <= 0L) return;
            long cost = amount > Long.MAX_VALUE / value ? Long.MAX_VALUE : value * amount;
            setEmc(provider, Math.max(0L, emc(provider) - cost));
            syncProjectE(provider, player);
        } catch (Throwable ignored) {}
    }

    private static Object projectEProxy() {
        try {
            Class<?> api = loadClass(PROJECTE_API);
            if (api == null) return null;
            Object result = tryInvokeStatic(api, "getEMCProxy");
            return result == NO_RESULT ? null : result;
        } catch (Throwable ignored) { return null; }
    }

    private static Object projectEProvider(ServerPlayer player) {
        try {
            Class<?> api = loadClass(PROJECTE_API);
            if (api == null) return null;
            Object proxy = tryInvokeStatic(api, "getTransmutationProxy");
            if (proxy == NO_RESULT || proxy == null) return null;
            Object provider = tryInvoke(proxy, "getKnowledgeProviderFor", player.getUUID());
            return provider == NO_RESULT ? null : provider;
        } catch (Throwable ignored) { return null; }
    }

    private static long emc(Object provider) {
        Object value = tryInvoke(provider, "getEmc");
        if (value instanceof BigInteger integer) {
            return integer.signum() <= 0 ? 0L : integer.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        }
        return value instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private static void setEmc(Object provider, long value) {
        long safeValue = Math.max(0L, value);
        // ProjectE PE1.0.1 的 IKnowledgeProvider.setEmc 参数是 BigInteger，不是 long。
        Object result = tryInvoke(provider, "setEmc", BigInteger.valueOf(safeValue));
        if (result == NO_RESULT) result = tryInvoke(provider, "setEmc", safeValue);
        if (result == NO_RESULT) tryInvoke(provider, "setEmc", Long.valueOf(safeValue));
    }

    private static void learn(Object provider, ItemStack stack) {
        Object result = tryInvoke(provider, "addKnowledge", stack);
        if (result == NO_RESULT) tryInvoke(provider, "learn", stack);
    }

    private static boolean hasKnowledge(Object provider, ItemStack stack) {
        Object result = tryInvoke(provider, "hasKnowledge", stack);
        return result instanceof Boolean value && value;
    }

    private static void syncProjectE(Object provider, ServerPlayer player) {
        Object result = tryInvoke(provider, "sync", player);
        if (result == NO_RESULT) tryInvoke(provider, "syncEmc", player);
    }

    /** insert 的返回值：0.7.30 是 KeyAmount（amount() 为剩余量）；取不到剩余量按全部收下处理。 */
    private static long remainingAmount(Object result) {
        if (result == NO_RESULT || result == null) return 0L;
        if (result instanceof ItemStack stack) return Math.max(0, stack.getCount());
        long amount = amountOf(result);
        return amount < 0L ? 0L : Math.min(Integer.MAX_VALUE, amount);
    }

    private static int toItemCount(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, amount));
    }

    private static long amountOf(Object value) {
        if (value == NO_RESULT || value == null) return 0L;
        if (value instanceof Number number) return Math.max(0L, number.longValue());
        for (String method : AMOUNT_ACCESSORS) {
            Object result = tryInvoke(value, method);
            if (result instanceof Number number) return Math.max(0L, number.longValue());
        }
        return -1L;
    }

    private static ItemStack asStack(Object value) {
        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    private static ItemStack stackFromKeyAmount(Object value) {
        ItemStack stack = asStack(value);
        if (stack.isEmpty()) stack = asStack(tryInvoke(value, "toStack"));
        if (stack.isEmpty()) stack = asStack(tryInvoke(value, "getStack"));
        return stack;
    }

    private static String stackKey(ItemStack stack) {
        return NetworkStore.stackKey(stack);
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static ServerPlayer playerForNet(Object net) {
        try {
            Object players = tryInvoke(net, "getPlayers");
            if (!(players instanceof Iterable<?> iterable)) return null;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            for (Object uuid : iterable) {
                if (uuid instanceof UUID id) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) return player;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 缓存 getConstructors() 的克隆数组，避免每次点击/插入都重新分配。 */
    private static Constructor<?>[] constructors(Class<?> type) {
        Constructor<?>[] cached = CTOR_CACHE.get(type);
        if (cached != null) return cached;
        Constructor<?>[] found = type.getConstructors();
        CTOR_CACHE.put(type, found);
        return found;
    }

    private static String signature(Class<?> owner, String method, Object[] args) {
        StringBuilder builder = new StringBuilder(96);
        builder.append(owner.getName()).append('#').append(method).append('#').append(args.length);
        for (Object arg : args) {
            builder.append('#');
            builder.append(arg == null ? "-" : arg.getClass().getName());
        }
        return builder.toString();
    }

    /** 按 类+方法名+参数类型 解析一次方法并缓存，替代此前每次调用都全量扫描 getMethods()。 */
    private static Method resolveMethod(Class<?> owner, String method, Object[] args) {
        for (Method candidate : owner.getMethods()) {
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != args.length) continue;
            if (!compatible(candidate.getParameterTypes(), args)) continue;
            return candidate;
        }
        return null;
    }

    private static Object tryInvokeStatic(Class<?> type, String method, Object... args) {
        if (type == null) return NO_RESULT;
        String key = "S" + signature(type, method, args);
        Object cached = INVOKE_CACHE.get(key);
        if (cached == MISS) return NO_RESULT;
        Method target;
        if (cached instanceof Method found) {
            target = found;
        } else {
            target = resolveMethod(type, method, args);
            INVOKE_CACHE.put(key, target == null ? MISS : target);
            if (target == null) return NO_RESULT;
        }
        try { return target.invoke(null, args); } catch (Throwable ignored) { return NO_RESULT; }
    }

    private static Object tryInvoke(Object target, String method, Object... args) {
        if (target == null || target == NO_RESULT) return NO_RESULT;
        Class<?> owner = target.getClass();
        String key = "I" + signature(owner, method, args);
        Object cached = INVOKE_CACHE.get(key);
        if (cached == MISS) return NO_RESULT;
        Method resolved;
        if (cached instanceof Method found) {
            resolved = found;
        } else {
            resolved = resolveMethod(owner, method, args);
            INVOKE_CACHE.put(key, resolved == null ? MISS : resolved);
            if (resolved == null) return NO_RESULT;
        }
        try { return resolved.invoke(target, args); } catch (Throwable ignored) { return NO_RESULT; }
    }

    private static boolean compatible(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) return false;
        for (int i = 0; i < parameters.length; i++) if (!compatible(parameters[i], args[i])) return false;
        return true;
    }

    private static boolean compatible(Class<?> parameter, Object arg) {
        if (arg == null) return !parameter.isPrimitive();
        Class<?> type = parameter.isPrimitive() ? wrapper(parameter) : parameter;
        return type.isAssignableFrom(arg.getClass())
                || (Number.class.isAssignableFrom(type) && arg instanceof Number);
    }

    private static Class<?> wrapper(Class<?> type) {
        if (type == long.class) return Long.class;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
