package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "emc_emc_nbt_emc_8_emc_em";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredItem<Item> ACCESS_RUNE = ITEMS.register("access_rune",
            () -> new AccessRuneItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.emc_emc_nbt_emc_8_emc_em.main"))
                    .icon(() -> ACCESS_RUNE.toStack())
                    .displayItems((parameters, output) -> output.accept(ACCESS_RUNE.get()))
                    .build());

    public GeneratedMod(IEventBus modBus) {
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
        modBus.addListener(ModNetwork::register);
    }
}
