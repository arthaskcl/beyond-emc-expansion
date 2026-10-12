package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 玩家专属的网络访问与开关状态；数据随玩家存档保存。 */
public final class NetworkStore {
    private static final String ROOT = "emc_network_access";
    private static final String UNLOCKED = "unlocked";
    private static final String ENABLED = "enabled";
    private static final String PRIORITY = "priority";
    private static final String NBT_FILTER = "nbt_filter";
    private static final String CONVERT_HINT = "convert_hint";

    private NetworkStore() {}

    private static CompoundTag root(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag root = persisted.getCompound(ROOT);
        if (!persisted.contains(ROOT)) {
            root.putBoolean(UNLOCKED, false);
            root.putBoolean(ENABLED, true);
            root.putBoolean(NBT_FILTER, true);
            root.putBoolean(CONVERT_HINT, true);
            root.putString(PRIORITY, "network");
            persisted.put(ROOT, root);
            player.getPersistentData().put("PlayerPersisted", persisted);
        } else if (!root.contains(CONVERT_HINT)) {
            // 老存档没有此开关，默认开启以保留升级前的提示行为。
            root.putBoolean(CONVERT_HINT, true);
            persisted.put(ROOT, root);
            player.getPersistentData().put("PlayerPersisted", persisted);
        }
        return root;
    }

    private static void saveRoot(ServerPlayer player, CompoundTag root) {
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        persisted.put(ROOT, root);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }

    public static boolean isUnlocked(ServerPlayer player) {
        return root(player).getBoolean(UNLOCKED);
    }

    public static boolean isEnabled(ServerPlayer player) {
        return root(player).getBoolean(ENABLED);
    }

    public static boolean isNbtFilter(ServerPlayer player) {
        return root(player).getBoolean(NBT_FILTER);
    }

    public static boolean networkFirst(ServerPlayer player) {
        return "network".equals(root(player).getString(PRIORITY));
    }

    public static void unlock(ServerPlayer player) {
        CompoundTag stored = root(player);
        stored.putBoolean(UNLOCKED, true);
        saveRoot(player, stored);
    }

    public static void toggleEnabled(ServerPlayer player) {
        CompoundTag stored = root(player);
        stored.putBoolean(ENABLED, !stored.getBoolean(ENABLED));
        saveRoot(player, stored);
    }

    public static void togglePriority(ServerPlayer player) {
        CompoundTag stored = root(player);
        stored.putString(PRIORITY, networkFirst(player) ? "table" : "network");
        saveRoot(player, stored);
    }

    public static void toggleNbtFilter(ServerPlayer player) {
        CompoundTag stored = root(player);
        stored.putBoolean(NBT_FILTER, !stored.getBoolean(NBT_FILTER));
        saveRoot(player, stored);
    }

    public static boolean isConvertHintEnabled(ServerPlayer player) {
        return root(player).getBoolean(CONVERT_HINT);
    }

    public static void toggleConvertHint(ServerPlayer player) {
        CompoundTag stored = root(player);
        stored.putBoolean(CONVERT_HINT, !stored.getBoolean(CONVERT_HINT));
        saveRoot(player, stored);
    }

    public static boolean hasExtraComponents(ItemStack stack) {
        return !ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
    }

    public static ItemStack loadStack(ServerPlayer player, CompoundTag tag) {
        return ItemStack.parseOptional(player.registryAccess(), tag);
    }

    public static CompoundTag saveStack(ServerPlayer player, ItemStack stack) {
        Tag saved = stack.saveOptional(player.registryAccess());
        return saved instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    /** 返回未存入的剩余量，物品绝不会凭空消失。 */
    public static ItemStack deposit(ServerPlayer player, ItemStack input) {
        if (!isUnlocked(player) || !isEnabled(player) || input.isEmpty()) {
            return input;
        }
        ItemStack remaining = input.copy();
        boolean skipProjectE = isNbtFilter(player) && hasExtraComponents(remaining);
        if (!networkFirst(player) && !skipProjectE) {
            remaining = OptionalBridges.depositProjectE(player, remaining);
        }
        return OptionalBridges.depositBeyond(player, remaining);
    }

    /** 网络中一件物品的唯一标识：注册表 ID + 组件序列化结果。 */
    public static String stackKey(ServerPlayer player, ItemStack stack) {
        return stack.getItem().builtInRegistryHolder().key().location() + "|" + saveStack(player, stack.copyWithCount(1));
    }

    public static void sendStatus(ServerPlayer player) {
        ModNetwork.sendStatus(player, isUnlocked(player), isEnabled(player), networkFirst(player),
                isNbtFilter(player), isConvertHintEnabled(player));
    }
}
