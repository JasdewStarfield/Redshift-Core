package yourscraft.jasdewstarfield.redshift_core.registry;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.common.geyser.GeyserBlock;

public class RedshiftBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RedshiftCore.MODID);

    // 注册间歇泉方块
    public static final DeferredBlock<GeyserBlock> GEYSER = BLOCKS.register("basalt_geyser",
            () -> new GeyserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.25f, 4.2f)
                    .requiresCorrectToolForDrops()
            ));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
