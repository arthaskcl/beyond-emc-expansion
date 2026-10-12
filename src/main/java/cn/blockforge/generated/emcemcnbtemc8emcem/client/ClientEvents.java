package cn.blockforge.generated.emcemcnbtemc8emcem.client;

import cn.blockforge.generated.emcemcnbtemc8emcem.GeneratedMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端登录时只请求状态；EMC 功能槽由超越维度原生网络界面承载。 */
@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onLogin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        ClientState.requestStatus();
    }
}
