package yourscraft.jasdewstarfield.redshift_core.common;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.common.logic.fog.FogLogic;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmExposureLogic;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmManager;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmPhase;

@EventBusSubscriber(modid = RedshiftCore.MODID)
public class DebugHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // 1. 仅在服务端运行 (逻辑验证)
        // 使用 instanceof 模式匹配，同时获取 ServerPlayer 对象
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 2. 检查时间间隔
        if (player.tickCount % 5 != 0) {
            return;
        }

        // 3. 检查主手是否持有指南针
        if (!player.getMainHandItem().is(Items.COMPASS)) return;

        // 用于构建最终消息的容器
        MutableComponent finalMessage = Component.empty();
        boolean hasContent = false;

        // ==========================================
        // 模块 A: 玄武岩管风琴 (Rhythm)
        // ==========================================

        float exposure = RhythmExposureLogic.getExposureFactor(player.level(), player);

        if (exposure > 0.001f) {
            RhythmManager.RhythmState state = RhythmManager.getState(player.level());

            // 根据阶段显示不同颜色 (爆发期变红)
            ChatFormatting color;
            if (state.phase() == RhythmPhase.BLAST) color = ChatFormatting.RED;
            else if (state.phase() == RhythmPhase.WARNING) color = ChatFormatting.GOLD;
            else color = ChatFormatting.GREEN;

            MutableComponent rhythmInfo = Component.literal("[律动] ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(state.phase().name()).withStyle(color))
                    .append(Component.literal(String.format(" 强度:%.0f%%", exposure * 100)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (Tick:" + state.cycleTime() + ")").withStyle(ChatFormatting.DARK_GRAY));

            finalMessage.append(rhythmInfo);
            hasContent = true;
        }

        // ==========================================
        // 模块 B: 毒雾机制 (Fog)
        // ==========================================

        // 调用核心逻辑计算浓度
        float intensity = FogLogic.getFogIntensity(player.level(), player.position());

        if (intensity > 0.001f) {
            // 获取昼夜因子作为额外调试信息
            float sunAngle = player.level().getSunAngle(1.0f);
            float dayFactor = (net.minecraft.util.Mth.cos(sunAngle) + 1.0f) / 2.0f;

            MutableComponent fogInfo = Component.literal("[毒雾] ").withStyle(ChatFormatting.DARK_GREEN)
                    .append(Component.literal(String.format(" 浓度:%.1f%%", intensity * 100)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(String.format(" (昼夜系数:" + dayFactor + ")")).withStyle(ChatFormatting.DARK_GRAY));

            finalMessage.append(fogInfo);
            hasContent = true;
        }

        // 发送消息给玩家
        if (hasContent) {
            player.displayClientMessage(finalMessage, true);
        }
    }
}
