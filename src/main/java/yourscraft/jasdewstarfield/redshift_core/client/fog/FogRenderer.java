package yourscraft.jasdewstarfield.redshift_core.client.fog;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import yourscraft.jasdewstarfield.redshift_core.client.RedshiftRenderTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(value = Dist.CLIENT)
public class FogRenderer {

    private static final ResourceLocation FOG_TEXTURE = ResourceLocation.fromNamespaceAndPath("redshift",
            "textures/environment/fog_cloud.png");

    private static final FogGenerator generator = new FogGenerator();

    private static final ByteBufferBuilder BUFFER_BUILDER = new ByteBufferBuilder(2048);

    private static final int MIN_BRIGHTNESS = 4;

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

        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(BUFFER_BUILDER);
        VertexConsumer buffer = bufferSource.getBuffer(RedshiftRenderTypes.getFog(FOG_TEXTURE));

        double smoothTime = gameTime + partialTick;
        // 纹理流动时间
        float flowTime = (float) (smoothTime * FogConfig.ANIMATION_SPEED);
        // 垂直呼吸时间
        float breathingTime = (float) (smoothTime * FogConfig.BREATHING_SPEED);

        double maxDist = (FogConfig.RENDER_DISTANCE_CHUNKS - 1) * 16.0;

        float immersion = FogEventHandler.getCurrentFogWeight();

        // 区块排序
        List<Map.Entry<Long, FogGenerator.FogChunkData>> sortedChunks = new ArrayList<>(generator.getVisibleChunks().entrySet());
        sortedChunks.sort((e1, e2) -> {
            Vec3 center1 = getChunkCenter(e1.getKey());
            Vec3 center2 = getChunkCenter(e2.getKey());
            double d1 = center1.distanceToSqr(cameraPos);
            double d2 = center2.distanceToSqr(cameraPos);
            return Double.compare(d2, d1); // d2 > d1 则返回正数，d2 排前面
        });

        // 3. 遍历所有 Chunk 并绘制
        for (Map.Entry<Long, FogGenerator.FogChunkData> entry : sortedChunks) {
            long chunkPos = entry.getKey();
            FogGenerator.FogChunkData data = entry.getValue();
            int chunkX = (int) (chunkPos & 0xFFFFFFFFL);
            int chunkZ = (int) (chunkPos >>> 32);
            double worldChunkX = chunkX * 16;
            double worldChunkZ = chunkZ * 16;

            float currentSize = data.voxelSize();

            // 区块内点排序
            List<FogGenerator.FogPoint> sortedPoints = new ArrayList<>(data.points());
            sortedPoints.sort((p1, p2) -> {
                double distSq1 = getDistSq(worldChunkX, worldChunkZ, p1, cameraPos);
                double distSq2 = getDistSq(worldChunkX, worldChunkZ, p2, cameraPos);
                return Double.compare(distSq2, distSq1); // 降序
            });

            for (FogGenerator.FogPoint point : sortedPoints) {
                double wx = worldChunkX + point.x();
                double wz = worldChunkZ + point.z();

                BlockPos checkPos = BlockPos.containing(wx + currentSize / 2.0, FogConfig.FOG_HEIGHT, wz + currentSize / 2.0);
                BlockState state = mc.level.getBlockState(checkPos);

                if (state.isSuffocating(mc.level, checkPos)) {
                    continue;
                }

                // 光照计算
                int blockLight = mc.level.getBrightness(LightLayer.BLOCK, checkPos);
                int skyLight = mc.level.getBrightness(LightLayer.SKY, checkPos);
                blockLight = Math.max(blockLight, MIN_BRIGHTNESS);

                int packedLight = LightTexture.pack(blockLight, skyLight);

                // 垂直呼吸
                float localBreathing = getComplexBreathing(wx, wz, breathingTime);
                double driftedY = FogConfig.FOG_HEIGHT + point.yOffset() + localBreathing;

                // 水平漂移
                Vec2 drift = getDrift(wx, wz, (float)smoothTime);
                double driftedX = wx + drift.x;
                double driftedZ = wz + drift.y;

                double distSq = (driftedX - cameraPos.x) * (driftedX - cameraPos.x) +
                        (driftedY - cameraPos.y) * (driftedY - cameraPos.y) +
                        (driftedZ - cameraPos.z) * (driftedZ - cameraPos.z);

                if (distSq > maxDist * maxDist) continue;
                double dist = Math.sqrt(distSq);

                // 距离淡出
                float fade = computeFade((float)wx, (float)wz, (float)cameraPos.x, (float)cameraPos.z, maxDist);
                if (fade <= 0.01f) continue;

                float proximityFade = 1.0f;
                if (immersion > 0.05f) {
                    proximityFade = (float) Mth.clamp((dist - 5.0) / 7.0, 0.0, 1.0);
                    proximityFade = Mth.lerp(immersion, 1.0f, proximityFade);
                }

                // 最终透明度
                float finalAlpha = point.alpha() * fade * proximityFade;

                drawFogCube(poseStack, buffer, driftedX, (float) driftedY, driftedZ, finalAlpha, flowTime, currentSize, packedLight);
            }
        }

        // 提交绘制
        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static void drawFogCube(PoseStack poseStack, VertexConsumer buffer, double x, float topY, double z, float alpha, float time, float size, int packedLight) {
        float bottomY = topY - 2.0f;

        float eps = 0.05f;
        float minX = (float)x + eps;
        float maxX = (float)x + size - eps;
        float minZ = (float)z + eps;
        float maxZ = (float)z + size - eps;

        // 基础颜色
        float r = 0.65f;
        float g = 0.75f;
        float b = 0.45f;

        Matrix4f mat = poseStack.last().pose();

        // 纹理缩放 (控制纹理在方块上的疏密)
        float texScale = 32.0f;

        // UV 计算 (基于世界坐标 + 时间流动)
        float u1 = (float) (x / texScale + time);
        float u2 = (float) ((x + size) / texScale + time);
        float v1 = (float) (z / texScale + time * 0.5f);
        float v2 = (float) ((z + size) / texScale + time * 0.5f);

        // --- 1. 顶面 (Top Face) ---
        buffer.addVertex(mat, minX, topY, minZ).setColor(r, g, b, alpha).setUv(u1, v1).setLight(packedLight);
        buffer.addVertex(mat, minX, topY, maxZ).setColor(r, g, b, alpha).setUv(u1, v2).setLight(packedLight);
        buffer.addVertex(mat, maxX, topY, maxZ).setColor(r, g, b, alpha).setUv(u2, v2).setLight(packedLight);
        buffer.addVertex(mat, maxX, topY, minZ).setColor(r, g, b, alpha).setUv(u2, v1).setLight(packedLight);

        // 侧面的 Alpha 可以稍微低一点，模拟气体边缘的稀薄感
        float sideAlphaTop = alpha * 0.6f;
        float sideAlphaBottom = 0.0f;

        // 北面 (Z-)
        buffer.addVertex(mat, maxX, topY, minZ).setColor(r, g, b, sideAlphaTop).setUv(u2, v1).setLight(packedLight);
        buffer.addVertex(mat, maxX, bottomY, minZ).setColor(r, g, b, sideAlphaBottom).setUv(u2, v2).setLight(packedLight);
        buffer.addVertex(mat, minX, bottomY, minZ).setColor(r, g, b, sideAlphaBottom).setUv(u1, v2).setLight(packedLight);
        buffer.addVertex(mat, minX, topY, minZ).setColor(r, g, b, sideAlphaTop).setUv(u1, v1).setLight(packedLight);

        // 南面 (Z+)
        buffer.addVertex(mat, minX, topY, maxZ).setColor(r, g, b, sideAlphaTop).setUv(u1, v1).setLight(packedLight);
        buffer.addVertex(mat, minX, bottomY, maxZ).setColor(r, g, b, sideAlphaBottom).setUv(u1, v2).setLight(packedLight);
        buffer.addVertex(mat, maxX, bottomY, maxZ).setColor(r, g, b, sideAlphaBottom).setUv(u2, v2).setLight(packedLight);
        buffer.addVertex(mat, maxX, topY, maxZ).setColor(r, g, b, sideAlphaTop).setUv(u2, v1).setLight(packedLight);

        // 西面 (X-)
        buffer.addVertex(mat, minX, topY, minZ).setColor(r, g, b, sideAlphaTop).setUv(u1, v1).setLight(packedLight);
        buffer.addVertex(mat, minX, bottomY, minZ).setColor(r, g, b, sideAlphaBottom).setUv(u1, v2).setLight(packedLight);
        buffer.addVertex(mat, minX, bottomY, maxZ).setColor(r, g, b, sideAlphaBottom).setUv(u2, v2).setLight(packedLight);
        buffer.addVertex(mat, minX, topY, maxZ).setColor(r, g, b, sideAlphaTop).setUv(u2, v1).setLight(packedLight);

        // 东面 (X+)
        buffer.addVertex(mat, maxX, topY, maxZ).setColor(r, g, b, sideAlphaTop).setUv(u2, v1).setLight(packedLight);
        buffer.addVertex(mat, maxX, bottomY, maxZ).setColor(r, g, b, sideAlphaBottom).setUv(u2, v2).setLight(packedLight);
        buffer.addVertex(mat, maxX, bottomY, minZ).setColor(r, g, b, sideAlphaBottom).setUv(u1, v2).setLight(packedLight);
        buffer.addVertex(mat, maxX, topY, minZ).setColor(r, g, b, sideAlphaTop).setUv(u1, v1).setLight(packedLight);
    }

    // 辅助方法：计算区块中心坐标
    private static Vec3 getChunkCenter(long chunkPos) {
        int chunkX = (int) (chunkPos & 0xFFFFFFFFL);
        int chunkZ = (int) (chunkPos >>> 32);
        return new Vec3(chunkX * 16 + 8, 60, chunkZ * 16 + 8);
    }

    // 辅助方法：计算点到摄像机的距离平方
    private static double getDistSq(double worldChunkX, double worldChunkZ, FogGenerator.FogPoint p, Vec3 cam) {
        double wx = worldChunkX + p.x();
        double wz = worldChunkZ + p.z();
        double wy = FogConfig.FOG_HEIGHT + p.yOffset();
        return (wx - cam.x) * (wx - cam.x) + (wy - cam.y) * (wy - cam.y) + (wz - cam.z) * (wz - cam.z);
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

    private static Vec2 getDrift(double x, double z, float time) {
        float dx = (float) (Math.sin(time * 0.01 + x * 0.3 + z * 0.1) * 0.35);
        float dz = (float) (Math.cos(time * 0.008 + x * 0.1 - z * 0.3) * 0.35);
        return new Vec2(dx, dz);
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
