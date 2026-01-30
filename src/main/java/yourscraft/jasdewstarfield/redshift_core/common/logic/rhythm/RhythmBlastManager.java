package yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftDamageTypes;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftEffects;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = RedshiftCore.MODID)
public class RhythmBlastManager {

    // 记录每个维度上一次爆发的绝对时间 (GameTime)
    private static final Map<ResourceKey<Level>, Long> LAST_BLAST_TIMESTAMPS = new HashMap<>();

    // 缓存实体受到的暴露系数：EntityID -> ExposureFactor
    private static final Map<Integer, Float> EXPOSURE_CACHE = new HashMap<>();

    // 空间位置缓存 BlockPos -> Exposure
    private static final Map<BlockPos, Float> POS_CACHE = new HashMap<>();

    // 分摊计算的时间窗口长度 (Ticks)
    private static final int CALCULATION_WINDOW = 80;
    // 对于生物，使用更小的窗口，因为它们会频繁移动
    private static final int WINDOW_LIVING = 20;
    private static final int START_TICK_LIVING = RhythmManager.PHASE_BLAST_START - WINDOW_LIVING;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        // 1. 仅在服务端运行，且必须是 ServerLevel
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // 2. 获取当前维度的律动状态
        RhythmManager.RhythmState state = RhythmManager.getState(level);

        long cycleTime = state.cycleTime();
        long gameTime = level.getGameTime();

        //在平静期清空缓存，防止内存泄漏
        if (cycleTime == 0) {
            EXPOSURE_CACHE.clear();
            POS_CACHE.clear();
            return;
        }

        // [预计算阶段]
        // 利用这 80 ticks 分摊计算压力
        if (state.phase() == RhythmPhase.WARNING) {
            precalculateEntities(level, cycleTime);
        }

        // 触发震击
        if (cycleTime >= RhythmManager.PHASE_BLAST_START && cycleTime < RhythmManager.PHASE_BLAST_START + 10) {

            ResourceKey<Level> dimKey = level.dimension();
            long lastBlastTime = LAST_BLAST_TIMESTAMPS.getOrDefault(dimKey, -1L);

            // 2. 冷却锁：检查本轮周期是否已经触发过，防止跳刻
            // 只要上次触发的时间距离现在超过 20 ticks（肯定属于上一个周期），就说明是新的一轮
            if (gameTime - lastBlastTime > 20) {
                applyBlastToEntities(level);

                // 更新触发时间戳
                LAST_BLAST_TIMESTAMPS.put(dimKey, gameTime);
            }
        }
    }

    /**
     * 预计算逻辑：每 tick 只扫描 1/80 的实体
     */
    private static void precalculateEntities(ServerLevel level, long cycleTime) {
        long itemSlot = cycleTime - RhythmManager.PHASE_WARN_START;

        boolean isLivingWindow = cycleTime >= START_TICK_LIVING;
        long livingSlot = isLivingWindow ? (cycleTime - START_TICK_LIVING) : -1;

        Iterable<Entity> entities = level.getAllEntities();

        for (Entity entity : entities) {
            // 剔除不 tick 的实体
            if (!level.isPositionEntityTicking(entity.blockPosition())) {
                continue;
            }

            // 基础过滤，不保存玩家状态
            if (entity instanceof ServerPlayer) continue;
            if (!isValidTarget(entity)) continue;

            boolean shouldCalculate = false;

            // 生物 -> 短窗口 (20 ticks)
            if (entity instanceof LivingEntity) {
                if (isLivingWindow && entity.getId() % WINDOW_LIVING == livingSlot) {
                    shouldCalculate = true;
                }
            }
            // 物品/其他 -> 长窗口 (80 ticks)
            else {
                if (entity.getId() % CALCULATION_WINDOW == itemSlot) {
                    shouldCalculate = true;
                }
            }

            if (shouldCalculate) {
                float exposure;
                BlockPos pos = entity.blockPosition();

                if (POS_CACHE.containsKey(pos)) {
                    exposure = POS_CACHE.get(pos);
                } else {
                    // 没算过，进行射线/群系计算
                    exposure = RhythmExposureLogic.getExposureFactor(level, entity);
                    // 存入位置缓存
                    POS_CACHE.put(pos, exposure);
                }

                // 只有受影响的才存入缓存，节省内存
                if (exposure > 0.01f) {
                    EXPOSURE_CACHE.put(entity.getId(), exposure);
                }
            }
        }
    }

    private static void applyBlastToEntities(ServerLevel level) {
        // 获取所有加载的实体
        Iterable<Entity> entities = level.getAllEntities();

        for (Entity entity : entities) {
            // 实体过滤逻辑
            if (!level.isPositionEntityTicking(entity.blockPosition())) continue;
            if (!isValidTarget(entity)) {
                continue;
            }

            float exposure;

            // 1. 尝试从缓存获取，玩家仍然实时计算
            if (EXPOSURE_CACHE.containsKey(entity.getId())) {
                exposure = EXPOSURE_CACHE.get(entity.getId());
            } else {
                // 2. fallback
                exposure = RhythmExposureLogic.getExposureFactor(level, entity);
            }

            if (exposure > 0.05f) {
                pushEntity(level, entity, exposure);

                if (entity instanceof LivingEntity living) {
                    applyInternalInjury(level, living, exposure);
                }
            }
        }
    }

    private static boolean isValidTarget(Entity entity) {
        if (entity.isSpectator() || entity instanceof BlockAttachedEntity) return false;
        if (!entity.isPushable()) {
            return entity instanceof ItemEntity;
        }
        return true;
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

    /**
     * 处理内脏损伤与伤害计算
     */
    private static void applyInternalInjury(ServerLevel level, LivingEntity entity, float exposure) {
        // 1. 判定几率：几率 = 震动强度 (exposure)
        // exposure 0.0 ~ 1.0，直接作为 random 的阈值
        if (level.random.nextFloat() > exposure) {
            return; // 运气好，豁免了这次体内共振
        }

        // 2. 检查当前 Debuff 状态
        MobEffectInstance currentEffect = entity.getEffect(RedshiftEffects.INTERNAL_INJURY);

        int currentLevel = 0;
        boolean hasEffect = false;

        if (currentEffect != null) {
            currentLevel = currentEffect.getAmplifier();
            hasEffect = true;
        }

        // 3. 计算并施加伤害

        float baseDamage = 6.0f; // 基础伤害 3颗心

        if (hasEffect) {
            // 伤害随等级线性增长 (或者你可以用 Math.pow 做指数增长)
            // currentLevel + 1 代表显示等级 (Level 1 -> 1.0x, Level 2 -> 2.0x)
            float damageAmount = baseDamage * (currentLevel + 1) * exposure;

            // 使用 sonic_boom 伤害类型穿透护甲
            entity.hurt(level.damageSources().source(RedshiftDamageTypes.INFRASOUND), damageAmount);
        }

        // 4. 施加/升级 Debuff
        // 5 min
        int duration = 5 * 60 * 20;

        // 新等级：如果有效果，则等级+1；否则从 0 (Level I) 开始
        // 限制最大等级 5
        int nextLevel = hasEffect ? Math.min(currentLevel + 1, 4) : 0;

        entity.addEffect(new MobEffectInstance(
                RedshiftEffects.INTERNAL_INJURY,
                duration,
                nextLevel,
                false,
                false,
                true
        ));
    }
}
