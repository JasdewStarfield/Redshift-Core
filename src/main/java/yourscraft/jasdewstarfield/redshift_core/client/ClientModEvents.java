package yourscraft.jasdewstarfield.redshift_core.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.client.rhythm.PulseOverlay;
import yourscraft.jasdewstarfield.redshift_core.client.fog.FogOverlay;
import yourscraft.jasdewstarfield.redshift_core.common.geyser.GeyserSteamParticle;
import yourscraft.jasdewstarfield.redshift_core.registry.RedshiftParticles;

@EventBusSubscriber(modid = RedshiftCore.MODID)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(RedshiftCore.MODID, "toxic_fog_overlay"),
                new FogOverlay()
        );

        event.registerAbove(
                ResourceLocation.fromNamespaceAndPath(RedshiftCore.MODID, "toxic_fog_overlay"),
                ResourceLocation.fromNamespaceAndPath(RedshiftCore.MODID, "basalt_organ_flash_overlay"),
                new PulseOverlay()
        );
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                RedshiftParticles.GEYSER_STEAM.get(),
                GeyserSteamParticle.Provider::new
        );
    }
}
