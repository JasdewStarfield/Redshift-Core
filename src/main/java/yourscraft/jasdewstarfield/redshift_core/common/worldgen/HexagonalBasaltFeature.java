package yourscraft.jasdewstarfield.redshift_core.common.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.HashMap;
import java.util.Map;

public class HexagonalBasaltFeature extends Feature<NoneFeatureConfiguration> {

    // 柱子的半径（方块数）
    private static final int HEX_SIZE = 5;
    // 缝隙阈值 (0.0 ~ 1.0)
    private static final double BORDER_THRESHOLD = 1;
    // 团簇生成的半径
    private static final int CLUSTER_RADIUS_MIN = 12;
    private static final int CLUSTER_RADIUS_MAX = 36;

    public HexagonalBasaltFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // 1. 随机决定这个“管风琴团簇”的大小
        int clusterRadius = random.nextInt(CLUSTER_RADIUS_MAX - CLUSTER_RADIUS_MIN + 1) + CLUSTER_RADIUS_MIN;
        int rSquared = clusterRadius * clusterRadius;

        boolean placedAny = false;

        // 预定义方块状态
        BlockState basaltState = Blocks.BASALT.defaultBlockState();
        BlockState topState = Blocks.POLISHED_BASALT.defaultBlockState();

        // 缓存。Key: hexKey (将 q 和 r 压缩成一个 long), Value: 柱子的最终表面高度 Y
        Map<Long, Integer> hexHeightCache = new HashMap<>();

        // 2. 遍历以 origin 为中心的区域
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = -clusterRadius; x <= clusterRadius; x++) {
            for (int z = -clusterRadius; z <= clusterRadius; z++) {

                // 简单的圆形距离检查，只生成圆内的柱子
                if (x * x + z * z > rSquared) continue;

                // 转换为绝对世界坐标
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;

                // --- 3. 六边形网格计算 ---
                // 关键：必须使用 worldX/worldZ，这样不同团簇之间的网格依然是全局对齐的
                long[] hexCoord = HexMath.blockToHex(worldX, worldZ, HEX_SIZE);
                long q = hexCoord[0];
                long r = hexCoord[1];

                // --- 4. 缝隙处理 ---
                double hexDist = HexMath.getRadius(q, r, worldX, worldZ, HEX_SIZE);
                if (hexDist > BORDER_THRESHOLD) {
                    continue;
                }

                // --- 5. 获取/计算该六边形的高度 ---
                // 确保同一个六边形内的所有方块拿到同一个高度
                long hexKey = (q << 32) | (r & 0xFFFFFFFFL);

                Integer surfaceY;

                if (hexHeightCache.containsKey(hexKey)) {
                    surfaceY = hexHeightCache.get(hexKey);
                } else {
                    // 如果缓存里没有，说明是第一次遇到这个六边形，开始计算它的属性
                    int[] centerCoord = HexMath.hexToBlock(q, r, HEX_SIZE);
                    int centerX = centerCoord[0];
                    int centerZ = centerCoord[1];

                    // A. 获取地面高度
                    int groundY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, centerX, centerZ);

                    // 如果中心点悬空或太低，标记为不可生成
                    if (groundY <= level.getMinBuildHeight()) {
                        surfaceY = null;
                    } else {
                        // B. 计算噪音高度
                        double noise = getNaturalHeight(level.getSeed(), q, r);

                        // C. 计算边缘淡出
                        // 使用【六边形中心】到【团簇原点】的距离，而不是当前方块的距离
                        double distFromOrigin = Math.sqrt(Math.pow(centerX - origin.getX(), 2) + Math.pow(centerZ - origin.getZ(), 2));
                        double distFactor = distFromOrigin / clusterRadius;

                        // 使用余弦插值让顶部更平坦，边缘下降更陡峭，形成高原感
                        double fade = Math.cos(distFactor * Math.PI / 2.0);
                        fade = Mth.clamp(fade, 0.0, 1.0);

                        // D. 最终高度计算
                        int heightOffset = (int) ((10 + noise * 50) * fade);

                        // 只有高度 > 1 才生成，避免地面上只有一层薄皮
                        if (heightOffset < 2) {
                            surfaceY = null;
                        } else {
                            surfaceY = groundY + heightOffset;
                        }
                    }
                    // 存入缓存
                    hexHeightCache.put(hexKey, surfaceY);
                }

                // 如果当前六边形被标记为不生成，则跳过
                if (surfaceY == null) continue;

                // --- 7. 放置方块 ---
                mutablePos.set(worldX, surfaceY, worldZ);

                if (!level.isOutsideBuildHeight(mutablePos)) {
                    // 放置柱顶
                    if (level.getBlockState(mutablePos).canBeReplaced()) {
                        level.setBlock(mutablePos, topState, 2);
                        placedAny = true;
                    }

                    // 向下延伸
                    int startY = surfaceY - 1;
                    int maxDepth = 100;
                    for (int y = startY; y > level.getMinBuildHeight() && (startY - y) < maxDepth; y--) {
                        mutablePos.setY(y);
                        BlockState stateBelow = level.getBlockState(mutablePos);

                        if (stateBelow.is(Blocks.BEDROCK)) break;

                        // 如果是不可替换的固体（如石头），就停止
                        if (stateBelow.isSolidRender(level, mutablePos) && !stateBelow.canBeReplaced()) {
                            // 如果是同类玄武岩，说明和下面的地形融合了，也可以停
                            if (!stateBelow.is(Blocks.BASALT) && !stateBelow.is(Blocks.POLISHED_BASALT)) {
                                break;
                            }
                        }
                        level.setBlock(mutablePos, basaltState, 2);
                    }
                }
            }
        }

        return placedAny;
    }

    // --- 噪音算法 (针对六边形坐标 q, r) ---
    private double getNaturalHeight(long seed, long q, long r) {
        double trend = pseudoPerlin(seed, q * 0.1, r * 0.1);
        double detail = pseudoPerlin(seed + 12345, q * 0.3, r * 0.3) * 0.3;
        double jitter = (coordHash(q, r) % 100) / 100.0 * 0.15;
        return Mth.clamp((trend + detail + jitter + 1) / 2.0, 0.0, 1.0);
    }

    // --- 内部数学类 ---
    private static class HexMath {
        private static final double SQRT_3 = 1.7320508075688772;

        /**
         * 将世界坐标 (x, z) 转换为六边形轴向坐标 (q, r)
         * 对应 Hex.blockToHex
         */
        public static long[] blockToHex(double x, double z, double size) {
            double q = ((2d / 3d) * x) / size;
            double r = ((-1d / 3d) * x + (SQRT_3 / 3d) * z) / size;
            return hexRound(q, r);
        }

        /**
         * 将六边形坐标 (q, r) 转回世界坐标中心 (x, z)
         * 对应 Hex.hexToBlock
         */
        public static int[] hexToBlock(long q, long r, double size) {
            double x = size * ((3d / 2d) * q);
            double z = size * ((SQRT_3 / 2d) * q + SQRT_3 * r);
            return new int[]{(int) Math.round(x), (int) Math.round(z)};
        }

        /**
         * 四舍五入到最近的六边形
         * 对应 Hex.hexRound
         */
        private static long[] hexRound(double q, double r) {
            double s = -q - r;
            long rq = Math.round(q);
            long rr = Math.round(r);
            long rs = Math.round(s);
            double dq = Math.abs(rq - q);
            double dr = Math.abs(rr - r);
            double ds = Math.abs(rs - s);
            if (dq > dr && dq > ds) {
                rq = -rr - rs;
            } else if (dr > ds && dr > dq) {
                rr = -rq - rs;
            }
            return new long[]{rq, rr};
        }

        /**
         * 计算点到六边形中心的相对距离 (0.0 ~ 1.0)
         * 用于判定是否在边缘（缝隙）
         * 对应 Hex.radius
         */
        public static double getRadius(long q, long r, double x, double z, double size) {
            double fq = ((2d / 3d) * x) / size;
            double fr = ((-1d / 3d) * x + (SQRT_3 / 3d) * z) / size;
            double s = -q - r;
            double fs = -fq - fr;
            double dq = q - fq;
            double dr = r - fr;
            double ds = s - fs;
            double qr = Math.abs(dq - dr);
            double rs = Math.abs(dr - ds);
            double sq = Math.abs(ds - dq);
            return (1d / 2d) * (qr + rs + sq);
        }
    }

    // 一个极简的伪 2D 噪音实现
    private double pseudoPerlin(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        double sx = x - x0;
        double sz = z - z0;
        double n00 = dotGridGradient(seed, x0, z0, x, z);
        double n10 = dotGridGradient(seed, x1, z0, x, z);
        double n01 = dotGridGradient(seed, x0, z1, x, z);
        double n11 = dotGridGradient(seed, x1, z1, x, z);
        double ix0 = Mth.lerp(sx, n00, n10);
        double ix1 = Mth.lerp(sx, n01, n11);
        return Mth.lerp(sz, ix0, ix1);
    }

    private double dotGridGradient(long seed, int ix, int iz, double x, double z) {
        long hash = coordHash(ix, iz) ^ seed;
        double angle = (hash & 1023) / 1024.0 * Math.PI * 2;
        double dx = x - (double) ix;
        double dz = z - (double) iz;
        return dx * Math.cos(angle) + dz * Math.sin(angle);
    }

    private long coordHash(long x, long z) {
        long h = x * 3129871 ^ z * 11612973433L ^ x;
        h = h * h * 42317861L + h * 11L;
        return h;
    }
}
