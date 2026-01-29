package yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = RedshiftCore.MODID)
public class RhythmManager {

    public static final ResourceKey<Biome> BASALT_ORGAN = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("redshift", "basalt_organ"));

    // 循环周期：30秒 = 600 ticks
    public static final int CYCLE_TICKS = 600;

    // 阶段定义 (Ticks)
    // 0 - 460: 平静期
    // 460 - 540: 预警期 (80 ticks / 4秒)
    // 540 - 560: 爆发峰值 (20 ticks / 1秒) - 此时屏幕最白，伤害发生
    // 560 - 600: 衰退期 (40 ticks / 2秒)

    public static final int PHASE_WARN_START = 460;
    public static final int PHASE_BLAST_START = 540;
    public static final int PHASE_BLAST_END = 560;

    // 缓存每个世界的律动状态，Key 是 Dimension
    private static final Map<ResourceKey<Level>, RhythmState> STATE_CACHE = new HashMap<>();

    // 一个简单的数据记录类，包含所有需要频繁获取的数据
    public record RhythmState(RhythmPhase phase, float intensity, long cycleTime, boolean isBlastActive) {}

    // 默认空状态，防止空指针
    private static final RhythmState EMPTY_STATE = new RhythmState(RhythmPhase.IDLE, 0.0f, 0, false);

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        updateState(level);
    }

    private static void updateState(Level level) {
        long gameTime = level.getGameTime();
        long cycleTime = gameTime % CYCLE_TICKS;

        RhythmPhase phase;
        float intensity;
        boolean isBlast = false;

        // 复用之前的逻辑，但这里只运行一次
        if (cycleTime < PHASE_WARN_START) {
            phase = RhythmPhase.IDLE;
            intensity = 0.0f;
        } else if (cycleTime < PHASE_BLAST_START) {
            phase = RhythmPhase.WARNING;
            // 预警阶段强度计算
            if (cycleTime < PHASE_WARN_START + 10) {
                float progress = (float)(cycleTime - PHASE_WARN_START) / 10.0f;
                intensity = Mth.lerp(progress, 0.0f, 0.25f);
            } else {
                float progress = (float)(cycleTime - (PHASE_WARN_START + 10)) / (PHASE_BLAST_START - (PHASE_WARN_START + 10));
                intensity = Mth.lerp(progress, 0.25f, 0.35f);
            }
        } else if (cycleTime < PHASE_BLAST_END) {
            phase = RhythmPhase.BLAST;
            intensity = 0.8f;
            isBlast = true;
        } else {
            phase = RhythmPhase.DECAY;
            float progress = (float)(cycleTime - PHASE_BLAST_END) / (CYCLE_TICKS - PHASE_BLAST_END);
            intensity = Mth.lerp(progress, 0.8f, 0.0f);
        }

        // 更新缓存
        STATE_CACHE.put(level.dimension(), new RhythmState(phase, intensity, cycleTime, isBlast));
    }

    /**
     * 高频调用的接口：获取当前状态
     * O(1) 复杂度，无计算开销
     */
    public static RhythmState getState(Level level) {
        return STATE_CACHE.getOrDefault(level.dimension(), EMPTY_STATE);
    }

    /**
     * 便捷方法：获取强度
     */
    public static float getIntensity(Level level) {
        return getState(level).intensity();
    }

}
