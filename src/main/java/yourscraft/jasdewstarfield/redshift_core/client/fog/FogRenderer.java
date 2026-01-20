package yourscraft.jasdewstarfield.redshift_core.client.fog;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class FogRenderer {

    private static final ResourceLocation FOG_TEXTURE = ResourceLocation.fromNamespaceAndPath("redshift",
            "textures/environment/fog_cloud.png");

    private static final FogGenerator generator = new FogGenerator();

    private static final float MIN_BRIGHTNESS = 0.25f;

    public static FogGenerator getGenerator() {
        return generator;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 只在半透明渲染后执行更新逻辑
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 1. 更新生成器逻辑 (CPU 计算)
        generator.update(event.getCamera().getPosition(), mc.level.getGameTime());

        // 2. 驱动 Flywheel 管理器 (实例更新)
        FogFlywheelManager.getInstance().tick(mc.level, event);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            FogFlywheelManager.getInstance().reset();
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
