package cn.blockforge.generated.emcemcnbtemc8emcem.client;

import cn.blockforge.generated.emcemcnbtemc8emcem.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** 客户端缓存的开关状态；由服务器 StatusPacket 单向同步。 */
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
        send(new ModNetwork.ActionPacket(5, 0, ItemStack.EMPTY));
    }

    public static void action(int action) {
        send(new ModNetwork.ActionPacket(action, 0, ItemStack.EMPTY));
    }

    public static void depositSlot(int inventorySlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || inventorySlot < 0 || inventorySlot >= minecraft.player.getInventory().getContainerSize()) {
            return;
        }
        ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
        if (!stack.isEmpty()) {
            send(new ModNetwork.ActionPacket(6, inventorySlot, stack.copy()));
        }
    }

    private static void send(ModNetwork.ActionPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().send(packet);
        }
    }
}
