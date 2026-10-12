package cn.blockforge.generated.emcemcnbtemc8emcem.client;

import cn.blockforge.generated.emcemcnbtemc8emcem.GeneratedMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** 把 EMC 功能槽挂到超越维度原生网络界面的左侧功能按钮旁。 */
@EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class BeyondNetworkIntegration {
    private static final String DIMENSIONS_NET_GUI = "com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI";
    private static final int BUTTON_SIZE = 16;
    private static final int E_BUTTON_HITBOX = 20;
    private static final int E_BUTTON_GAP = 4;
    private static final int PANEL_MIN_WIDTH = 76;
    private static final int PANEL_HEIGHT = 84;

    private BeyondNetworkIntegration() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isDimensionsNetwork(screen) || !ClientState.unlocked || !(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }

        int defaultFunctionX = container.getGuiLeft() - 18;
        int defaultFunctionY = container.getGuiTop() + 6 + 18 * 6;
        int lowestFunctionY = Integer.MIN_VALUE;
        int lowestFunctionX = defaultFunctionX;
        int targetFunctionY = Integer.MIN_VALUE;
        int targetFunctionX = defaultFunctionX;
        for (Object listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget
                    && widget.getX() < container.getGuiLeft()
                    && widget.getWidth() <= BUTTON_SIZE + 2
                    && widget.getHeight() <= BUTTON_SIZE + 2
                    && widget.getY() >= container.getGuiTop()) {
                // 左侧功能栏最底下通常是另一个原生按钮；取它上面的按钮作为目标，
                // 对应截图中被圈出的方格按钮，避免 E 跟着最底部按钮跑到背包物品旁边。
                if (widget.getY() > lowestFunctionY) {
                    targetFunctionY = lowestFunctionY;
                    targetFunctionX = lowestFunctionX;
                    lowestFunctionY = widget.getY();
                    lowestFunctionX = widget.getX();
                } else if (widget.getY() > targetFunctionY) {
                    targetFunctionY = widget.getY();
                    targetFunctionX = widget.getX();
                }
            }
        }
        if (targetFunctionY == Integer.MIN_VALUE) {
            targetFunctionY = defaultFunctionY;
            targetFunctionX = defaultFunctionX;
        }

        // E 放在截图中的方格按钮左侧，并保留清晰间隔；点击区域与图标一起移动。
        int buttonX = targetFunctionX - E_BUTTON_HITBOX - E_BUTTON_GAP;
        int buttonY = targetFunctionY - (E_BUTTON_HITBOX - BUTTON_SIZE) / 2;
        // 面板整体弹在 E 按钮的左侧（而不是按钮正上方）：面板比按钮宽得多，
        // 居中弹出会盖住右侧那一竖列超越维度原生功能键，挪到左边就完全避开。
        int panelWidth = computePanelWidth();
        int panelX = buttonX - panelWidth - 4;
        int panelY = buttonY + E_BUTTON_HITBOX / 2 - PANEL_HEIGHT / 2;
        panelX = Math.max(2, Math.min(panelX, screen.width - panelWidth - 2));
        panelY = Math.max(2, Math.min(panelY, screen.height - PANEL_HEIGHT - 2));
        NativeEmcPanel panel = new NativeEmcPanel(panelX, panelY, panelWidth, PANEL_HEIGHT);
        EmcSlotButton slotButton = new EmcSlotButton(buttonX, buttonY, panel);
        slotButton.setTooltip(Tooltip.create(Component.translatable("screen.emc_network.native_slot_tooltip")));
        event.addListener(slotButton);
        event.addListener(panel);
    }

    /** 面板宽度按当前语言下最长的一行文字自适应，去掉右侧空白。 */
    private static int computePanelWidth() {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        Component[] samples = {
                Component.translatable("screen.emc_network.native_title"),
                Component.translatable("screen.emc_network.enabled"),
                Component.translatable("screen.emc_network.disabled"),
                Component.translatable("screen.emc_network.priority_network"),
                Component.translatable("screen.emc_network.priority_table"),
                Component.translatable("screen.emc_network.nbt_on"),
                Component.translatable("screen.emc_network.nbt_off"),
                Component.translatable("screen.emc_network.convert_hint_on"),
                Component.translatable("screen.emc_network.convert_hint_off"),
        };
        int widest = 0;
        for (Component text : samples) {
            widest = Math.max(widest, font.width(text));
        }
        return Math.max(PANEL_MIN_WIDTH, widest + 12);
    }

    /** 网络优先级由本模组接管时，Shift+点击背包物品会进入统一路由。 */
    @SubscribeEvent
    public static void onNetworkInventoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
        // 先做最便宜的判断：不是左键+Shift 或不是容器界面时，不必检查类名。
        if (event.getButton() != 0 || !Screen.hasShiftDown()) {
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        if (!isDimensionsNetwork(event.getScreen()) || !ClientState.unlocked || !ClientState.enabled) {
            return;
        }
        Slot slot = container.getSlotUnderMouse();
        if (slot == null || slot.container != container.getMinecraft().player.getInventory() || slot.getItem().isEmpty()) {
            return;
        }
        ClientState.depositSlot(slot.getContainerSlot());
        event.setCanceled(true);
    }

    private static boolean isDimensionsNetwork(Screen screen) {
        Class<?> type = screen.getClass();
        while (type != null) {
            if (DIMENSIONS_NET_GUI.equals(type.getName()) || type.getName().endsWith("DimensionsNetGUI")) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static final class EmcSlotButton extends AbstractWidget {
        private final NativeEmcPanel panel;

        private EmcSlotButton(int x, int y, NativeEmcPanel panel) {
            super(x, y, E_BUTTON_HITBOX, E_BUTTON_HITBOX,
                    Component.translatable("screen.emc_network.native_slot"));
            this.panel = panel;
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            int background = isHoveredOrFocused() ? 0xFF80662A : 0xFF3A303E;
            gui.fill(getX(), getY(), getX() + width, getY() + height, background);
            gui.renderOutline(getX(), getY(), width, height,
                    ClientState.enabled ? 0xFFD6A93A : 0xFF696969);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            Component label = Component.translatable("screen.emc_network.native_slot");
            int textWidth = font.width(label);
            gui.drawString(font, label, getX() + (width - textWidth) / 2,
                    getY() + (height - font.lineHeight) / 2, 0xFFF1C75B);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            panel.visible = !panel.visible;
            setMessage(Component.translatable(panel.visible
                    ? "screen.emc_network.native_slot_open" : "screen.emc_network.native_slot"));
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
        }
    }

    private static final class NativeEmcPanel extends AbstractWidget {
        private NativeEmcPanel(int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("screen.emc_network.native_slot"));
            this.visible = false;
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            gui.fill(getX(), getY(), getX() + width, getY() + height, 0xF014101C);
            gui.renderOutline(getX(), getY(), width, height, ClientState.enabled ? 0xFFD6A93A : 0xFF696969);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            gui.drawString(font, Component.translatable("screen.emc_network.native_title"), getX() + 6, getY() + 6, 0xFFF1C75B);
            gui.drawString(font, Component.translatable(ClientState.enabled ? "screen.emc_network.enabled" : "screen.emc_network.disabled"), getX() + 6, getY() + 22, 0xFFFFFFFF);
            gui.drawString(font, Component.translatable(ClientState.networkFirst ? "screen.emc_network.priority_network" : "screen.emc_network.priority_table"), getX() + 6, getY() + 38, 0xFFFFFFFF);
            gui.drawString(font, Component.translatable(ClientState.nbtFilter ? "screen.emc_network.nbt_on" : "screen.emc_network.nbt_off"), getX() + 6, getY() + 54, 0xFFFFFFFF);
            gui.drawString(font, Component.translatable(ClientState.convertHint
                    ? "screen.emc_network.convert_hint_on" : "screen.emc_network.convert_hint_off"),
                    getX() + 6, getY() + 70, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!visible || button != 0 || !isMouseOver(mouseX, mouseY)) {
                return false;
            }
            int localY = (int) mouseY - getY();
            if (localY >= 16 && localY < 32) {
                ClientState.action(0);
            } else if (localY >= 32 && localY < 48) {
                ClientState.action(1);
            } else if (localY >= 48 && localY < 64) {
                ClientState.action(2);
            } else if (localY >= 64 && localY < 82) {
                ClientState.action(7);
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
        }
    }
}
