package yourscraft.jasdewstarfield.redshift_core.client;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;

@EventBusSubscriber(modid = RedshiftCore.MODID, value = Dist.CLIENT)
public class FogEventHandler {

    public static final ResourceKey<Biome> AEROSOL_MANGROVES = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("redshift", "aerosol_mangroves"));

    private static float currentFogWeight = 0.0f;
    private static long lastTime = 0;

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Entity entity = event.getCamera().getEntity();
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        Holder<Biome> biomeHolder = level.getBiome(pos);

        boolean isBiome = biomeHolder.is(AEROSOL_MANGROVES);
        boolean isInFog = isBiome && entity.getY() <= 63;
        float targetWeight = isInFog ? 1.0f : 0.0f;

        long currentTime = System.currentTimeMillis();
        if (lastTime == 0) {
            lastTime = currentTime;
            // Instant load if spawning in biome
            if (isBiome) {
                currentFogWeight = 1.0f;
            }
        }
        float dt = (currentTime - lastTime) / 1000.0f;
        lastTime = currentTime;

        float speedMod = 1.0f - ((float) Math.pow(currentFogWeight, 2) * 0.8f);
        float transitionSpeed = 0.5f * dt * speedMod;

        if (currentFogWeight < targetWeight) {
            currentFogWeight = Math.min(currentFogWeight + transitionSpeed, targetWeight);
        } else if (currentFogWeight > targetWeight) {
            currentFogWeight = Math.max(currentFogWeight - transitionSpeed, targetWeight);
        }

        if (currentFogWeight > 0.0f) {
            long time = level.getGameTime();
            float breathing = (float) Math.sin(time * 0.05) * 2.0f;

            float vanillaNear = event.getNearPlaneDistance();
            float vanillaFar = event.getFarPlaneDistance();
            float customNear = 0.0f;
            float customFar = 24.0f + breathing;

            event.setNearPlaneDistance(vanillaNear + (customNear - vanillaNear) * currentFogWeight);
            event.setFarPlaneDistance(vanillaFar + (customFar - vanillaFar) * currentFogWeight);
            event.setCanceled(true);
        }
    }
}
