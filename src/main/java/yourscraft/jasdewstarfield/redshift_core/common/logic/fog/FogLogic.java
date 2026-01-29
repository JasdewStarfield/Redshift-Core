package yourscraft.jasdewstarfield.redshift_core.common.logic.fog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import yourscraft.jasdewstarfield.redshift_core.client.fog.FogConfig;
import yourscraft.jasdewstarfield.redshift_core.common.util.BiomeSamplerUtil;

public class FogLogic {

    public static final ResourceKey<Biome> AEROSOL_MANGROVES = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("redshift", "aerosol_mangroves"));

    public static final ResourceKey<Biome> AEROSOL_MANGROVES_EDGE = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("redshift", "aerosol_mangroves_edge"));

    public enum FogType {
        NONE,
        CORE, // 核心区：永久存在
        EDGE  // 边缘区：随时间消散
    }

    /**
     * 获取当前的边缘扩散系数
     * @return 0.0 (夜间，收缩) ~ 1.0 (昼间，扩散)
     */
    public static float getEdgeExpansionFactor(Level level) {
        float sunAngle = level.getSunAngle(1.0f);
        float sunFactor = Mth.cos(sunAngle); // 1.0 = 正午, -1.0 = 午夜

        // 映射到 0~1，并加一点曲线让白天更长一些
        float factor = (sunFactor + 1.0f) / 2.0f;
        // 可以根据需要调整曲线，例如 factor = (float) Math.pow(factor, 0.5);

        return Mth.clamp(factor, 0.0f, 1.0f);
    }

    /**
     * 判断某个位置的雾类型
     */
    public static FogType getFogTypeAt(Level level, int x, int z) {
        // 为了性能，建议只在生成时调用，取 Y=60 避免洞穴生物群系干扰
        Holder<Biome> biome = level.getBiome(new BlockPos(x, 60, z));

        if (biome.is(AEROSOL_MANGROVES)) {
            return FogType.CORE;
        } else if (biome.is(AEROSOL_MANGROVES_EDGE)) {
            return FogType.EDGE;
        }
        return FogType.NONE;
    }

    /**
     * 获取指定位置的雾气浓度 (0.0 ~ 1.0)
     * 包含：高度衰减、生物群系权重、昼夜边缘收缩
     * @param level 世界对象
     * @param pos 实体/采样位置
     * @return 最终浓度值
     */
    public static float getFogIntensity(Level level, Vec3 pos) {
        // 1. 垂直高度检测
        float verticalFactor = getVerticalFactor(pos.y);
        if (verticalFactor <= 0.001f) return 0.0f;

        // 2. 计算时间因子
        float sunAngle = level.getSunAngle(1.0f);
        float rawDayFactor = (Mth.cos(sunAngle) + 1.0f) / 2.0f;

        // 映射到 0.0 ~ 1.0 (白天 1.0, 晚上 0.0)
        float dayNightFactor = (float) Math.pow(rawDayFactor, 3.0);

        // 边缘群系在深夜完全无雾，正午雾最浓
        float edgeWeight = 0.9f * dayNightFactor;

        // 3. 采样核心区
        float coreDensity = BiomeSamplerUtil.sampleBiomeWeight(level, pos, 32, 16, b -> b.is(AEROSOL_MANGROVES));

        // 4. 采样边缘区
        float edgeDensity = BiomeSamplerUtil.sampleBiomeWeight(level, pos, 32, 16, b -> b.is(AEROSOL_MANGROVES_EDGE));

        float totalDensity = coreDensity + edgeDensity * edgeWeight;
        return Mth.clamp(totalDensity * 1.2f, 0.0f, 1.0f) * verticalFactor;
    }

    private static float getVerticalFactor(double y) {
        // 让接近云层前浓度就开始淡化
        float startY = FogConfig.FOG_HEIGHT - 2;

        // 1. 硬上限检查
        float cutoffY = startY + FogConfig.FADE_RANGE;

        if (y > cutoffY) return 0.0f;

        // 2. 计算低于海平面的情况 (全浓度)
        if (y <= startY) return 1.0f;

        // 3. 计算衰减
        // normalizedDist: 0.0 (海平面) -> 1.0 (最高点)
        float normalizedDist = (float)((y - startY) / FogConfig.FADE_RANGE);

        // 先算线性剩余，再三次方
        float linearFactor = 1.0f - Mth.clamp(normalizedDist, 0.0f, 1.0f);
        return (float) Math.pow(linearFactor, 3);
    }
}
