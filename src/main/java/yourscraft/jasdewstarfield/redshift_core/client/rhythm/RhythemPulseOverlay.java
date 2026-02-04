package yourscraft.jasdewstarfield.redshift_core.client.rhythm;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.redshift_core.RedshiftCore;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmExposureLogic;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmManager;
import yourscraft.jasdewstarfield.redshift_core.common.logic.rhythm.RhythmPhase;

public class RhythemPulseOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation VIGNETTE_LOCATION = ResourceLocation.fromNamespaceAndPath(RedshiftCore.MODID, "textures/misc/pulse_overlay.png");

    private float smoothedIntensity = 0.0f;
    private float cachedExposure = 0.0f;
    private long lastUpdateTick = 0;

    private static final int UPDATE_INTERVAL = 10;

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        Level level = player.level();

        // 1. 获取全局律动状态
        RhythmManager.RhythmState state = RhythmManager.getState(level);

        float rawRhythm = state.intensity();

        // 2. 如果没有任何律动（平静期），直接跳过采样，节省性能
        if (rawRhythm <= 0.001f && smoothedIntensity <= 0.001f) {
            smoothedIntensity = 0.0f;
            return;
        }

        long currentTick = level.getGameTime();
        // 只有当时间流逝超过间隔时才更新
        if (Math.abs(currentTick - lastUpdateTick) >= UPDATE_INTERVAL) {
            // 调用重型计算逻辑
            this.cachedExposure = RhythmExposureLogic.getExposureFactor(level, player);
            this.lastUpdateTick = currentTick;
        }

        float targetAlpha = state.intensity() * this.cachedExposure * 0.8f;

        // 非对称平滑过渡
        float dt = deltaTracker.getGameTimeDeltaPartialTick(true);

        if (targetAlpha > this.smoothedIntensity) {
            // [变亮阶段 - Attack]
            float attackSpeed;
            if (rawRhythm >= 0.75f) {
                attackSpeed = 0.9f;
            } else {
                attackSpeed = 0.3f;
            }
            this.smoothedIntensity = Mth.lerp(attackSpeed * dt, this.smoothedIntensity, targetAlpha);
        } else {
            // [变暗阶段 - Decay]
            float decaySpeed = 0.05f;
            this.smoothedIntensity = Mth.lerp(decaySpeed * dt, this.smoothedIntensity, targetAlpha);
        }

        float finalAlpha = this.smoothedIntensity;

        boolean isWarningPhase = (state.phase() == RhythmPhase.WARNING);

        if (isWarningPhase && state.intensity() > 0.1f) {
            float time = level.getGameTime() + dt;

            // 频率: 0.4 (约每秒 1.5 次呼吸，比较急促)
            // 幅度: 0.1 (在基础亮度上浮动)
            float pulse = (Mth.sin(time * 0.4f) + 1.0f) * 0.5f * 0.05f;

            // 脉动强度也受群系权重影响
            finalAlpha += pulse * state.intensity();
        }

        if (finalAlpha > 0.01f) {
            renderWhiteVignette(guiGraphics, Math.min(finalAlpha, 1.0f));
        }
    }

    private void renderWhiteVignette(GuiGraphics guiGraphics, float alpha) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha);
        // 绘制纯白遮罩
        guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0f, 0.0f, width, height, width, height);
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
