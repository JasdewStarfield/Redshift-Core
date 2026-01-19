package yourscraft.jasdewstarfield.redshift_core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RedshiftSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, "redshift");

    // 注册声音事件通用方法
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("redshift", name)));
    }

    // --- 1. 气溶胶红树林 ---
    public static final DeferredHolder<SoundEvent, SoundEvent> MANGROVE_LOOP = register("ambient.aerosol_mangroves.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANGROVE_ADDITIONS = register("ambient.aerosol_mangroves.additions");

    // --- 2. 玄武岩管风琴 ---
    public static final DeferredHolder<SoundEvent, SoundEvent> BASALT_LOOP = register("ambient.basalt_organ.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> BASALT_ADDITIONS = register("ambient.basalt_organ.additions");

    // --- 3. 废土 ---
    public static final DeferredHolder<SoundEvent, SoundEvent> WASTELAND_LOOP = register("ambient.wasteland.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> WASTELAND_ADDITIONS = register("ambient.wasteland.additions");

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
