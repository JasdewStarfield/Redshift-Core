package yourscraft.jasdewstarfield.redshift_core;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftSounds;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftFeatures;

@Mod(RedshiftCore.MODID)
public class RedshiftCore {

    public static final String MODID = "redshift";
    private static final Logger LOGGER = LogUtils.getLogger();

    public RedshiftCore(IEventBus modEventBus, ModContainer modContainer) {
        RedshiftSounds.register(modEventBus);
        RedshiftFeatures.register(modEventBus);
        LOGGER.info("[Redshift Core] Mod Registered");
    }
}
