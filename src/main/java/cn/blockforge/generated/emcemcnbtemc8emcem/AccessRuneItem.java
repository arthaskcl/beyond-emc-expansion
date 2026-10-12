package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class AccessRuneItem extends Item {
    public AccessRuneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!NetworkStore.isUnlocked(serverPlayer)) {
                NetworkStore.unlock(serverPlayer);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.displayClientMessage(Component.translatable("message.emc_network.unlocked"), true);
                player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
            } else {
                NetworkStore.toggleEnabled(serverPlayer);
                boolean enabled = NetworkStore.isEnabled(serverPlayer);
                player.displayClientMessage(Component.translatable(enabled
                        ? "message.emc_network.enabled" : "message.emc_network.disabled"), true);
                player.playNotifySound(enabled ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
                        SoundSource.PLAYERS, 0.7f, enabled ? 1.3f : 0.7f);
            }
            // 解锁与切换的收尾动作相同：注册拦截、对齐账本、回发状态，只需做一次。
            OptionalBridges.registerBeyondRoutingHandler();
            OptionalBridges.syncProjectEIntoBeyond(serverPlayer);
            NetworkStore.sendStatus(serverPlayer);
        } else if (level.isClientSide) {
            for (int i = 0; i < 5; i++) {
                level.addParticle(ParticleTypes.ENCHANT,
                        player.getX() + (level.random.nextDouble() - 0.5) * 0.8,
                        player.getY() + 1.0 + level.random.nextDouble() * 0.7,
                        player.getZ() + (level.random.nextDouble() - 0.5) * 0.8,
                        0.8, 0.55, 0.1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.emc_emc_nbt_emc_8_emc_em.access_rune.tooltip"));
        tooltip.add(Component.translatable("item.emc_emc_nbt_emc_8_emc_em.access_rune.tooltip2"));
    }
}
