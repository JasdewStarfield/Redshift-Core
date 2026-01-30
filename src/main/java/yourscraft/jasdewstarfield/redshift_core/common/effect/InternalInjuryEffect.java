package yourscraft.jasdewstarfield.redshift_core.common.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class InternalInjuryEffect extends MobEffect {
    public InternalInjuryEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity pLivingEntity, int pAmplifier) {
        return super.applyEffectTick(pLivingEntity, pAmplifier);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int pDuration, int pAmplifier) {
        return false; // 不需要每 tick 执行
    }
}