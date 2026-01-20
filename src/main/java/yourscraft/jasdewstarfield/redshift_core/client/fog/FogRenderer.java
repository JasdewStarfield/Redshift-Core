package yourscraft.jasdewstarfield.redshift_core.client.fog;

import com.mojang.blaze3d.vertex.*;
import dev.engine_room.flywheel.api.event.ReloadLevelRendererEvent;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class FogRenderer {

    private static final FogGenerator generator = new FogGenerator();

    private static final float MIN_BRIGHTNESS = 0.25f;

    public static FogGenerator getGenerator() {
        return generator;
    }

    @SubscribeEvent
    public static void onReloadLevelRenderer(ReloadLevelRendererEvent event) {
        ClientLevel level = event.level();

        VisualizationManager manager = VisualizationManager.get(level);
        if (manager != null) {
            System.out.println("[Redshift Debug] Adding FogEffect to VisualizationManager");
            manager.effects().queueAdd(new FogEffect(level));
        } else {
            System.out.println("[Redshift Debug] VisualizationManager is NULL! Flywheel backend might be OFF.");
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // 只有在游戏进行中且不暂停时更新
        if (mc.level != null && mc.player != null && !mc.isPaused()) {
            // 获取相机位置用于 LOD 计算
            net.minecraft.world.phys.Vec3 pos = mc.player.getEyePosition();
            long time = mc.level.getGameTime();

            // 驱动生成器更新逻辑
            generator.update(pos, time);
        }
    }

    static float getComplexBreathing(double x, double z, float time) {
        // 1. 主波 (Primary Wave): 较慢，波长中等
        float wave1 = Mth.sin(time + (float)x * 0.2f + (float)z * 0.2f);

        // 2. 次波 (Secondary Wave): 较快，方向不同，用于打破主波的规律性
        float wave2 = Mth.cos(time * 1.3f + (float)x * 0.5f - (float)z * 0.3f);

        // 3. 随机相位 (Random Phase): 基于坐标的伪随机数
        float randomPhase = (float) Math.sin(x * 12.989 + z * 78.233);

        // 组合：主波 + 次波的一半 + 随机扰动
        // 结果乘以幅度系数
        return (wave1 + wave2 * 0.5f + randomPhase * 0.2f) * FogConfig.BREATHING_AMPLITUDE;
    }

    static float calculateWorldBrightness(Minecraft mc, float partialTick) {
        if (mc.level == null) return 1.0f;

        // 1. 获取太阳角度
        float sunAngle = mc.level.getSunAngle(partialTick);
        // cos(0) = 1 (Day), cos(pi) = -1 (Night). 映射到 0~1
        float dayLight = Mth.cos(sunAngle) * 0.5f + 0.5f;
        dayLight = Mth.clamp(dayLight, 0.0f, 1.0f);

        // 2. 考虑天气
        float rain = mc.level.getRainLevel(partialTick);
        float thunder = mc.level.getThunderLevel(partialTick);

        // 雨天最多降低 20% 亮度，雷暴最多降低 50%
        dayLight *= (1.0f - rain * 0.2f);
        dayLight *= (1.0f - thunder * 0.5f);

        // 3. 混合最小亮度 (保留夜间荧光感)
        return MIN_BRIGHTNESS + (1.0f - MIN_BRIGHTNESS) * dayLight;
    }

    public static float computeFade(double dist, double maxDist) {
        if (dist > maxDist) return 0.0f;
        double fadeStart = maxDist - 32.0;
        if (dist < fadeStart) return 1.0f;
        return (float) ((maxDist - dist) / 32.0);
    }
}
