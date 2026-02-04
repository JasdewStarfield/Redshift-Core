package yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import yourscraft.jasdewstarfield.redshift_core.common.util.BiomeSamplerUtil;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftTags;

public class RhythmExposureLogic {

    // 采样半径：检测周围多少格的地形
    private static final int CHECK_RADIUS = 7;

    // 垂直扫描步长：每次跳过多少格
    private static final int VERTICAL_STEP = 2;

    // 最大有效距离：超过这个距离就不再产生影响
    private static final float MAX_DANGER_DISTANCE = 80.0f;

    // 采样偏移点 (中心 + 四个方向)
    private static final int[][] SAMPLE_OFFSETS = {
            {0, 0},                     // 脚下
            {CHECK_RADIUS, 0},          // 东
            {-CHECK_RADIUS, 0},         // 西
            {0, CHECK_RADIUS},          // 南
            {0, -CHECK_RADIUS},         // 北
            //对角线
            {CHECK_RADIUS, CHECK_RADIUS},
            {CHECK_RADIUS, -CHECK_RADIUS},
            {-CHECK_RADIUS, CHECK_RADIUS},
            {-CHECK_RADIUS, -CHECK_RADIUS}
    };

    public static float getRhythmBiomeWeight(Level level, Entity entity) {
        return BiomeSamplerUtil.sampleBiomeWeight(
                level,
                entity.position(),
                32, 16,
                holder -> holder.is(RhythmManager.BASALT_ORGAN)
        );
    }

    public static float getExposureFactor(Level level, Entity entity) {
        // 1. 群系权重 (Biome Weight) - 如果不在群系，直接返回 0
        float biomeWeight = getRhythmBiomeWeight(level, entity);

        if (biomeWeight <= 0.01f) return 0.0f;

        // 2. 物理环境判定 (Physical Exposure)
        float physicalFactor = calculatePhysicalExposure(level, entity);

        return biomeWeight * physicalFactor;
    }

    private static float calculatePhysicalExposure(Level level, Entity entity) {
        BlockPos centerPos = entity.blockPosition();

        double minDistanceSq = Double.MAX_VALUE;
        double maxDistSq = MAX_DANGER_DISTANCE * MAX_DANGER_DISTANCE;

        // 3. 循环采样点 (稀疏采样)
        for (int[] offset : SAMPLE_OFFSETS) {
            int checkX = centerPos.getX() + offset[0];
            int checkZ = centerPos.getZ() + offset[1];

            // 找出该列的震源高度
            int sourceY = findSeismicSourceHeight(level, checkX, checkZ, centerPos.getY());

            // 如果没找到震源，跳过
            if (sourceY == Integer.MIN_VALUE) continue;

            // 计算欧几里得距离的平方
            // d^2 = dx^2 + dz^2 + dy^2
            double dx = offset[0];
            double dz = offset[1];
            double dy = centerPos.getY() - sourceY;

            double distSq = (dx * dx) + (dz * dz) + (dy * dy);

            // 我们只关心最近的那个震源
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;

                // 极速剪枝：如果已经贴脸了 (距离 < 2)，不需要再找了，直接返回最大值
                if (minDistanceSq < 4.0) break;
            }
        }

        // 如果所有点都找不到震源，说明绝对安全
        if (minDistanceSq >= maxDistSq) return 0.0f;

        // 4. 转换距离为系数
        // 距离 0 -> 1.0
        // 距离 32 -> 0.0
        double distance = Math.sqrt(minDistanceSq);
        return (float) (1.0 - (distance / MAX_DANGER_DISTANCE));
    }

    private static int findSeismicSourceHeight(Level level, int x, int z, int playerY) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int startY = Math.max(surfaceY, playerY);
        int bottomY = level.getMinBuildHeight();

        int scanLimitY = startY - 80;
        int stopY = Math.max(bottomY, scanLimitY);

        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos(x, startY, z);

        for (int y = startY; y >= stopY; y -= VERTICAL_STEP) {
            mPos.setY(y);
            BlockState state = level.getBlockState(mPos);

            if (state.is(RedshiftTags.Blocks.RESONANT_BLOCKS)) {
                return y;
            }
        }

        return Integer.MIN_VALUE; // 未找到
    }
}
