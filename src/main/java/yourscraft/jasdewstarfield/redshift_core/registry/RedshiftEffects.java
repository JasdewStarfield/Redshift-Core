package yourscraft.jasdewstarfield.redshift_core.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.common.effect.InternalInjuryEffect;

public class RedshiftEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, RedshiftCore.MODID);

    // 注册内脏损伤效果
    public static final DeferredHolder<MobEffect, InternalInjuryEffect> INTERNAL_INJURY =
            MOB_EFFECTS.register("internal_injury", InternalInjuryEffect::new);

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }

    // 辅助方法：获取 ResourceKey
    public static ResourceKey<MobEffect> getKey(DeferredHolder<MobEffect, ? extends MobEffect> holder) {
        return ResourceKey.create(Registries.MOB_EFFECT, holder.getId());
    }
}
