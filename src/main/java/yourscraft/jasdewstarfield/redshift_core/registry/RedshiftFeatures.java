package yourscraft.jasdewstarfield.redshift_core.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.common.worldgen.HexagonalBasaltFeature;

public class RedshiftFeatures {

    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, RedshiftCore.MODID);

    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> BASALT_ORGAN_FEATURE =
            FEATURES.register("basalt_organ_columns", () -> new HexagonalBasaltFeature(NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
