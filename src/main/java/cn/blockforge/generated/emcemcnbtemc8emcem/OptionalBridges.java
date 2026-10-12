package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

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

/** ProjectE 与 Beyond Dimensions 的运行时桥接，兼容多个公开 API 版本。 */
public final class OptionalBridges {
    private static final Object NO_RESULT = new Object();
    /** 反射解析失败的哨兵，避免同一签名反复扫描方法表。 */
    private static final Object MISS = new Object();
    private static final String VIRTUAL_ITEMS = "emc_virtual_items";
    private static final String PHYSICAL_ITEMS = "emc_virtual_physical_items";
    private static final String PROJECTE_API = "moze_intel.projecte.api.ProjectEAPI";
    private static final String PROJECTE_EMC_PROXY_IMPL = "moze_intel.projecte.impl.EMCProxyImpl";
    private static final String PROJECTE_TRANSMUTATION_PROXY_IMPL = "moze_intel.projecte.impl.TransmutationProxyImpl";
    private static final String PROJECTE_CAPABILITIES = "moze_intel.projecte.api.capabilities.PECapabilities";
    private static final String[] BD_NET_CLASSES = {
            "com.wintercogs.beyonddimensions.Api.DataBase.DimensionsNet",
            "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet"
    };
    private static final String[] BD_ITEM_KEY_CLASSES = {
            "com.wintercogs.beyonddimensions.Api.DataBase.Stack.ItemStackKey",
            "com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey"
    };
    private static final String[] BD_LEGACY_TYPE_CLASSES = {
            "com.wintercogs.beyonddimensions.Api.DataBase.Stack.ItemStackType",
            "com.wintercogs.beyonddimensions.api.storage.type.ItemStackType",
            "com.wintercogs.beyonddimensions.api.storage.key.ItemStackType"
    };
    private static final String[] BD_HANDLER_CLASSES = {
            "com.wintercogs.beyonddimensions.Api.Registry.UnifiedStorageBeforeInsertHandler",
            "com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler"
    };
    private static final String[] BD_EXTRACT_HANDLER_CLASSES = {
            "com.wintercogs.beyonddimensions.Api.Registry.UnifiedStorageBeforeExtractHandler",
            "com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeExtractHandler"
    };
    private static boolean beyondInsertHandlerRegistered;
    private static boolean beyondExtractHandlerRegistered;
    /** PE1.1.0 无参代理实例（类内无字段，可安全复用）。 */
    private static Object emcProxyInstance;
    private static Object transmutationProxyInstance;

    /** 类查找结果缓存（含"不存在"结果），避免每秒反复触发 ClassNotFoundException 构建堆栈。 */
    private static final Map<String, Object> CLASS_CACHE = new ConcurrentHashMap<>();
    /** 反射方法解析缓存：按 目标类+方法名+参数类型 缓存已解析的 Method。 */
    private static final Map<String, Object> INVOKE_CACHE = new ConcurrentHashMap<>();
    /** 构造器数组缓存：getConstructors() 每次调用都会克隆数组，这里只解析一次。 */
    private static final Map<Class<?>, Constructor<?>[]> CTOR_CACHE = new ConcurrentHashMap<>();

    private OptionalBridges() {}

    private static Class<?> loadClass(String name) {
        Object cached = CLASS_CACHE.get(name);
        if (cached instanceof Class<?> type) {
            return type;
        }
        if (cached != null) {
            return null;
        }
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
        return loadClass(BD_NET_CLASSES[0]) != null || loadClass(BD_NET_CLASSES[1]) != null;
    }

    private static boolean projectEAvailable() {
        return loadClass(PROJECTE_API) != null || loadClass(PROJECTE_EMC_PROXY_IMPL) != null;
    }

    public static List<ItemStack> projectEItems(ServerPlayer player) {
        return projectEItems(player, Integer.MAX_VALUE, false);
    }

    /**
     * 读取玩家已学习的 ProjectE 物品。onlyValued 为真时跳过查不到 EMC 的条目：
     * 这类条目在转化桌里既换不到东西，也不该出现在可调用列表上。
     */
    public static List<ItemStack> projectEItems(ServerPlayer player, int limit, boolean onlyValued) {
        List<ItemStack> result = new ArrayList<>();
        if (!projectEAvailable()) {
            return result;
        }
        try {
            Object provider = projectEProvider(player);
            Object knowledge = provider == null ? null : tryInvoke(provider, "getKnowledge");
            if (!(knowledge instanceof Iterable<?> iterable)) {
                return result;
            }
            Object proxy = onlyValued ? projectEProxy() : null;
            for (Object value : iterable) {
                if (result.size() >= limit) {
                    break;
                }
                ItemStack stack = asStack(value);
                if (stack.isEmpty()) {
                    Object created = tryInvoke(value, "createStack");
                    stack = asStack(created);
                }
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack one = stack.copyWithCount(1);
                if (proxy != null && rawEmcValue(proxy, one) <= 0L) {
                    continue;
                }
                result.add(one);
            }
        } catch (Throwable ignored) {
            // ProjectE 未安装或 API 不兼容时保持空列表。
        }
        return result;
    }

    /**
     * 向 ProjectE 存入普通物品，并自动学习尚未学习的物品。
     * 带附加数据（附魔、耐久损耗等）的物品是否允许兑换由「NBT过滤」开关决定：
     * 过滤开启时原样退回；过滤关闭时按 ProjectE 自己的定价兑换。
     * 兑换学哪个版本由 resolveExchange 判断：附魔/耐久这类不增值的数据按裸物品学习，
     * 药水之箭这类「组件就是物品本身」的条目按原样学习，不会被抹成没有 EMC 的空白物品。
     */
    public static ItemStack depositProjectE(ServerPlayer player, ItemStack input) {
        if (input.isEmpty()) {
            return input;
        }
        if (NetworkStore.hasExtraComponents(input) && NetworkStore.isNbtFilter(player)) {
            return input;
        }
        try {
            Object provider = projectEProvider(player);
            Object proxy = projectEProxy();
            if (provider == null || proxy == null) {
                return input;
            }
            Exchange exchange = resolveExchange(proxy, input);
            if (exchange == null) {
                return input;
            }
            long value = exchange.value();

            learn(provider, exchange.knowledge());
            long available = emc(provider);
            long capacity = (Long.MAX_VALUE - available) / value;
            int amount = (int) Math.min(input.getCount(), Math.min(Integer.MAX_VALUE, capacity));
            if (amount <= 0) {
                return input;
            }
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
     * 照 ProjectE 自己的定价顺序决定兑换方式：
     * 裸物品有价且完整版本不比裸物品更贵 → 附魔/耐久只是消耗，按裸物品学习、按查到的价入账；
     * 只有带附加数据的完整版有价（或明显更贵） → 组件就是这件物品本身，原样学习，
     * 否则会学出一张没有 EMC 的空白药水之箭，玩家既看不到也取不回来。
     */
    private static Exchange resolveExchange(Object proxy, ItemStack input) {
        long fullValue = rawEmcValue(proxy, input);
        if (!NetworkStore.hasExtraComponents(input)) {
            return fullValue > 0L ? new Exchange(fullValue, input.copyWithCount(1)) : null;
        }
        ItemStack clean = new ItemStack(input.getItem());
        long cleanValue = rawEmcValue(proxy, clean);
        if (cleanValue > 0L && fullValue <= cleanValue) {
            return new Exchange(fullValue > 0L ? fullValue : cleanValue, clean);
        }
        if (fullValue > 0L) {
            return new Exchange(fullValue, input.copyWithCount(1));
        }
        return null;
    }

    /** 兑换成功后在动作栏提示一句，避免玩家以为物品「凭空消失」。 */
    private static void notifyConverted(ServerPlayer player, ItemStack input, long value, int amount) {
        if (!NetworkStore.isConvertHintEnabled(player)) {
            return;
        }
        player.displayClientMessage(Component.translatable(
                "chat.emc_network.converted", input.getHoverName(), amount, value * amount), true);
    }

    public static ItemStack depositBeyond(ServerPlayer player, ItemStack input) {
        if (input.isEmpty()) {
            return input;
        }
        try {
            Object storage = beyondStorage(player);
            if (storage == null) {
                return input;
            }

            Object key = newObject(BD_ITEM_KEY_CLASSES, input.copyWithCount(1));
            if (key != null) {
                Object result = tryInvoke(storage, "insert", key, (long) input.getCount(), false);
                if (result != NO_RESULT) {
                    return input.copyWithCount(toItemCount(remainingAmount(result)));
                }
            }

            Object legacy = newLegacyType(input.copy());
            if (legacy != null) {
                Object result = tryInvoke(storage, "insert", legacy, false);
                if (result == NO_RESULT) {
                    result = tryInvoke(storage, "insert", legacy);
                }
                if (result != NO_RESULT) {
                    return input.copyWithCount(toItemCount(remainingAmount(result)));
                }
            }
        } catch (Throwable ignored) {
        }
        return input;
    }

    /** 将 ProjectE 的知识物品同步为超越维度中的影子数量。 */
    public static void syncProjectEIntoBeyond(ServerPlayer player) {
        try {
            if (!beyondAvailable()) {
                return;
            }
            CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
            ListTag oldList = persisted.getList(VIRTUAL_ITEMS, Tag.TAG_COMPOUND);
            ListTag oldPhysicalList = persisted.getList(PHYSICAL_ITEMS, Tag.TAG_COMPOUND);
            boolean active = NetworkStore.isUnlocked(player) && NetworkStore.isEnabled(player);
            // 未解锁（或未开启）且没有遗留影子账目时，同步没有任何可做的事，直接跳过。
            if (!active && oldList.isEmpty() && oldPhysicalList.isEmpty()) {
                return;
            }

            Object storage = beyondStorage(player);
            Object probeKey = newObject(BD_ITEM_KEY_CLASSES, ItemStack.EMPTY);
            if (storage == null || probeKey == null || tryInvoke(storage, "getStackByKey", probeKey) == NO_RESULT) {
                return;
            }

            Map<String, Long> oldVirtual = new HashMap<>();
            Map<String, Long> oldPhysical = new HashMap<>();
            Map<String, ItemStack> oldStacks = new HashMap<>();
            for (Tag tag : oldList) {
                CompoundTag entry = (CompoundTag) tag;
                ItemStack stack = NetworkStore.loadStack(player, entry.getCompound("stack"));
                if (stack.isEmpty()) {
                    continue;
                }
                String key = NetworkStore.stackKey(player, stack);
                oldStacks.put(key, stack.copyWithCount(1));
                oldVirtual.put(key, Math.max(0L, entry.getLong("amount")));
            }
            for (Tag tag : oldPhysicalList) {
                CompoundTag entry = (CompoundTag) tag;
                ItemStack stack = NetworkStore.loadStack(player, entry.getCompound("stack"));
                if (stack.isEmpty()) {
                    continue;
                }
                String key = NetworkStore.stackKey(player, stack);
                oldStacks.putIfAbsent(key, stack.copyWithCount(1));
                oldPhysical.put(key, Math.max(0L, entry.getLong("amount")));
            }

            List<ItemStack> learned = active ? projectEItems(player) : List.of();
            Map<String, ItemStack> learnedByKey = new HashMap<>();
            for (ItemStack stack : learned) {
                learnedByKey.put(NetworkStore.stackKey(player, stack), stack.copyWithCount(1));
            }

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
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                Object itemKey = newObject(BD_ITEM_KEY_CLASSES, stack.copyWithCount(1));
                if (itemKey == null) {
                    continue;
                }
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
                    deductProjectE(player, stack, virtualLoss);
                    emcTotal = provider == null ? 0L : emc(provider);
                }

                long virtualAmount = active ? projectEAmount(stack, provider, proxy, emcTotal) : 0L;
                long desired = safeAdd(physical, virtualAmount);
                if (desired != actual) {
                    if (!setStorageAmount(storage, itemKey, desired)) {
                        continue;
                    }
                    storageChanged = true;
                }
                if (virtualAmount > 0L) {
                    CompoundTag entry = new CompoundTag();
                    entry.put("stack", NetworkStore.saveStack(player, stack.copyWithCount(1)));
                    entry.putLong("amount", virtualAmount);
                    newList.add(entry);
                }
                if (physical > 0L) {
                    CompoundTag entry = new CompoundTag();
                    entry.put("stack", NetworkStore.saveStack(player, stack.copyWithCount(1)));
                    entry.putLong("amount", physical);
                    newPhysicalList.add(entry);
                }
            }
            persisted.put(VIRTUAL_ITEMS, newList);
            persisted.put(PHYSICAL_ITEMS, newPhysicalList);
            player.getPersistentData().put("PlayerPersisted", persisted);
            if (storageChanged) {
                tryInvoke(storage, "onChange");
            }
        } catch (Throwable ignored) {
            // 不同版本没有影子条目接口时，功能槽仍直接显示 ProjectE 物品。
        }
    }

    /** 转化桌优先时拦截超越维度原生存入，并在原生取出前扣除 EMC。 */
    public static void registerBeyondRoutingHandler() {
        if (!beyondAvailable()) {
            return;
        }
        registerBeyondInsertHandler();
        registerBeyondExtractHandler();
    }

    private static void registerBeyondInsertHandler() {
        if (beyondInsertHandlerRegistered) {
            return;
        }
        for (String name : BD_HANDLER_CLASSES) {
            try {
                Class<?> owner = loadClass(name);
                if (owner == null) {
                    continue;
                }
                Class<?> handlerType = Class.forName(name + "$BeforeInsertHandler");
                Object handler = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType},
                        (proxy, method, args) -> {
                            if (method.getName().equals("beforeInsert") && args != null && args.length >= 2) {
                                return routeNativeInsert(args);
                            }
                            return null;
                        });
                Object added = tryInvokeStatic(owner, "addHandler", handler);
                if (added != NO_RESULT) {
                    beyondInsertHandlerRegistered = true;
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void registerBeyondExtractHandler() {
        if (beyondExtractHandlerRegistered) {
            return;
        }
        for (String name : BD_EXTRACT_HANDLER_CLASSES) {
            try {
                Class<?> owner = loadClass(name);
                if (owner == null) {
                    continue;
                }
                Class<?> handlerType = Class.forName(name + "$BeforeExtractHandler");
                Object handler = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType},
                        (proxy, method, args) -> {
                            if (method.getName().equals("beforeExtract") && args != null && args.length >= 3) {
                                return routeNativeExtract(args);
                            }
                            return null;
                        });
                Object added = tryInvokeStatic(owner, "addHandler", handler);
                if (added != NO_RESULT) {
                    beyondExtractHandlerRegistered = true;
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
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
            if (stack.isEmpty()) {
                return beforeInsertResult(current, false);
            }
            if (NetworkStore.isNbtFilter(player) && NetworkStore.hasExtraComponents(stack)) {
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
            if (stack.isEmpty() || requested <= 0L) {
                return beforeExtractResult(current, false);
            }

            String key = NetworkStore.stackKey(player, stack);
            CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
            long physicalTaken = takeFromLedger(player, persisted, PHYSICAL_ITEMS, key, requested);
            long virtualTaken = takeFromLedger(player, persisted, VIRTUAL_ITEMS, key, requested - physicalTaken);
            if (virtualTaken > 0L) {
                deductProjectE(player, stack, virtualTaken);
            }
            return beforeExtractResult(current, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 在账目列表中定位 key 对应条目并扣除至多 requested 个，返回实际扣减量；条目清零时移除。 */
    private static long takeFromLedger(ServerPlayer player, CompoundTag persisted, String listName, String key, long requested) {
        if (requested <= 0L) {
            return 0L;
        }
        ListTag list = persisted.getList(listName, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stored = NetworkStore.loadStack(player, entry.getCompound("stack"));
            if (stored.isEmpty() || !NetworkStore.stackKey(player, stored).equals(key)) {
                continue;
            }
            long available = Math.max(0L, entry.getLong("amount"));
            long taken = Math.min(available, requested);
            if (available - taken <= 0L) {
                list.remove(i);
            } else {
                entry.putLong("amount", available - taken);
            }
            persisted.put(listName, list);
            return taken;
        }
        return 0L;
    }

    private static Object beforeInsertResult(Object keyAmount, boolean cancel) {
        for (String name : BD_HANDLER_CLASSES) {
            try {
                Class<?> owner = loadClass(name);
                if (owner == null) {
                    continue;
                }
                Class<?> type = Class.forName(name + "$BeforeInsertHandlerReturnInfo");
                for (Constructor<?> constructor : constructors(type)) {
                    if (constructor.getParameterCount() == 2) {
                        return constructor.newInstance(keyAmount, cancel);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object beforeExtractResult(Object keyAmount, boolean cancel) {
        for (String name : BD_EXTRACT_HANDLER_CLASSES) {
            try {
                Class<?> owner = loadClass(name);
                if (owner == null) {
                    continue;
                }
                Class<?> type = Class.forName(name + "$BeforeExtractHandlerReturnInfo");
                for (Constructor<?> constructor : constructors(type)) {
                    if (constructor.getParameterCount() == 2) {
                        return constructor.newInstance(keyAmount, cancel);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object newKeyAmount(Object key, long amount) {
        if (key == null) {
            return null;
        }
        for (String name : new String[]{
                "com.wintercogs.beyonddimensions.Api.DataBase.Stack.KeyAmount",
                "com.wintercogs.beyonddimensions.api.storage.key.KeyAmount"}) {
            try {
                Class<?> type = loadClass(name);
                if (type == null) {
                    continue;
                }
                for (Constructor<?> constructor : constructors(type)) {
                    if (constructor.getParameterCount() == 2) {
                        return constructor.newInstance(key, amount);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object newObject(String[] classNames, ItemStack stack) {
        for (String name : classNames) {
            try {
                Class<?> type = loadClass(name);
                if (type == null) {
                    continue;
                }
                for (Constructor<?> constructor : constructors(type)) {
                    if (constructor.getParameterCount() == 1 && compatible(constructor.getParameterTypes()[0], stack)) {
                        return constructor.newInstance(stack);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object newLegacyType(ItemStack stack) {
        for (String name : BD_LEGACY_TYPE_CLASSES) {
            try {
                Class<?> type = loadClass(name);
                if (type == null) {
                    continue;
                }
                for (Constructor<?> constructor : constructors(type)) {
                    Class<?>[] parameters = constructor.getParameterTypes();
                    if (parameters.length == 1 && compatible(parameters[0], stack)) {
                        return constructor.newInstance(stack);
                    }
                    if (parameters.length == 2 && compatible(parameters[0], stack) && compatible(parameters[1], (long) stack.getCount())) {
                        return constructor.newInstance(stack, (long) stack.getCount());
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object beyondStorage(ServerPlayer player) {
        for (String name : BD_NET_CLASSES) {
            try {
                Class<?> dimensionsNet = loadClass(name);
                if (dimensionsNet == null) {
                    continue;
                }
                Object net = tryInvokeStatic(dimensionsNet, "getNetFromPlayer", player);
                if (net == NO_RESULT) {
                    net = tryInvokeStatic(dimensionsNet, "getPrimaryNetFromPlayer", player);
                }
                if (net == NO_RESULT || net == null) {
                    continue;
                }
                Object storage = tryInvoke(net, "getUnifiedStorage");
                if (storage == NO_RESULT) {
                    storage = tryInvoke(net, "getStorage");
                }
                if (storage != NO_RESULT && storage != null) {
                    return storage;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static long storageAmount(Object storage, Object key) {
        Object value = tryInvoke(storage, "getStackByKey", key);
        if (value == NO_RESULT) {
            value = tryInvoke(storage, "getStack", key);
        }
        return amountOf(value);
    }

    private static boolean setStorageAmount(Object storage, Object key, long amount) {
        Object result = tryInvoke(storage, "setAmountByKey", key, Math.max(0L, amount));
        if (result != NO_RESULT) {
            return true;
        }
        result = tryInvoke(storage, "setAmount", key, Math.max(0L, amount));
        return result != NO_RESULT;
    }

    /**
     * 直接向 ProjectE 查一件物品的 EMC。ProjectE 内部已经处理了「带附加数据查不到就退回裸物品」，
     * 所以这里不再自行猜测，只把结果取回来。
     */
    private static long rawEmcValue(Object proxy, ItemStack stack) {
        Object valueObject = tryInvoke(proxy, "getValue", stack);
        return valueObject instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private static long projectEAmount(ItemStack stack, Object provider, Object proxy, long emcTotal) {
        try {
            if (provider == null || proxy == null || emcTotal <= 0L) {
                return 0L;
            }
            long value = rawEmcValue(proxy, stack);
            if (value <= 0L || !hasKnowledge(provider, stack)) {
                return 0L;
            }
            return emcTotal / value;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static void deductProjectE(ServerPlayer player, ItemStack stack, long amount) {
        try {
            Object provider = projectEProvider(player);
            Object proxy = projectEProxy();
            if (provider == null) {
                return;
            }
            long value = proxy == null ? 0L : rawEmcValue(proxy, stack);
            if (value <= 0L || amount <= 0L) {
                return;
            }
            long cost = amount > Long.MAX_VALUE / value ? Long.MAX_VALUE : value * amount;
            setEmc(provider, Math.max(0L, emc(provider) - cost));
            syncProjectE(provider, player);
        } catch (Throwable ignored) {
        }
    }

    private static Object projectEProxy() {
        try {
            Class<?> api = loadClass(PROJECTE_API);
            if (api != null) {
                Object result = tryInvokeStatic(api, "getEMCProxy");
                if (result != NO_RESULT && result != null) {
                    return result;
                }
            }
            // ProjectE 1.21.1（PE1.1.0）：ProjectEAPI 只剩常量，改用 EMCProxyImpl 公共无参构造器。
            if (emcProxyInstance == null) {
                Class<?> impl = loadClass(PROJECTE_EMC_PROXY_IMPL);
                if (impl == null) {
                    return null;
                }
                emcProxyInstance = impl.getConstructor().newInstance();
            }
            return emcProxyInstance;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object projectEProvider(ServerPlayer player) {
        try {
            Class<?> api = loadClass(PROJECTE_API);
            if (api != null) {
                Object legacyProxy = tryInvokeStatic(api, "getTransmutationProxy");
                if (legacyProxy != NO_RESULT && legacyProxy != null) {
                    Object provider = tryInvoke(legacyProxy, "getKnowledgeProviderFor", player.getUUID());
                    if (provider != NO_RESULT && provider != null) {
                        return provider;
                    }
                }
            }
            // PE1.1.0：TransmutationProxyImpl 公共无参构造器 + getKnowledgeProviderFor(UUID)。
            if (transmutationProxyInstance == null) {
                Class<?> impl = loadClass(PROJECTE_TRANSMUTATION_PROXY_IMPL);
                if (impl == null) {
                    return providerFromKnowledgeCapability(player);
                }
                transmutationProxyInstance = impl.getConstructor().newInstance();
            }
            Object provider = tryInvoke(transmutationProxyInstance, "getKnowledgeProviderFor", player.getUUID());
            if (provider != NO_RESULT && provider != null) {
                return provider;
            }
            return providerFromKnowledgeCapability(player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 兜底入口：直接读取 PECapabilities.KNOWLEDGE_CAPABILITY 实体能力。 */
    private static Object providerFromKnowledgeCapability(ServerPlayer player) {
        try {
            Class<?> capabilities = loadClass(PROJECTE_CAPABILITIES);
            if (capabilities == null) {
                return null;
            }
            Object capability = capabilities.getField("KNOWLEDGE_CAPABILITY").get(null);
            Object provider = tryInvoke(player, "getCapability", capability);
            return provider == NO_RESULT ? null : provider;
        } catch (Throwable ignored) {
            return null;
        }
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
        Object result = tryInvoke(provider, "setEmc", BigInteger.valueOf(safeValue));
        if (result == NO_RESULT) {
            result = tryInvoke(provider, "setEmc", safeValue);
        }
        if (result == NO_RESULT) {
            tryInvoke(provider, "setEmc", Long.valueOf(safeValue));
        }
    }

    private static void learn(Object provider, ItemStack stack) {
        Object result = tryInvoke(provider, "addKnowledge", stack);
        if (result == NO_RESULT) {
            tryInvoke(provider, "learn", stack);
        }
    }

    private static boolean hasKnowledge(Object provider, ItemStack stack) {
        Object result = tryInvoke(provider, "hasKnowledge", stack);
        return result instanceof Boolean value && value;
    }

    private static void syncProjectE(Object provider, ServerPlayer player) {
        Object result = tryInvoke(provider, "sync", player);
        if (result == NO_RESULT) {
            tryInvoke(provider, "syncEmc", player);
        }
    }

    private static ItemStack extractedStack(Object result) {
        if (result == NO_RESULT || result == null) {
            return ItemStack.EMPTY;
        }
        ItemStack direct = asStack(result);
        if (!direct.isEmpty()) {
            return direct.copy();
        }
        ItemStack stack = asStack(tryInvoke(result, "toStack"));
        if (stack.isEmpty()) {
            stack = asStack(tryInvoke(result, "getStack"));
        }
        if (stack.isEmpty()) {
            stack = asStack(tryInvoke(result, "copyStack"));
        }
        return stack.copy();
    }

    private static long remainingAmount(Object result) {
        if (result == NO_RESULT || result == null) {
            return 0L;
        }
        if (result instanceof ItemStack stack) {
            return Math.max(0, stack.getCount());
        }
        long amount = amountOf(result);
        if (amount >= 0) {
            return Math.min(Integer.MAX_VALUE, amount);
        }
        ItemStack stack = extractedStack(result);
        return stack.isEmpty() ? 0L : stack.getCount();
    }

    private static int toItemCount(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, amount));
    }

    private static long amountOf(Object value) {
        if (value == NO_RESULT || value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        for (String method : new String[]{"amount", "getAmount", "getStackAmount"}) {
            Object result = tryInvoke(value, method);
            if (result instanceof Number number) {
                return Math.max(0L, number.longValue());
            }
        }
        return -1L;
    }

    private static ItemStack asStack(Object value) {
        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    private static ItemStack stackFromKeyAmount(Object value) {
        ItemStack stack = asStack(value);
        if (stack.isEmpty()) {
            stack = asStack(tryInvoke(value, "toStack"));
        }
        if (stack.isEmpty()) {
            stack = asStack(tryInvoke(value, "getStack"));
        }
        return stack;
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static ServerPlayer playerForNet(Object net) {
        try {
            Object players = tryInvoke(net, "getPlayers");
            if (!(players instanceof Iterable<?> iterable)) {
                return null;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return null;
            }
            for (Object uuid : iterable) {
                if (uuid instanceof UUID id) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        return player;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 缓存 getConstructors() 的克隆数组，避免每次点击/插入都重新分配。 */
    private static Constructor<?>[] constructors(Class<?> type) {
        Constructor<?>[] cached = CTOR_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
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
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != args.length) {
                continue;
            }
            if (!compatible(candidate.getParameterTypes(), args)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static Object tryInvokeStatic(Class<?> type, String method, Object... args) {
        if (type == null) {
            return NO_RESULT;
        }
        String key = "S" + signature(type, method, args);
        Object cached = INVOKE_CACHE.get(key);
        if (cached == MISS) {
            return NO_RESULT;
        }
        Method target;
        if (cached instanceof Method found) {
            target = found;
        } else {
            target = resolveMethod(type, method, args);
            INVOKE_CACHE.put(key, target == null ? MISS : target);
            if (target == null) {
                return NO_RESULT;
            }
        }
        try {
            return target.invoke(null, args);
        } catch (Throwable ignored) {
            return NO_RESULT;
        }
    }

    private static Object tryInvoke(Object target, String method, Object... args) {
        if (target == null || target == NO_RESULT) {
            return NO_RESULT;
        }
        Class<?> owner = target.getClass();
        String key = "I" + signature(owner, method, args);
        Object cached = INVOKE_CACHE.get(key);
        if (cached == MISS) {
            return NO_RESULT;
        }
        Method resolved;
        if (cached instanceof Method found) {
            resolved = found;
        } else {
            resolved = resolveMethod(owner, method, args);
            INVOKE_CACHE.put(key, resolved == null ? MISS : resolved);
            if (resolved == null) {
                return NO_RESULT;
            }
        }
        try {
            return resolved.invoke(target, args);
        } catch (Throwable ignored) {
            return NO_RESULT;
        }
    }

    private static boolean compatible(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!compatible(parameters[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean compatible(Class<?> parameter, Object arg) {
        if (arg == null) {
            return !parameter.isPrimitive();
        }
        Class<?> type = parameter.isPrimitive() ? wrapper(parameter) : parameter;
        return type.isAssignableFrom(arg.getClass())
                || (Number.class.isAssignableFrom(type) && arg instanceof Number);
    }

    private static Class<?> wrapper(Class<?> type) {
        if (type == long.class) {
            return Long.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
