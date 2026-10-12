package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // 状态包已移除物品列表字段，通道版本从 "3" 升到 "4"，避免新旧客户端串包报错。
        PayloadRegistrar registrar = event.registrar("4");
        registrar.playToServer(ActionPacket.TYPE, ActionPacket.STREAM_CODEC, ActionPacket::handle);
        registrar.playToClient(StatusPacket.TYPE, StatusPacket.STREAM_CODEC, StatusPacket::handle);
    }

    public static void sendStatus(ServerPlayer player, boolean unlocked, boolean enabled, boolean networkFirst,
                                  boolean nbtFilter, boolean convertHint) {
        player.connection.send(new StatusPacket(unlocked, enabled, networkFirst, nbtFilter, convertHint));
    }

    /** 客户端 → 服务器：0 接入开关，1 优先策略，2 NBT过滤，5 请求状态，6 存入背包槽，7 兑换提示开关。 */
    public record ActionPacket(int action, int value, ItemStack stack) implements CustomPacketPayload {
        public static final Type<ActionPacket> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPacket> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ActionPacket::action,
                ByteBufCodecs.VAR_INT, ActionPacket::value,
                ItemStack.OPTIONAL_STREAM_CODEC, ActionPacket::stack,
                ActionPacket::new);

        @Override
        public Type<ActionPacket> type() {
            return TYPE;
        }

        public static void handle(ActionPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) {
                    return;
                }
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
                    case 6 -> depositInventorySlot(player, packet.value, packet.stack);
                    case 7 -> NetworkStore.toggleConvertHint(player);
                    // 动作 5 是"请求状态"：无需处理，走到底部的统一回发即可。
                    default -> {
                    }
                }
                NetworkStore.sendStatus(player);
            });
        }
    }

    private static void depositInventorySlot(ServerPlayer player, int slot, ItemStack sent) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return;
        }
        ItemStack actual = player.getInventory().getItem(slot);
        if (actual.isEmpty() || !ItemStack.isSameItemSameComponents(actual, sent)) {
            return;
        }
        ItemStack before = actual.copy();
        ItemStack remaining = NetworkStore.deposit(player, before);
        int accepted = before.getCount() - remaining.getCount();
        if (accepted > 0) {
            actual.shrink(accepted);
        }
    }

    /** 服务器 → 客户端：仅同步五个开关状态；物品展示由超越维度原生界面承担。 */
    public record StatusPacket(boolean unlocked, boolean enabled, boolean networkFirst, boolean nbtFilter,
                               boolean convertHint) implements CustomPacketPayload {
        public static final Type<StatusPacket> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatusPacket> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, StatusPacket::unlocked,
                ByteBufCodecs.BOOL, StatusPacket::enabled,
                ByteBufCodecs.BOOL, StatusPacket::networkFirst,
                ByteBufCodecs.BOOL, StatusPacket::nbtFilter,
                ByteBufCodecs.BOOL, StatusPacket::convertHint,
                StatusPacket::new);

        @Override
        public Type<StatusPacket> type() {
            return TYPE;
        }

        public static void handle(StatusPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> cn.blockforge.generated.emcemcnbtemc8emcem.client.ClientState.apply(packet));
        }
    }
}
