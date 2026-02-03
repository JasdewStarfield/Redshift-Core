package yourscraft.jasdewstarfield.redshift_core;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import yourscraft.jasdewstarfield.redshift_core.registry.*;

@Mod(RedshiftCore.MODID)
public class RedshiftCore {

    public static final String MODID = "redshift";
    private static final Logger LOGGER = LogUtils.getLogger();

    public RedshiftCore(IEventBus modEventBus, ModContainer modContainer) {
        RedshiftEffects.register(modEventBus);
        RedshiftSounds.register(modEventBus);
        RedshiftParticles.register(modEventBus);
        RedshiftFeatures.register(modEventBus);
        RedshiftBlocks.register(modEventBus);
        RedshiftBlockEntities.register(modEventBus);
        RedshiftItems.register(modEventBus);
        LOGGER.info("[Redshift Core] Mod Registered");
    }
}
