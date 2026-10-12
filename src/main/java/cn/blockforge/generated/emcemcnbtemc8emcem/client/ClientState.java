package cn.blockforge.generated.emcemcnbtemc8emcem.client;

import cn.blockforge.generated.emcemcnbtemc8emcem.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class ClientState {
    public static boolean unlocked;
    public static boolean enabled;
    public static boolean networkFirst;
    public static boolean nbtFilter = true;
    public static boolean convertHint = true;

    private ClientState() {}

    public static void apply(ModNetwork.StatusPacket packet) {
        unlocked = packet.unlocked();
        enabled = packet.enabled();
        networkFirst = packet.networkFirst();
        nbtFilter = packet.nbtFilter();
        convertHint = packet.convertHint();
    }

    public static void requestStatus() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(5, 0, ItemStack.EMPTY));
    }

    public static void action(int action) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(action, 0, ItemStack.EMPTY));
    }

    public static void depositSlot(int inventorySlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || inventorySlot < 0 || inventorySlot >= minecraft.player.getInventory().getContainerSize()) return;
        ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
        if (!stack.isEmpty()) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(6, inventorySlot, stack.copy()));
        }
    }

    public static void depositAllMatching(int inventorySlot, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || stack.isEmpty()
                || inventorySlot < 0 || inventorySlot >= minecraft.player.getInventory().getContainerSize()) return;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(7, inventorySlot, stack.copyWithCount(1)));
    }
}
