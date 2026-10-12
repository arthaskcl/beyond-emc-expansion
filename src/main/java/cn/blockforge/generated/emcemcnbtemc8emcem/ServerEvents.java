package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = GeneratedMod.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OptionalBridges.registerBeyondRoutingHandler();
            OptionalBridges.syncProjectEIntoBeyond(player);
            NetworkStore.sendStatus(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) {
            return;
        }
        if (!OptionalBridges.beyondAvailable()) {
            return;
        }
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
