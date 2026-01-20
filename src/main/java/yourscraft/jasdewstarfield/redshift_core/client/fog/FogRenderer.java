package yourscraft.jasdewstarfield.redshift_core.client.fog;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;

@EventBusSubscriber(value = Dist.CLIENT)
public class FogRenderer {

    private static final ResourceLocation FOG_TEXTURE = ResourceLocation.fromNamespaceAndPath("redshift",
            "textures/environment/fog_cloud.png");

    private static final FogGenerator generator = new FogGenerator();

    private static final float MIN_BRIGHTNESS = 0.25f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 只在半透明方块渲染后绘制，保证水体混合正常
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        long gameTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        // 1. 更新生成器
        generator.update(cameraPos, mc.level.getGameTime());

        // 2. 准备渲染状态
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // 抵消摄像机移动，因为我们要在绝对世界坐标渲染
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FOG_TEXTURE);
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // 光照计算
        float worldBrightness = calculateWorldBrightness(mc, partialTick);

        double smoothTime = gameTime + partialTick;
        // 纹理流动时间
        float flowTime = (float) (smoothTime * FogConfig.ANIMATION_SPEED);
        // 垂直呼吸时间
        float breathingTime = (float) (smoothTime * FogConfig.BREATHING_SPEED);

        double maxDist = (FogConfig.RENDER_DISTANCE_CHUNKS - 1) * 16.0;

        float immersion = FogEventHandler.getCurrentFogWeight();

        // 3. 遍历所有 Chunk 并绘制
        for (Map.Entry<Long, FogGenerator.FogChunkData> entry : generator.getVisibleChunks().entrySet()) {
            long chunkPos = entry.getKey();
            FogGenerator.FogChunkData data = entry.getValue();
            int chunkX = (int) (chunkPos & 0xFFFFFFFFL);
            int chunkZ = (int) (chunkPos >>> 32);
            double worldChunkX = chunkX * 16;
            double worldChunkZ = chunkZ * 16;

            float currentSize = data.voxelSize();

            for (FogGenerator.FogPoint point : data.points()) {
                double wx = worldChunkX + point.x();
                double wz = worldChunkZ + point.z();

                double distSq = (wx - cameraPos.x) * (wx - cameraPos.x) + (wz - cameraPos.z) * (wz - cameraPos.z);
                double dist = Math.sqrt(distSq);

                // 距离淡出
                float fade = computeFade((float)wx, (float)wz, (float)cameraPos.x, (float)cameraPos.z, maxDist);
                if (fade <= 0.01f) continue;

                float proximityFade = 1.0f;
                if (immersion > 0.05f) {
                    proximityFade = (float) Mth.clamp((dist - 5.0) / 7.0, 0.0, 1.0);
                    proximityFade = Mth.lerp(immersion, 1.0f, proximityFade);
                }

                // 垂直动画：给每个方块加一个基于位置的相位，让它们不是同步升降，而是波浪式升降
                float localBreathing = getComplexBreathing(wx, wz, breathingTime);

                float finalAlpha = point.alpha() * fade * proximityFade;

                drawFogCube(poseStack, buffer, wx, point.yOffset() + localBreathing, wz, finalAlpha, flowTime, currentSize, worldBrightness);
            }
        }

        // 提交绘制
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static float getComplexBreathing(double x, double z, float time) {
        // 1. 主波 (Primary Wave): 较慢，波长中等
        float wave1 = Mth.sin(time + (float)x * 0.2f + (float)z * 0.2f);

        // 2. 次波 (Secondary Wave): 较快，方向不同，用于打破主波的规律性
        float wave2 = Mth.cos(time * 1.3f + (float)x * 0.5f - (float)z * 0.3f);

        // 3. 随机相位 (Random Phase): 基于坐标的伪随机数
        float randomPhase = (float) Math.sin(x * 12.989 + z * 78.233);

        // 组合：主波 + 次波的一半 + 随机扰动
        // 结果乘以幅度系数
        return (wave1 + wave2 * 0.5f + randomPhase * 0.2f) * FogConfig.BREATHING_AMPLITUDE;
    }

    private static float calculateWorldBrightness(Minecraft mc, float partialTick) {
        if (mc.level == null) return 1.0f;

        // 1. 获取太阳角度
        float sunAngle = mc.level.getSunAngle(partialTick);
        // cos(0) = 1 (Day), cos(pi) = -1 (Night). 映射到 0~1
        float dayLight = Mth.cos(sunAngle) * 0.5f + 0.5f;
        dayLight = Mth.clamp(dayLight, 0.0f, 1.0f);

        // 2. 考虑天气
        float rain = mc.level.getRainLevel(partialTick);
        float thunder = mc.level.getThunderLevel(partialTick);

        // 雨天最多降低 20% 亮度，雷暴最多降低 50%
        dayLight *= (1.0f - rain * 0.2f);
        dayLight *= (1.0f - thunder * 0.5f);

        // 3. 混合最小亮度 (保留夜间荧光感)
        return MIN_BRIGHTNESS + (1.0f - MIN_BRIGHTNESS) * dayLight;
    }

    private static void drawFogCube(PoseStack poseStack, BufferBuilder buffer, double x, float yOffset, double z, float alpha, float time, float size, float brightness) {
        // 雾气顶部高度
        float topY = FogConfig.FOG_HEIGHT + yOffset;
        // 雾气底部高度 (固定在海平面以下一点，或者根据 topY 向下延伸一个固定厚度)
        float bottomY = FogConfig.FOG_HEIGHT - 2.0f;

        float eps = 0.01f;
        float minX = (float)x + eps;
        float maxX = (float)x + size - eps;
        float minZ = (float)z + eps;
        float maxZ = (float)z + size - eps;

        // 基础颜色
        float baseR = 0.65f;
        float baseG = 0.75f;
        float baseB = 0.45f;

        // 应用亮度乘数
        float r = baseR * brightness;
        float g = baseG * brightness;
        float b = baseB * brightness;

        Matrix4f mat = poseStack.last().pose();

        // 纹理缩放 (控制纹理在方块上的疏密)
        float texScale = 32.0f;

        // UV 计算 (基于世界坐标 + 时间流动)
        float u1 = (float) (x / texScale + time);
        float u2 = (float) ((x + size) / texScale + time);
        float v1 = (float) (z / texScale + time * 0.5f);
        float v2 = (float) ((z + size) / texScale + time * 0.5f);

        // --- 1. 顶面 (Top Face) ---
        buffer.addVertex(mat, (float)x, topY, (float)z).setUv(u1, v1).setColor(r, g, b, alpha);
        buffer.addVertex(mat, (float)x, topY, (float)z + size).setUv(u1, v2).setColor(r, g, b, alpha);
        buffer.addVertex(mat, (float)x + size, topY, (float)z + size).setUv(u2, v2).setColor(r, g, b, alpha);
        buffer.addVertex(mat, (float)x + size, topY, (float)z).setUv(u2, v1).setColor(r, g, b, alpha);

        // 侧面的 Alpha 可以稍微低一点，模拟气体边缘的稀薄感
        float sideAlphaTop = alpha * 0.6f;
        float sideAlphaBottom = 0.0f;

        // 北面 (Z-)
        buffer.addVertex(mat, maxX, topY, minZ).setUv(u2, v1).setColor(r, g, b, sideAlphaTop);
        buffer.addVertex(mat, maxX, bottomY, minZ).setUv(u2, v2).setColor(r, g, b, sideAlphaBottom);
        buffer.addVertex(mat, minX, bottomY, minZ).setUv(u1, v2).setColor(r, g, b, sideAlphaBottom);
        buffer.addVertex(mat, minX, topY, minZ).setUv(u1, v1).setColor(r, g, b, sideAlphaTop);

        // 南面 (Z+)
        buffer.addVertex(mat, minX, topY, maxZ).setUv(u1, v1).setColor(r, g, b, sideAlphaTop);
        buffer.addVertex(mat, minX, bottomY, maxZ).setUv(u1, v2).setColor(r, g, b, sideAlphaBottom);
        buffer.addVertex(mat, maxX, bottomY, maxZ).setUv(u2, v2).setColor(r, g, b, sideAlphaBottom);
        buffer.addVertex(mat, maxX, topY, maxZ).setUv(u2, v1).setColor(r, g, b, sideAlphaTop);

        // 西面 (X-)
        buffer.addVertex(mat, minX, topY, minZ).setUv(u1, v1).setColor(r, g, b, sideAlphaTop);
        buffer.addVertex(mat, minX, bottomY, minZ).setUv(u1, v2).setColor(r, g, b, sideAlphaBottom);
        buffer.addVertex(mat, minX, bottomY, maxZ).setUv(u2, v2).setColor(r, g, b, sideAlphaBottom);
        buffer.addVertex(mat, minX, topY, maxZ).setUv(u2, v1).setColor(r, g, b, sideAlphaTop);

        // 东面 (X+)
        buffer.addVertex(mat, maxX, topY, maxZ).setUv(u2, v1).setColor(r, g, b, sideAlphaTop);
        buffer.addVertex(mat, maxX, bottomY, maxZ).setUv(u2, v2).setColor(r, g, b, sideAlphaBottom);
        buffer.addVertex(mat, maxX, bottomY, minZ).setUv(u1, v2).setColor(r, g, b, sideAlphaBottom);
        buffer.addVertex(mat, maxX, topY, minZ).setUv(u1, v1).setColor(r, g, b, sideAlphaTop);
    }

    private static float computeFade(float x, float z, float px, float pz, double maxDist) {
        double distSq = (x - px) * (x - px) + (z - pz) * (z - pz);
        double dist = Math.sqrt(distSq);
        if (dist > maxDist) return 0.0f;
        double fadeStart = maxDist - 32.0;
        if (dist < fadeStart) return 1.0f;
        return (float) ((maxDist - dist) / 32.0);
    }
}
