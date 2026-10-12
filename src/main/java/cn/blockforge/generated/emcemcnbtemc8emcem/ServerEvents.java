package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NetworkStore.refundLegacyItems(player);
            OptionalBridges.registerBeyondRoutingHandler();
            OptionalBridges.syncProjectEIntoBeyond(player);
            NetworkStore.sendStatus(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        // 没装超越维度时，同步本来就会被跳过；这里连账本解析都不做，保持零开销。
        if (!OptionalBridges.beyondAvailable()) return;
        OptionalBridges.syncProjectEIntoBeyond(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag oldPersisted = event.getOriginal().getPersistentData().getCompound("PlayerPersisted");
        CompoundTag newPersisted = event.getEntity().getPersistentData().getCompound("PlayerPersisted");
        if (oldPersisted.contains("emc_network_access")) {
            newPersisted.put("emc_network_access", oldPersisted.getCompound("emc_network_access").copy());
            if (oldPersisted.contains("emc_virtual_items")) {
                newPersisted.put("emc_virtual_items", oldPersisted.getList("emc_virtual_items", 10).copy());
            }
            if (oldPersisted.contains("emc_virtual_physical_items")) {
                newPersisted.put("emc_virtual_physical_items", oldPersisted.getList("emc_virtual_physical_items", 10).copy());
            }
            event.getEntity().getPersistentData().put("PlayerPersisted", newPersisted);
        }
    }
}
