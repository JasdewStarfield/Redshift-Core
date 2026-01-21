package yourscraft.jasdewstarfield.redshift_core.common;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;

@EventBusSubscriber(modid = RedshiftCore.MODID)
public class DebugHandler {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // 1. 仅在服务端运行 (逻辑验证)
        // 使用 instanceof 模式匹配，同时获取 ServerPlayer 对象
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 2. 检查时间间隔：每 2 秒 (40 ticks) 运行一次
        if (player.tickCount % 40 != 0) {
            return;
        }

        // 3. 检查主手是否持有指南针
        if (player.getMainHandItem().is(Items.COMPASS)) {
            // 调用核心逻辑计算浓度
            float intensity = FogLogic.getFogIntensity(player.level(), player.position());

            // 获取昼夜因子作为额外调试信息
            float sunAngle = player.level().getSunAngle(1.0f);
            float dayFactor = (net.minecraft.util.Mth.cos(sunAngle) + 1.0f) / 2.0f;

            // 格式化颜色代码：
            // §c[Redshift] §e浓度 §b昼夜系数
            String msg = String.format("§c[Redshift]§r 浓度: §e%.2f§r | 昼夜系数: §b%.2f§r | Y: %.1f",
                    intensity, dayFactor, player.getY());

            // 发送消息给玩家 (ActionBar 不会刷屏，Chat 会留底，这里选系统消息方便查看历史)
            player.sendSystemMessage(Component.literal(msg));
        }
    }
}
