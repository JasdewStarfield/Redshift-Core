package yourscraft.jasdewstarfield.redshift_core.common.geyser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmBlastManager;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmManager;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmPhase;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftBlockEntities;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftParticles;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftSounds;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftTags;

import java.util.List;

public class GeyserBlockEntity extends BlockEntity {

    // 喷发高度 (格)
    private static final int ERUPTION_HEIGHT = 20;
    // 缓存的截断高度（绝对坐标 Y）。-1 表示缓存无效/未计算
    private int cachedCutoffY = -1;
    // 缓存的阻塞状态：如果根部就被堵死，则为 true
    private boolean cachedIsBlocked = false;

    private boolean hasPlayedSound = false;

    public GeyserBlockEntity(BlockPos pos, BlockState blockState) {
        super(RedshiftBlockEntities.GEYSER.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GeyserBlockEntity blockEntity) {
        // 1. 获取全局律动状态
        RhythmManager.RhythmState rhythmState = RhythmManager.getState(level);
        RhythmPhase phase = rhythmState.phase();

        // 2. 性能优化：如果是 IDLE 或者是 DECAY，直接跳过
        if (phase == RhythmPhase.IDLE || phase == RhythmPhase.DECAY) {
            if (blockEntity.cachedCutoffY != -1) {
                blockEntity.resetCache();
            }
            if (blockEntity.hasPlayedSound) {
                blockEntity.hasPlayedSound = false;
            }
            return;
        }

        if (blockEntity.cachedCutoffY == -1) {
            blockEntity.recalculateStructure(level, pos);
        }

        // 3. 客户端逻辑：视觉效果 (粒子)
        if (level.isClientSide) {
            handleClientParticles(level, pos, phase, blockEntity.cachedIsBlocked, blockEntity.cachedCutoffY);
            if (phase == RhythmPhase.BLAST && !blockEntity.cachedIsBlocked) {
                playEruptionSound(level, pos);
            }
            return;
        }

        // 4. 服务端逻辑：物理效果 (仅在爆发期)
        if (phase == RhythmPhase.BLAST) {
            blockEntity.handleEruptionPhysics(level, pos);
        }
    }

    private void recalculateStructure(Level level, BlockPos pos) {
        int startY = pos.getY() + 1;
        this.cachedCutoffY = startY + ERUPTION_HEIGHT;
        this.cachedIsBlocked = false;

        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(pos.getX(), startY, pos.getZ());

        for (int y = startY; y < startY + ERUPTION_HEIGHT; y++) {
            checkPos.setY(y);

            if (!isPermeable(level, checkPos)) {
                // 遇到障碍，记录截断高度
                this.cachedCutoffY = y;

                // 如果在第一格就遇到障碍，标记为完全阻塞
                if (y == startY) {
                    this.cachedIsBlocked = true;
                }
                break;
            }
        }
    }

    private void handleEruptionPhysics(Level level, BlockPos pos) {
        if (this.cachedIsBlocked) {
            // handleBlockedPressure(level, pos);
            return;
        }

        int startY = pos.getY() + 1;

        // 构建检测范围 AABB：从方块上方一直到最大高度
        AABB effectBox = new AABB(pos.getX(), startY, pos.getZ(), pos.getX() + 1, this.cachedCutoffY, pos.getZ() + 1).inflate(0.1);

        // 获取范围内所有实体
        List<Entity> entities = level.getEntities(null, effectBox);

        for (Entity entity : entities) {
            if (!RhythmBlastManager.isValidTarget(entity)) continue;

            // 计算实体脚部到喷口的垂直距离
            if (entity.getY() >= this.cachedCutoffY) continue;
            double distance = entity.getY() - (pos.getY() + 1);

            // 归一化距离 (0.0 = 喷口底端, 1.0 = 最高点)
            double distanceFactor = 1.0 - (distance / ERUPTION_HEIGHT);

            // 基础推力
            double baseStrength = 0.5;

            boolean isSneaking = entity.isShiftKeyDown();
            double sneakFactor = isSneaking ? 0.5 : 1.0;

            double finalVelocityY = (baseStrength + distanceFactor) * sneakFactor;

            Vec3 motion = entity.getDeltaMovement();

            // 施加推力：保留水平速度，覆盖垂直速度
            entity.setDeltaMovement(motion.x, finalVelocityY, motion.z);
            entity.fallDistance = 0;
            entity.hurtMarked = true;
        }
    }

    private static boolean isPermeable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // 1. 硬编码标签检查 (保留原有逻辑作为后备)
        if (state.is(RedshiftTags.Blocks.GEYSER_PERMEABLE)) return true;

        VoxelShape shape = state.getCollisionShape(level, pos);

        // 2. 空方块直接通过
        if (shape.isEmpty()) return true;

        // 3. 完整方块直接阻挡
        if (shape == Shapes.block()) return false;

        // 4. 几何检测
        return findMaxDepth(shape) == Double.POSITIVE_INFINITY;
    }

    private static double findMaxDepth(VoxelShape shape) {
        // 采样点坐标 (0-1 之间)
        double[][] DEPTH_TEST_COORDINATES = {
                {0.25, 0.25}, {0.25, 0.75}, {0.5, 0.5}, {0.75, 0.25}, {0.75, 0.75}
        };

        Direction.Axis axis = Direction.UP.getAxis();
        Direction.AxisDirection axisDirection = Direction.UP.getAxisDirection();
        double maxDepth = 0;

        for (double[] coordinates : DEPTH_TEST_COORDINATES) {
            double depth;
            if (axisDirection == Direction.AxisDirection.POSITIVE) {
                double min = shape.min(axis, coordinates[0], coordinates[1]);
                // 如果这一路都没碰到碰撞箱，返回无限大 -> 说明是透的
                if (min == Double.POSITIVE_INFINITY) return Double.POSITIVE_INFINITY;
                depth = min;
            } else {
                double max = shape.max(axis, coordinates[0], coordinates[1]);
                if (max == Double.NEGATIVE_INFINITY) return Double.POSITIVE_INFINITY;
                depth = 1 - max;
            }
            if (depth > maxDepth) maxDepth = depth;
        }
        return maxDepth;
    }

    private static void handleClientParticles(Level level, BlockPos pos, RhythmPhase phase, boolean isBlocked, int cutoffY) {
        if (isBlocked) return;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        // 预警期：冒少量烟
        if (phase == RhythmPhase.WARNING) {
            if (level.random.nextFloat() < 0.15f) {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        x + (level.random.nextGaussian() * 0.1), y, z + (level.random.nextGaussian() * 0.1),
                        0, 0.05, 0);
            }
        }
        // 爆发期：大量云雾/喷发粒子
        else if (phase == RhythmPhase.BLAST) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(RedshiftParticles.GEYSER_STEAM.get(),
                        x + (level.random.nextGaussian() * 0.2),
                        y,
                        z + (level.random.nextGaussian() * 0.2),
                        level.random.nextGaussian() * 0.1,
                        0.5 + (level.random.nextDouble() * 0.5),
                        level.random.nextGaussian() * 0.1
                );
            }
        }
    }

    private static void playEruptionSound(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof GeyserBlockEntity geyser)) return;

        // 如果本轮已经播放过，直接跳过
        if (geyser.hasPlayedSound) return;

        // 播放声音
        // volume: 0.8 (稍大), pitch: 0.8 ~ 1.2 (随机音调)
        level.playLocalSound(pos, RedshiftSounds.BASALT_GEYSER_ERUPT.get(),
                net.minecraft.sounds.SoundSource.BLOCKS,
                0.8f, 0.8f + level.random.nextFloat() * 0.4f, false);

        geyser.hasPlayedSound = true;
    }

    private void resetCache() {
        this.cachedCutoffY = -1;
        this.cachedIsBlocked = false;
    }
}
