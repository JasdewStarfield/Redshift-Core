package yourscraft.jasdewstarfield.redshift_core.client.fog;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class FogGenerator {

    private final FogSampler sampler;
    private final Minecraft minecraft;

    // 缓存：Chunk坐标 -> 该Chunk内的雾气点列表
    // 使用 ConcurrentHashMap 保证多线程安全
    private final ConcurrentHashMap<Long, FogChunkData> chunkCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> processingChunks = new ConcurrentHashMap<>();

    // 简单的记录类，存储雾气点相对于 Chunk 的位置和透明度
    public record FogPoint(float x, float yOffset, float z, float alpha, boolean isEdge) {}
    public record FogChunkData(List<FogPoint> points, int voxelSize) {}

    public FogGenerator() {
        this.minecraft = Minecraft.getInstance();
        this.sampler = new FogSampler(1234L);
    }

    /**
     * 每帧调用，清理旧数据并触发新生成
     */
    public void update(Vec3 cameraPos, long gameTime) {
        int centerChunkX = (int) cameraPos.x >> 4;
        int centerChunkZ = (int) cameraPos.z >> 4;
        int radius = FogConfig.RENDER_DISTANCE_CHUNKS;

        // 1. 清理太远的缓存
        chunkCache.keySet().removeIf(chunkPosLong -> {
            int cx = (int) (chunkPosLong & 0xFFFFFFFFL);
            int cz = (int) (chunkPosLong >>> 32);
            return Math.abs(cx - centerChunkX) > radius + 2 || Math.abs(cz - centerChunkZ) > radius + 2;
        });

        // 2. 检查并生成范围内的 Chunk
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                long posLong = (((long) cz) << 32) | (cx & 0xFFFFFFFFL);

                // 计算该区块应有的 LOD 大小
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                int targetSize = FogConfig.getLODVoxelSize(dist);

                // 检查是否需要生成或更新 LOD
                boolean needsUpdate = false;
                FogChunkData existingData = chunkCache.get(posLong);

                if (existingData == null) {
                    needsUpdate = true; // 不存在，需要生成
                } else if (existingData.voxelSize() != targetSize) {
                    needsUpdate = true; // LOD 级别变了，需要重新生成
                }

                if (needsUpdate && !processingChunks.containsKey(posLong)) {
                    queueChunkGeneration(cx, cz, posLong, gameTime, targetSize);
                }
            }
        }
    }

    private void queueChunkGeneration(int chunkX, int chunkZ, long posLong, long gameTime, int step) {
        processingChunks.put(posLong, true);

        CompletableFuture.supplyAsync(() -> {
            List<FogPoint> points = new ArrayList<>();
            if (minecraft.level == null) return new FogChunkData(points, step);

            // 噪声时间偏移
            double noiseTimeOffset = gameTime * FogConfig.ANIMATION_SPEED * 0.01;

            for (int x = 0; x < 16; x += step) {
                for (int z = 0; z < 16; z += step) {
                    int worldX = chunkX * 16 + x;
                    int worldZ = chunkZ * 16 + z;

                    // 为了更准确的 LOD 采样，取体素中心点进行检测
                    int offset = step / 2;
                    BlockPos checkPos = new BlockPos(worldX + offset, 63, worldZ + offset);

                    Holder<Biome> biomeHolder = minecraft.level.getBiome(checkPos);
                    boolean isCore = biomeHolder.is(ResourceLocation.fromNamespaceAndPath("redshift", "aerosol_mangroves"));
                    boolean isEdge = biomeHolder.is(ResourceLocation.fromNamespaceAndPath("redshift", "aerosol_mangroves_edge"));

                    if (!isCore && !isEdge) continue;

                    float density = sampler.sample(worldX + offset, worldZ + offset, noiseTimeOffset);

                    if (density > 0) {
                        float yOffset = density * FogConfig.Y_RANGE;
                        float alpha = density;

                        if (isEdge) {
                            yOffset *= 0.4f;
                            alpha *= 0.6f;
                        }

                        points.add(new FogPoint(x, yOffset, z, alpha, isEdge));
                    }
                }
            }
            return new FogChunkData(points, step);
        }).thenAccept(data -> {
            chunkCache.put(posLong, data);
            processingChunks.remove(posLong);
        });
    }

    public ConcurrentHashMap<Long, FogChunkData> getVisibleChunks() {
        return chunkCache;
    }
}
