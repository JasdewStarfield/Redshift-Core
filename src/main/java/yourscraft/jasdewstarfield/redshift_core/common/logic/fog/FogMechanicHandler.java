package yourscraft.jasdewstarfield.redshift_core.common.logic.fog;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;

@EventBusSubscriber(modid = RedshiftCore.MODID)
public class FogMechanicHandler {

    // 检查间隔：每 20 ticks (1秒) 执行一次逻辑
    // 过于频繁的检测没有必要，且药水效果通常以秒为单位
    private static final int CHECK_INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // 1. 仅在服务端运行，且针对 ServerPlayer
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 2. 间隔检查
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        // 3. 创造/旁观模式免疫
        if (player.isCreative() || player.isSpectator()) {
            //return;
        }

        // 4. 获取当前位置的雾气浓度
        float density = FogLogic.getFogIntensity(player.level(), player.position());

        if (density > 0.5f) {
            // 判断是否是高浓度区域 (> 0.8)
            boolean isLethal = density > 0.8f;
            // 放大倍率: 0 = 中毒 I, 1 = 中毒 II
            int amplifier = isLethal ? 1 : 0;

            // 持续时间：10s。
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, amplifier, true, false));
        }
    }
}
