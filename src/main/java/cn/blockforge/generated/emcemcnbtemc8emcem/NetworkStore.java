package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 玩家专属的网络接入设置与路由；数据随玩家存档保存。 */
public final class NetworkStore {
    private static final String ROOT = "emc_network_access";
    private static final String UNLOCKED = "unlocked";
    private static final String ENABLED = "enabled";
    private static final String PRIORITY = "priority";
    private static final String NBT_FILTER = "nbt_filter";
    private static final String CONVERT_HINT = "convert_hint";
    /** 旧版本遗留的后备仓库条目，本模组已不再写入，只在登录时退还给玩家。 */
    private static final String ITEMS = "items";
    private static final String COUNT = "count";

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
        }
        return root;
    }

    private static void saveRoot(ServerPlayer player, CompoundTag root) {
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        persisted.put(ROOT, root);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }

    public static boolean isUnlocked(ServerPlayer player) { return root(player).getBoolean(UNLOCKED); }
    public static boolean isEnabled(ServerPlayer player) { return root(player).getBoolean(ENABLED); }
    public static boolean isNbtFilter(ServerPlayer player) { return root(player).getBoolean(NBT_FILTER); }
    /** 老存档没有这个字段时默认开启，行为和升级前完全一样。 */
    public static boolean isConvertHint(ServerPlayer player) {
        CompoundTag tag = root(player);
        return !tag.contains(CONVERT_HINT) || tag.getBoolean(CONVERT_HINT);
    }
    public static boolean networkFirst(ServerPlayer player) { return "network".equals(root(player).getString(PRIORITY)); }

    public static void unlock(ServerPlayer player) {
        CompoundTag root = root(player);
        root.putBoolean(UNLOCKED, true);
        saveRoot(player, root);
    }

    public static void toggleEnabled(ServerPlayer player) {
        CompoundTag root = root(player);
        root.putBoolean(ENABLED, !root.getBoolean(ENABLED));
        saveRoot(player, root);
    }

    public static void togglePriority(ServerPlayer player) {
        CompoundTag root = root(player);
        root.putString(PRIORITY, networkFirst(player) ? "table" : "network");
        saveRoot(player, root);
    }

    public static void toggleNbtFilter(ServerPlayer player) {
        CompoundTag root = root(player);
        root.putBoolean(NBT_FILTER, !root.getBoolean(NBT_FILTER));
        saveRoot(player, root);
    }

    public static void toggleConvertHint(ServerPlayer player) {
        CompoundTag root = root(player);
        root.putBoolean(CONVERT_HINT, !isConvertHint(player));
        saveRoot(player, root);
    }

    /**
     * 按面板优先策略路由存入：转化桌优先或超越维度主网络。
     * 两边都收不下时原样返回，物品留在玩家背包里——本模组不再把它扣进一个
     * 玩家取不出来的私有仓库。
     */
    public static ItemStack deposit(ServerPlayer player, ItemStack input) {
        if (!isUnlocked(player) || !isEnabled(player) || input.isEmpty()) return input;
        ItemStack remaining = input.copy();
        boolean skipProjectE = isNbtFilter(player) && remaining.hasTag();
        if (!networkFirst(player) && !skipProjectE) remaining = OptionalBridges.depositProjectE(player, remaining);
        return OptionalBridges.depositBeyond(player, remaining);
    }

    /**
     * 老版本可能把「网络也收不下」的物品写进了私有仓库，而那份仓库如今没有取回入口。
     * 登录时把这些遗留条目退还给玩家并清空，避免物品永久卡在存档里。
     */
    public static void refundLegacyItems(ServerPlayer player) {
        CompoundTag root = root(player);
        if (!root.contains(ITEMS)) return;
        ListTag list = root.getList(ITEMS, Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            root.remove(ITEMS);
            saveRoot(player, root);
            return;
        }
        int refunded = 0;
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag entry)) continue;
            int count = entry.getInt(COUNT);
            if (count <= 0) continue;
            ItemStack stack = ItemStack.of(entry.getCompound("stack"));
            if (stack.isEmpty()) continue;
            stack.setCount(Math.min(stack.getMaxStackSize(), count));
            refunded += stack.getCount();
            player.getInventory().placeItemBackInInventory(stack);
        }
        root.remove(ITEMS);
        saveRoot(player, root);
        if (refunded > 0) {
            player.displayClientMessage(Component.translatable("message.emc_network.refunded", refunded), true);
        }
    }

    /**
     * 账本用的内存键：物品注册表数字 ID +（有 NBT 时）NBT 文本。
     * 该键只在一次同步里做映射、不落盘，因此用最便宜的数字 ID 而不是拼注册名。
     */
    public static String stackKey(ItemStack stack) {
        String id = Integer.toString(BuiltInRegistries.ITEM.getId(stack.getItem()));
        return stack.hasTag() ? id + "|" + stack.getTag() : id;
    }

    public static void sendStatus(ServerPlayer player) {
        ModNetwork.sendStatus(player, isUnlocked(player), isEnabled(player), networkFirst(player),
                isNbtFilter(player), isConvertHint(player));
    }
}
