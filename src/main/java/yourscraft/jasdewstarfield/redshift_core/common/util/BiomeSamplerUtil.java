package yourscraft.jasdewstarfield.redshift_core.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public class BiomeSamplerUtil {

    /**
     * 计算周围区域目标群系的权重
     * @param level 世界
     * @param centerPos 中心坐标
     * @param radius 采样半径 (例如 32)
     * @param step 采样步长 (例如 16)
     * @param biomePredicate 判定是否为目标群系的逻辑
     * @return 0.0 ~ 1.0 的密度值
     */
    public static float sampleBiomeWeight(Level level, Vec3 centerPos, int radius, int step, Predicate<Holder<Biome>> biomePredicate) {
        BlockPos origin = BlockPos.containing(centerPos);
        float totalWeight = 0f;
        int sampleCount = 0;

        int y = origin.getY();

        // 简单的网格采样
        for (int x = -radius; x <= radius; x += step) {
            for (int z = -radius; z <= radius; z += step) {
                sampleCount++;

                BlockPos samplePos = origin.offset(x, 0, z);
                Holder<Biome> biome = level.getBiome(new BlockPos(samplePos.getX(), y, samplePos.getZ()));

                if (biomePredicate.test(biome)) {
                    totalWeight += 1.0f;
                }
            }
        }

        if (sampleCount == 0) return 0.0f;
        return Math.min(totalWeight / sampleCount, 1.0f);
    }
}
