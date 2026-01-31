package yourscraft.jasdewstarfield.redshift_core.common.geyser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmBlastManager;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmManager;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmPhase;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftBlockEntities;

import java.util.List;

public class GeyserBlockEntity extends BlockEntity {

    // 喷发高度 (格)
    private static final int ERUPTION_HEIGHT = 20;
    // 最大推力 (Y轴速度)
    private static final double MAX_PUSH_STRENGTH = 3.0;

    public GeyserBlockEntity(BlockPos pos, BlockState blockState) {
        super(RedshiftBlockEntities.GEYSER.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GeyserBlockEntity blockEntity) {
        // 1. 获取全局律动状态
        RhythmManager.RhythmState rhythmState = RhythmManager.getState(level);
        RhythmPhase phase = rhythmState.phase();

        // 2. 性能优化：如果是 IDLE 或者是 DECAY，直接跳过
        if (phase == RhythmPhase.IDLE || phase == RhythmPhase.DECAY) {
            return;
        }

        // 3. 客户端逻辑：视觉效果 (粒子)
        if (level.isClientSide) {
            handleClientParticles(level, pos, phase);
            return;
        }

        // 4. 服务端逻辑：物理效果 (仅在爆发期)
        if (phase == RhythmPhase.BLAST) {
            handleEruptionPhysics(level, pos);
        }
    }

    private static void handleEruptionPhysics(Level level, BlockPos pos) {
        // 构建检测范围 AABB：从方块上方一直到最大高度
        // inflate(0.1) 是为了稍微宽容一点，防止玩家站在边缘没反应
        AABB effectBox = new AABB(pos).expandTowards(0, ERUPTION_HEIGHT, 0).inflate(0.1, 0, 0.1);

        // 获取范围内所有实体
        List<Entity> entities = level.getEntities(null, effectBox);

        for (Entity entity : entities) {
            if (!RhythmBlastManager.isValidTarget(entity)) continue;

            // 计算实体脚部到喷口的垂直距离
            double distance = entity.getY() - (pos.getY() + 1);

            // 简单防护
            if (distance < 0 || distance > ERUPTION_HEIGHT) continue;

            // 归一化距离 (0.0 = 喷口底端, 1.0 = 最高点)
            // 距离越近，distanceFactor 越大 (接近 1.0)
            double distanceFactor = 1.0 - (distance / ERUPTION_HEIGHT);

            // 力度计算公式：距离越近推力越大
            // 使用平方曲线让底部的推力显著大于顶部
            double pushY = 0.2 + (MAX_PUSH_STRENGTH * distanceFactor * distanceFactor);

            Vec3 motion = entity.getDeltaMovement();

            // 施加推力：保留水平速度，覆盖垂直速度
            // 如果你想让它即使在空中也被推得更高，可以用 add，但 set 更稳定，防止累积过快
            entity.setDeltaMovement(motion.x, pushY, motion.z);
            entity.hurtMarked = true; // 标记数据更新
        }
    }

    private static void handleClientParticles(Level level, BlockPos pos, RhythmPhase phase) {
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
                level.addParticle(ParticleTypes.CLOUD,
                        x + (level.random.nextGaussian() * 0.2),
                        y,
                        z + (level.random.nextGaussian() * 0.2),
                        0, 0.5 + (level.random.nextDouble() * 0.5), 0);
            }
        }
    }
}
