package yourscraft.jasdewstarfield.redshift_core.client.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;

@EventBusSubscriber(modid = RedshiftCore.MODID, value = Dist.CLIENT)
public class FogEventHandler {

    public static final ResourceKey<Biome> AEROSOL_MANGROVES = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("redshift", "aerosol_mangroves"));

    public static final ResourceKey<Biome> AEROSOL_MANGROVES_EDGE = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("redshift", "aerosol_mangroves_edge"));

    private static float currentFogWeight = 0.0f;
    private static long lastTime = 0;

    public static float getCurrentFogWeight() {
        return currentFogWeight;
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Entity entity = event.getCamera().getEntity();
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        Holder<Biome> biomeHolder = level.getBiome(pos);

        float targetWeight = calculateSampledTargetWeight(level, entity);

        long currentTime = System.currentTimeMillis();
        if (lastTime == 0) {
            lastTime = currentTime;
            // 初始加载瞬间完成
            currentFogWeight = targetWeight;
        }
        float dt = (currentTime - lastTime) / 1000.0f;
        lastTime = currentTime;

        float diff = Math.abs(targetWeight - currentFogWeight);
        float transitionSpeed = 0.8f * dt;

        if (diff > 0.5f) transitionSpeed *= 2.0f;

        if (currentFogWeight < targetWeight) {
            currentFogWeight = Math.min(currentFogWeight + transitionSpeed, targetWeight);
        } else if (currentFogWeight > targetWeight) {
            currentFogWeight = Math.max(currentFogWeight - transitionSpeed, targetWeight);
        }

        if (currentFogWeight > 0.01f) {
            applyFogSettings(event, level, currentFogWeight);
        }

        // 常规修正
        if (event.getMode() == FogRenderer.FogMode.FOG_TERRAIN) {
            float farPlaneDistance = event.getFarPlaneDistance();
            float newStart = farPlaneDistance * 0.25F;
            if (newStart < 0) newStart = 0;

            // 设置新的起始点
            event.setNearPlaneDistance(newStart);

            event.setFarPlaneDistance(farPlaneDistance);
            event.setCanceled(true);
        }
    }

    private static float calculateSampledTargetWeight(Level level, Entity entity) {
        // 1. 垂直高度衰减
        double y = entity.getY();
        float verticalFactor = 1.0f;

        if (y > 75) return 0.0f; // 太高了，完全没雾
        if (y > 63) {
            verticalFactor = 1.0f - (float)((y - 63) / 12.0);
            verticalFactor = Mth.clamp(verticalFactor, 0.0f, 1.0f);
        }

        // 2. 水平采样 (Horizontal Sampling)
        // 扫描以玩家为中心，半径 32 格内的生物群系分布
        // 网格: 5x5, 步长 16格 (-32, -16, 0, 16, 32)
        BlockPos playerPos = entity.blockPosition();
        int radius = 32;
        int step = 16;
        float totalWeight = 0f;
        int sampleCount = 0;

        for (int x = -radius; x <= radius; x += step) {
            for (int z = -radius; z <= radius; z += step) {
                sampleCount++;

                // 采样点坐标
                BlockPos samplePos = playerPos.offset(x, 0, z);
                Holder<Biome> b = level.getBiome(new BlockPos(samplePos.getX(), 60, samplePos.getZ()));

                if (b.is(AEROSOL_MANGROVES)) {
                    totalWeight += 1.0f; // 核心区贡献满分
                } else if (b.is(AEROSOL_MANGROVES_EDGE)) {
                    totalWeight += 0.4f; // 边缘区贡献低分
                }
            }
        }

        // 计算平均密度
        float density = totalWeight / sampleCount;

        density = Mth.clamp(density * 1.2f, 0.0f, 1.0f);

        return density * verticalFactor;
    }

    private static void applyFogSettings(ViewportEvent.RenderFog event, Level level, float weight) {
        long time = level.getGameTime();
        float breathing = (float) Math.sin(time * FogConfig.BREATHING_SPEED * 10) * 1.5f;

        float vanillaNear = event.getNearPlaneDistance();
        float vanillaFar = event.getFarPlaneDistance();

        // 自定义雾参数：
        float customNear = -2.0f;
        float customFar = 24.0f + breathing;

        // 基于 weight 插值
        float finalNear = Mth.lerp(weight, vanillaNear, customNear);
        float finalFar = Mth.lerp(weight, vanillaFar, customFar);

        // 如果原版雾比我们设定的还近（比如失明效果），保留原版
        if (finalFar < vanillaFar) {
            event.setNearPlaneDistance(finalNear);
            event.setFarPlaneDistance(finalFar);
            event.setCanceled(true);
        }
    }
}
