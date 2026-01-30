package yourscraft.jasdewstarfield.redshift_core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;

public class RedshiftTags {

    public static class Blocks {
        public static final TagKey<Block> RESONANT_BLOCKS = tag("resonant_blocks");

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(RedshiftCore.MODID, name));
        }
    }
}
