package yourscraft.jasdewstarfield.redshift_core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;

public class RedshiftDamageTypes {
    public static final ResourceKey<DamageType> INFRASOUND = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(RedshiftCore.MODID, "infrasound")
    );
}
