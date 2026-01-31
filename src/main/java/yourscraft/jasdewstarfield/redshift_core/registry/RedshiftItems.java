package yourscraft.jasdewstarfield.redshift_core.registry;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.common.geyser.GeyserBlockItem;

public class RedshiftItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RedshiftCore.MODID);

    public static final DeferredItem<Item> GEYSER = ITEMS.register("basalt_geyser",
            () -> new GeyserBlockItem(RedshiftBlocks.GEYSER.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
