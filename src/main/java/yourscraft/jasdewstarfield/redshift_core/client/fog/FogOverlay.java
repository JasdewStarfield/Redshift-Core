package yourscraft.jasdewstarfield.redshift_core.client.fog;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.redshift_core.common.logic.fog.FogLogic;


public class FogOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation NAUSEA_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/nausea.png");

    private float smoothedDensity = 0.0f;

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        // 安全检查：如果玩家不存在，直接跳过
        if (player == null) {
            return;
        }

        float targetDensity = FogLogic.getFogIntensity(player.level(), player.position());
        this.smoothedDensity = Mth.lerp(0.1f, this.smoothedDensity, targetDensity);

        if (this.smoothedDensity < 0.2f) return;

        float scalar = (this.smoothedDensity - 0.2f) / 0.6f;
        scalar = Mth.clamp(scalar, 0.0f, 1.0f);

        renderToxicOverlay(guiGraphics, scalar);
    }

    private void renderToxicOverlay(GuiGraphics guiGraphics, float scalar) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        guiGraphics.pose().pushPose();

        float scale = Mth.lerp(scalar, 1.0F, 1.2F);

        guiGraphics.pose().translate(width / 2.0F, height / 2.0F, 0.0F);
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.pose().translate(-width / 2.0F, -height / 2.0F, 0.0F);

        float r = 0.2F * scalar;
        float g = 0.5F * scalar;
        float b = 0.1F * scalar;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        guiGraphics.setColor(r, g, b, 1.0F);
        guiGraphics.blit(NAUSEA_LOCATION, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);

        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        guiGraphics.pose().popPose();
    }
}
