package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetwork {
    /** 状态包只带四个开关；不再同步物品列表（物品本来就从超越维度原生界面存取）。 */
    private static final String VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static int id = 0;

    private ModNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(id++, StatusPacket.class, StatusPacket::encode, StatusPacket::decode, StatusPacket::handle);
    }

    public static void sendStatus(ServerPlayer player, boolean unlocked, boolean enabled, boolean networkFirst,
                                   boolean nbtFilter, boolean convertHint) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new StatusPacket(unlocked, enabled, networkFirst, nbtFilter, convertHint));
    }

    public record ActionPacket(int action, int value, ItemStack stack) {
        public static void encode(ActionPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.action);
            buf.writeVarInt(packet.value);
            buf.writeItem(packet.stack);
        }
        public static ActionPacket decode(FriendlyByteBuf buf) {
            return new ActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readItem());
        }
        public static void handle(ActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                switch (packet.action) {
                    case 0 -> {
                        NetworkStore.toggleEnabled(player);
                        OptionalBridges.syncProjectEIntoBeyond(player);
                    }
                    case 1 -> {
                        NetworkStore.togglePriority(player);
                        OptionalBridges.syncProjectEIntoBeyond(player);
                    }
                    case 2 -> {
                        NetworkStore.toggleNbtFilter(player);
                        OptionalBridges.syncProjectEIntoBeyond(player);
                    }
                    case 8 -> NetworkStore.toggleConvertHint(player);
                    // case 5（客户端请求状态）只需走末尾的统一 sendStatus，避免重复发包。
                    case 6 -> depositInventorySlot(player, packet.value, packet.stack);
                    case 7 -> depositAllMatching(player, packet.value, packet.stack);
                    // 其余动作号一律不处理，只回一份当前状态。
                }
                NetworkStore.sendStatus(player);
            });
            context.setPacketHandled(true);
        }
    }

    private static void depositInventorySlot(ServerPlayer player, int slot, ItemStack sent) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;
        ItemStack actual = player.getInventory().getItem(slot);
        if (actual.isEmpty() || !ItemStack.isSameItemSameTags(actual, sent)) return;
        ItemStack before = actual.copy();
        ItemStack remaining = NetworkStore.deposit(player, before);
        int accepted = before.getCount() - remaining.getCount();
        if (accepted > 0) actual.shrink(accepted);
    }

    private static void depositAllMatching(ServerPlayer player, int clickedSlot, ItemStack sent) {
        if (clickedSlot < 0 || clickedSlot >= player.getInventory().getContainerSize() || sent.isEmpty()) return;
        // 以玩家真实背包里被点击的那一格为准，不信任客户端传来的物品描述。
        ItemStack clicked = player.getInventory().getItem(clickedSlot);
        if (clicked.isEmpty() || !ItemStack.isSameItemSameTags(clicked, sent)) return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack actual = player.getInventory().getItem(i);
            if (actual.isEmpty() || !ItemStack.isSameItemSameTags(actual, sent)) continue;
            ItemStack before = actual.copy();
            ItemStack remaining = NetworkStore.deposit(player, before);
            int accepted = before.getCount() - remaining.getCount();
            if (accepted > 0) actual.shrink(accepted);
        }
    }

    public record StatusPacket(boolean unlocked, boolean enabled, boolean networkFirst, boolean nbtFilter,
                               boolean convertHint) {
        public static void encode(StatusPacket packet, FriendlyByteBuf buf) {
            buf.writeBoolean(packet.unlocked);
            buf.writeBoolean(packet.enabled);
            buf.writeBoolean(packet.networkFirst);
            buf.writeBoolean(packet.nbtFilter);
            buf.writeBoolean(packet.convertHint);
        }
        public static StatusPacket decode(FriendlyByteBuf buf) {
            return new StatusPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean());
        }
        public static void handle(StatusPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> cn.blockforge.generated.emcemcnbtemc8emcem.client.ClientState.apply(packet)));
            context.setPacketHandled(true);
        }
    }
}
