package cn.blockforge.generated.emcemcnbtemc8emcem.client;

import cn.blockforge.generated.emcemcnbtemc8emcem.GeneratedMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** 客户端登录时只请求状态；EMC 功能槽由超越维度原生网络界面承载。 */
@EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientState.requestStatus();
    }
}
