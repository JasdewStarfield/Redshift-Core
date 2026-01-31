package yourscraft.jasdewstarfield.redshift_core.common.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftBlocks;

public class HexagonalBasaltFeature extends Feature<NoneFeatureConfiguration> {

    // 柱子直径设定
    private static final int HEX_SIZE = 6;
    private static final double BORDER_THRESHOLD = 1; // 缝隙大小

    // --- 噪音参数设定 ---
    // 1. 岛屿掩码：决定哪里有柱子。频率越低，岛屿越大。
    private static final double ISLAND_SCALE = 0.008;
    private static final double ISLAND_THRESHOLD = -0.35; // 阈值，大于此值才生成

    // 2. 峡谷/裂缝：用于切割跑道。频率较高，形成细长条纹。
    private static final double CANYON_SCALE = 0.06;
    private static final double CANYON_WIDTH = 0.12; // 裂缝宽度

    // 3. 高度地形：决定柱子高度。
    private static final double HEIGHT_SCALE = 0.02;

    public HexagonalBasaltFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        long seed = level.getSeed();

        boolean placedAny = false;

        int chunkMinX = origin.getX();
        int chunkMinZ = origin.getZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        // 1. 计算需要遍历的六边形坐标范围 (Q, R)
        // 扩大搜索范围，确保覆盖到所有中心点可能落在本 Chunk 的六边形
        long[] minHex = HexMath.blockToHex(chunkMinX, chunkMinZ, HEX_SIZE);
        long[] maxHex = HexMath.blockToHex(chunkMaxX, chunkMaxZ, HEX_SIZE);

        // 增加 buffer 确保不漏掉边缘
        long startQ = Math.min(minHex[0], maxHex[0]) - 2;
        long endQ   = Math.max(minHex[0], maxHex[0]) + 2;
        long startR = Math.min(minHex[1], maxHex[1]) - 2;
        long endR   = Math.max(minHex[1], maxHex[1]) + 2;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (long q = startQ; q <= endQ; q++) {
            for (long r = startR; r <= endR; r++) {
                int[] centerCoord = HexMath.hexToBlock(q, r, HEX_SIZE);
                int cx = centerCoord[0];
                int cz = centerCoord[1];

                // 1. 所有权检查：只处理中心点在当前 Chunk 的六边形
                if (cx < chunkMinX || cx > chunkMaxX || cz < chunkMinZ || cz > chunkMaxZ) {
                    continue;
                }

                BlockPos centerPos = new BlockPos(cx, 100, cz);

                // 2. 获取基准群系
                var centerBiomeHolder = getBiomeSafe(level, centerPos);
                var biomeKeyOpt = centerBiomeHolder.unwrapKey();
                if (biomeKeyOpt.isEmpty()) continue;
                ResourceKey<Biome> targetBiomeKey = biomeKeyOpt.get();

                // id 校验
                String targetId = targetBiomeKey.location().toString();
                if (!targetId.equals("redshift:basalt_organ")) {
                    continue;
                }

                int groundY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx, cz);

                // 边缘校验
                if (!isSafeFromBiomeEdge(level, cx, cz, groundY, targetBiomeKey)) {
                    continue;
                }

                // 3. 计算高度
                Integer surfaceY = calculateHexHeight(level, seed, cx, cz, q, r);
                if (surfaceY == null) continue;

                int relativeHeight = surfaceY - groundY;
                boolean isGroundLayer = (surfaceY <= groundY + 1);

                // 4. 生成柱子实体 (绘制六边形内的所有方块)
                if (placeHexColumn(level, mutablePos, cx, cz, q, r, surfaceY, isGroundLayer, relativeHeight)) {
                    placedAny = true;
                }
            }
        }

        return placedAny;
    }


    /**
     * 绘制单根六边形柱子
     */
    private boolean placeHexColumn(WorldGenLevel level, BlockPos.MutableBlockPos mutablePos,
                                   int cx, int cz, long q, long r, int surfaceY, boolean isGroundLayer, int relativeHeight) {
        boolean placed = false;
        RandomSource random = level.getRandom();

        // 扫描范围：中心点周围 (HEX_SIZE * 2) 的方块
        int scanRadius = (int)Math.ceil(HEX_SIZE * 2.0);

        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                int worldX = cx + dx;
                int worldZ = cz + dz;

                // 1. 几何裁剪：判断该点是否属于当前 (q, r) 六边形
                if (HexMath.getRadius(q, r, worldX, worldZ, HEX_SIZE) > BORDER_THRESHOLD) {
                    continue;
                }

                mutablePos.set(worldX, surfaceY, worldZ);

                if (level.isOutsideBuildHeight(mutablePos)) continue;

                // 3. 替换检查
                BlockState currentState = level.getBlockState(mutablePos);
                BlockState pillarBlock;
                BlockState topBlock;

                boolean isCenter = (dx == 0 && dz == 0);

                boolean canPlace;
                if (isGroundLayer) {
                    pillarBlock = Blocks.BLACKSTONE.defaultBlockState();

                    // 规则：极小概率 (0.5%) 在露天区域生成，且必须在中心
                    if (isCenter && random.nextFloat() < 0.005f) {
                        topBlock = RedshiftBlocks.GEYSER.get().defaultBlockState();
                    } else {
                        topBlock = Blocks.BLACKSTONE.defaultBlockState();
                    }

                    canPlace = currentState.canBeReplaced() ||
                            currentState.is(Blocks.BASALT) ||
                            currentState.is(Blocks.POLISHED_BASALT) ||
                            currentState.is(Blocks.BLACKSTONE);
                } else {
                    pillarBlock = Blocks.BASALT.defaultBlockState();

                    // 规则：小概率 (4%) 在"较低矮" (高度 < 25) 的石柱顶端生成，且必须在中心
                    if (isCenter && relativeHeight < 40 && random.nextFloat() < 0.04f) {
                        topBlock = RedshiftBlocks.GEYSER.get().defaultBlockState();
                    } else {
                        topBlock = Blocks.POLISHED_BASALT.defaultBlockState();
                    }

                    canPlace = currentState.canBeReplaced();
                }

                if (canPlace) {
                    level.setBlock(mutablePos, topBlock, 2);
                    placed = true;

                    // 4. 向下延伸柱身
                    int startY = surfaceY - 1;
                    int maxDepth = 100;
                    for (int y = startY; y > level.getMinBuildHeight() && (startY - y) < maxDepth; y--) {
                        mutablePos.setY(y);
                        BlockState stateBelow = level.getBlockState(mutablePos);

                        if (stateBelow.is(Blocks.BEDROCK)) break;
                        // 遇到实心方块停止
                        if (!stateBelow.canBeReplaced()) {
                            if (isGroundLayer || !stateBelow.is(Blocks.BLACKSTONE)) {
                                break;
                            }
                        }

                        BlockState blockToPlace;
                        if (!isGroundLayer && shouldBeBlackstone(level.getSeed(), worldX, y, worldZ)) {
                            blockToPlace = Blocks.BLACKSTONE.defaultBlockState();
                        } else {
                            blockToPlace = pillarBlock;
                        }
                        level.setBlock(mutablePos, blockToPlace, 2);
                    }
                }
            }
        }
        return placed;
    }

    /**
     * 边缘探测逻辑 (Perimeter Patrol)
     * 检查柱子最外圈的关键点。如果任何一个点落在了目标群系之外，
     * 说明这根柱子处于群系交界处，为了防止被切断，直接放弃生成。
     */
    private boolean isSafeFromBiomeEdge(WorldGenLevel level, int cx, int cz, int groundY,
                                        ResourceKey<Biome> targetKey) {
        // 探测半径：设置为柱子的最大视觉半径\
        double checkRadius = HEX_SIZE * 2.0;

        // 检测六边形的 6 个顶点
        for (int i = 0; i < 6; i++) {
            // 将角度转换为弧度 (60度 = PI/3)
            double angle = Math.toRadians(60 * i);

            // 计算探针坐标
            int px = cx + (int)(checkRadius * Math.cos(angle));
            int pz = cz + (int)(checkRadius * Math.sin(angle));

            // 保持和 placeHexColumn 一致的高度判定逻辑 (groundY + 2)
            BlockPos probePos = new BlockPos(px, groundY + 2, pz);

            // 使用安全的方法获取群系
            // 只要有一个角跑出去了，就视为不安全
            if (!getBiomeSafe(level, probePos).is(targetKey)) {
                return false;
            }
        }

        return true;
    }

    private Holder<Biome> getBiomeSafe(WorldGenLevel level, BlockPos pos) {
        // 使用 WorldGenLevel 的接口，它能处理好全局坐标到内部存储的转换
        return level.getNoiseBiome(
                QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ())
        );
    }

    /**
     * 核心逻辑：决定某个六边形是否存在、多高
     * 返回 null 表示此处不生成柱子 (空地/裂缝)
     */
    private Integer calculateHexHeight(WorldGenLevel level, long seed, int x, int z, long q, long r) {
        // A. 基础地面检查
        int groundY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        if (groundY <= level.getMinBuildHeight()) return null;

        // --- 1. 基础噪音采样 ---
        double maskNoise = pseudoPerlin(seed, x * ISLAND_SCALE, z * ISLAND_SCALE);
        double canyonNoise = pseudoPerlin(seed + 123, x * CANYON_SCALE, z * CANYON_SCALE);
        double heightNoise = pseudoPerlin(seed + 999, x * HEIGHT_SCALE, z * HEIGHT_SCALE);

        // --- 2. 判定逻辑状态 ---

        // 状态 A: 是否处于"柱子生长区" (由岛屿 Mask 决定)
        boolean hasColumn = (maskNoise >= ISLAND_THRESHOLD);

        // 状态 B: 是否处于"峡谷切割区" (由裂缝噪音决定)
        boolean isCanyon = (Math.abs(canyonNoise) < CANYON_WIDTH);

        // --- 3. 决策输出 ---
        if (isCanyon) {
            return groundY;
        }
        if (!hasColumn) {
            return groundY + 1;
        }

        // 高度计算
        int steppedHeight = getSteppedHeight(maskNoise, heightNoise);

        return groundY + steppedHeight;
    }

    private static int getSteppedHeight(double maskNoise, double heightNoise) {
        double edgeFactor = Mth.clamp((maskNoise - ISLAND_THRESHOLD) * 2.0, 0.0, 1.0);
        double rawHeight = 10 + (heightNoise + 1.0) / 2.0 * 55;
        rawHeight *= edgeFactor;

        // 阶梯化处理
        int dynamicStep;
        if (edgeFactor < 0.3) {
            dynamicStep = 2; // 边缘非常平缓
        } else if (edgeFactor < 0.6) {
            dynamicStep = 5; // 过渡带
        } else {
            dynamicStep = 10; // 中心宏伟的管风琴
        }

        return (int) (rawHeight / dynamicStep) * dynamicStep;
    }

    private boolean shouldBeBlackstone(long seed, int x, int y, int z) {
        // 1. 纹理缩放: 值越大，纹理越细碎
        double scale = 0.18;

        // 2. 模拟 3D 采样
        double sampleX = (x + y * 0.7) * scale;
        double sampleZ = (z - y * 0.7) * scale;

        // 3. 获取基础噪音
        double noise = pseudoPerlin(seed + 888, sampleX, sampleZ);

        // 4. 增加一点高频扰动
        double detailNoise = pseudoPerlin(seed + 999, sampleX * 2.0, sampleZ * 2.0);
        // 混合比例：80% 主噪音 + 20% 细节噪音
        double finalNoise = noise * 0.8 + detailNoise * 0.2;

        // 5. 脊状判定 (Ridge Noise)
        // 我们只取噪音值非常接近 0 的部分。
        // Math.abs(finalNoise) 会把噪音变成"V"字形的峡谷。
        // 阈值越小，裂纹越细。建议 0.1 ~ 0.15。
        double crackWidth = 0.1;

        return Math.abs(finalNoise) < crackWidth;
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
