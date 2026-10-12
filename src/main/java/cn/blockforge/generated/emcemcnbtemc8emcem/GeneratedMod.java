package cn.blockforge.generated.emcemcnbtemc8emcem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "emc_emc_nbt_emc_8_emc_em";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), MOD_ID);
    public static final RegistryObject<Item> ACCESS_RUNE = ITEMS.register("access_rune",
            () -> new AccessRuneItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.emc_emc_nbt_emc_8_emc_em.main"))
                    .icon(() -> new ItemStack(ACCESS_RUNE.get()))
                    .displayItems((parameters, output) -> output.accept(ACCESS_RUNE.get()))
                    .build());

    public GeneratedMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        CREATIVE_TABS.register(bus);
        ModNetwork.register();
    }
}
