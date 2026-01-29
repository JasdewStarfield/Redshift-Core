package yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.common.util.BiomeSamplerUtil;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = RedshiftCore.MODID)
public class RhythmBlastManager {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        // 1. 仅在服务端运行，且必须是 ServerLevel
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // 2. 获取当前维度的律动状态
        RhythmManager.RhythmState state = RhythmManager.getState(level);

        // 3. 仅在爆发开始的那一刻触发 (Tick 540)
        // 这样模拟的是一次瞬间的“震波”冲击
        if (state.cycleTime() == RhythmManager.PHASE_BLAST_START) {
            applyBlastToEntities(level);
        }
    }

    private static void applyBlastToEntities(ServerLevel level) {
        // 获取所有加载的实体 (在 LevelTickEvent 中遍历是安全的)
        Iterable<Entity> entities = level.getAllEntities();

        Map<ChunkPos, Float> chunkStrengthCache = new HashMap<>();

        for (Entity entity : entities) {
            // 4. 实体过滤逻辑
            boolean isItem = entity instanceof ItemEntity;
            if (entity.isSpectator() || entity instanceof BlockAttachedEntity || (!entity.isPushable() && !isItem)) {
                continue;
            }

            // 5. 计算群系距离权重 (范围 32)
            // 只有靠近玄武岩管风琴的实体才会受到冲击
            float weight;
            if (entity instanceof ServerPlayer) {
                weight = BiomeSamplerUtil.sampleBiomeWeight(
                        level,
                        entity.position(),
                        32, 16,
                        holder -> holder.is(RhythmManager.BASALT_ORGAN)
                );
            } else {
                ChunkPos chunkPos = entity.chunkPosition();
                weight = chunkStrengthCache.computeIfAbsent(chunkPos, pos -> {
                    Vec3 centerOfChunk = pos.getWorldPosition().getCenter();

                    return BiomeSamplerUtil.sampleBiomeWeight(
                            level,
                            centerOfChunk,
                            24, 16,
                            holder -> holder.is(RhythmManager.BASALT_ORGAN)
                    );
                });
            }

            // 如果受到影响 (权重 > 0)
            if (weight > 0.01f) {
                pushEntity(level, entity, weight);
            }
        }
    }

    private static void pushEntity(ServerLevel level, Entity entity, float strength) {
        // 随机生成一个水平方向 (X, Z)
        // random.nextDouble() -> 0.0 ~ 1.0 -> (减0.5 * 2) -> -1.0 ~ 1.0
        double randX = (level.random.nextDouble() - 0.5) * 2.0;
        double randZ = (level.random.nextDouble() - 0.5) * 2.0;

        // 水平力度
        Vec3 horizontal = new Vec3(randX, 0, randZ).normalize().scale(0.3 * strength);

        // 垂直力度
        double impulseY;

        if (entity instanceof ItemEntity) {
            impulseY = 0.1 + (0.2 * strength);
        } else {
            impulseY = 0.2 + (0.3 * strength);
        }

        // 获取当前运动并在其上叠加
        Vec3 currentMotion = entity.getDeltaMovement();
        entity.setDeltaMovement(currentMotion.add(horizontal.x, impulseY, horizontal.z));
        entity.hurtMarked = true;
    }
}
