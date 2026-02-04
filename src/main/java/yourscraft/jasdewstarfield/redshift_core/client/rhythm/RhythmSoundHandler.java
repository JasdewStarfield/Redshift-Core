package yourscraft.jasdewstarfield.redshift_core.client.rhythm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmExposureLogic;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmManager;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmPhase;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftSounds;

@EventBusSubscriber(modid = RedshiftCore.MODID, value = Dist.CLIENT)
public class RhythmSoundHandler {

    // 引用正在播放的一次性音效
    private static RhythmBlastSound currentOneShot = null;

    // 触发标记：防止在 Warning 阶段的每一 tick 重复播放
    private static boolean hasTriggeredWarning = false;

    private static float cachedExposure = 0.0f;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || mc.isPaused()) return;

        RhythmManager.RhythmState state = RhythmManager.getState(mc.level);
        RhythmPhase phase = state.phase();

        // 只有进入 Warning 阶段，且本轮尚未触发过，才开始播放
        if (phase == RhythmPhase.WARNING && !hasTriggeredWarning) {

            // 如果玩家完全不在区域内，可以直接不播
            float initialExposure = RhythmExposureLogic.getExposureFactor(mc.level, player);

            if (initialExposure > 0.01f) {
                // 创建实例并播放
                currentOneShot = new RhythmBlastSound(player, RedshiftSounds.BASALT_RHYTHM_BLAST.get());
                cachedExposure = initialExposure;
                mc.getSoundManager().play(currentOneShot);
            }

            // 标记已触发
            hasTriggeredWarning = true;
        }

        // 当律动回到 IDLE 状态时，重置触发标记，为下一轮做准备
        if (phase == RhythmPhase.IDLE) {
            hasTriggeredWarning = false;
            currentOneShot = null;
        }

        // 如果当前有音效正在播放 (SoundManager 没把它清理掉)，我们就更新它的音量
        if (currentOneShot != null && !currentOneShot.isStopped()) {

            // 性能优化：每 10 ticks 更新一次物理检测
            if (tickCounter++ % 10 == 0) {
                cachedExposure = RhythmExposureLogic.getExposureFactor(mc.level, player);
            }

            // 计算目标音量
            float targetVol = cachedExposure;
            currentOneShot.setTargetVolume(targetVol);
        }
    }
}
