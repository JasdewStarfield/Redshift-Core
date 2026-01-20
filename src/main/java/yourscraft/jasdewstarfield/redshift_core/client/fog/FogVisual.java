package yourscraft.jasdewstarfield.redshift_core.client.fog;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.task.SimplePlan;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FogVisual implements EffectVisual<FogEffect>, DynamicVisual {

    private final InstancerProvider provider;
    private final Vec3i renderOrigin;
    private final FogEffect effect;

    // 实例缓存：ChunkPos -> 实例列表
    private final ConcurrentHashMap<Long, List<TransformedInstance>> chunkInstances = new ConcurrentHashMap<>();

    // LOD 状态缓存：ChunkPos -> VoxelSize (用于检测 LOD 变化)
    private final ConcurrentHashMap<Long, Integer> chunkLODs = new ConcurrentHashMap<>();

    // Instancer 缓存
    private final Map<Integer, Instancer<TransformedInstance>> instancerMap = new HashMap<>();

    public FogVisual(VisualizationContext ctx, FogEffect effect) {
        this.provider = ctx.instancerProvider();
        this.renderOrigin = ctx.renderOrigin();
        this.effect = effect;
    }

    private Instancer<TransformedInstance> getInstancer(int size) {
        return instancerMap.computeIfAbsent(size, s -> {
            Model model = FogModels.get(s);
            return provider.instancer(InstanceTypes.TRANSFORMED, model);
        });
    }

    @Override
    public Plan<Context> planFrame() {
        // Flywheel 的并行执行计划
        return SimplePlan.of(this::updateFog);
    }

    private void updateFog(DynamicVisual.Context ctx) {
        // 1. 获取 FogGenerator 逻辑数据
        ConcurrentHashMap<Long, FogGenerator.FogChunkData> logicChunks = FogRenderer.getGenerator().getVisibleChunks();

        // 2. 清理失效的 Chunk 实例
        chunkInstances.keySet().removeIf(key -> {
            if (!logicChunks.containsKey(key)) {
                List<TransformedInstance> instances = chunkInstances.get(key);
                if (instances != null) {
                    instances.forEach(Instance::delete); // 删除 Flywheel 实例
                }
                chunkLODs.remove(key);
                return true;
            }
            return false;
        });

        // 3. 准备动画参数
        float partialTick = ctx.partialTick();
        long gameTime = effect.level() != null ? effect.level().getLevelData().getGameTime() : 0;
        double smoothTime = gameTime + partialTick;
        float flowTime = (float) (smoothTime * FogConfig.ANIMATION_SPEED);
        float breathingTime = (float) (smoothTime * FogConfig.BREATHING_SPEED);
        float baseWorldBrightness = FogRenderer.calculateWorldBrightness(Minecraft.getInstance(), partialTick);
        float immersion = FogEventHandler.getCurrentFogWeight();
        double maxDist = (FogConfig.RENDER_DISTANCE_CHUNKS - 1) * 16.0;

        Vec3 cameraPos = ctx.camera().getPosition();

        // 4. 更新或创建实例
        for (Map.Entry<Long, FogGenerator.FogChunkData> entry : logicChunks.entrySet()) {
            long chunkKey = entry.getKey();
            FogGenerator.FogChunkData data = entry.getValue();
            int currentSize = data.voxelSize();

            List<TransformedInstance> instances = chunkInstances.computeIfAbsent(chunkKey, k -> new ArrayList<>());

            int cachedSize = chunkLODs.getOrDefault(chunkKey, -1);

            // 如果数量不匹配（LOD 变化或新生成），重建该 Chunk 的所有实例
            if (instances.size() != data.points().size() || cachedSize != currentSize) {
                // 销毁旧的
                instances.forEach(Instance::delete);
                instances.clear();

                chunkLODs.put(chunkKey, currentSize);

                // 如果有数据，创建新实例
                if (!data.points().isEmpty()) {
                    Instancer<TransformedInstance> instancer = getInstancer(currentSize);
                    for (int i = 0; i < data.points().size(); i++) {
                        instances.add(instancer.createInstance());
                    }
                }
            }

            // 更新实例属性
            if (!instances.isEmpty()) {
                updateInstances(instances, data, chunkKey, flowTime, breathingTime, baseWorldBrightness, immersion, maxDist, cameraPos);
            }
        }
    }

    private void updateInstances(List<TransformedInstance> instances, FogGenerator.FogChunkData data,
                                 long chunkKey, float flowTime, float breathingTime,
                                 float brightness, float immersion, double maxDist, net.minecraft.world.phys.Vec3 cameraPos) {

        int chunkX = (int) (chunkKey & 0xFFFFFFFFL);
        int chunkZ = (int) (chunkKey >>> 32);
        float worldChunkX = chunkX * 16;
        float worldChunkZ = chunkZ * 16;
        float size = data.voxelSize();
        float heightScale = FogConfig.Y_RANGE;

        List<FogGenerator.FogPoint> points = data.points();

        // 基础颜色 (RGB) - 可以根据需要调整
        float baseR = 0.65f;
        float baseG = 0.75f;
        float baseB = 0.45f;
        float finalR = baseR * brightness;
        float finalG = baseG * brightness;
        float finalB = baseB * brightness;

        for (int i = 0; i < instances.size(); i++) {
            TransformedInstance instance = instances.get(i);
            FogGenerator.FogPoint point = points.get(i);

            float wx = worldChunkX + point.x();
            float wz = worldChunkZ + point.z();

            // 计算距离淡出 (Fade)
            double distSq = (wx - cameraPos.x) * (wx - cameraPos.x) + (wz - cameraPos.z) * (wz - cameraPos.z);
            double dist = Math.sqrt(distSq);
            float fade = FogRenderer.computeFade(dist, maxDist);

            if (fade <= 0.01f) {
                // 如果完全不可见，将矩阵设为零，Flywheel 会剔除它
                instance.setZeroTransform().setChanged();
                continue;
            }

            // 靠近玩家时的淡出效果
            float proximityFade = 1.0f;
            if (immersion > 0.05f) {
                proximityFade = (float) Mth.clamp((dist - 5.0) / 7.0, 0.0, 1.0);
                proximityFade = Mth.lerp(immersion, 1.0f, proximityFade);
            }

            // 呼吸动画
            float localBreathing = FogRenderer.getComplexBreathing(wx, wz, breathingTime);
            float y = FogConfig.FOG_HEIGHT + point.yOffset() + localBreathing;

            float finalAlpha = point.alpha() * fade * proximityFade;

            float relX = wx - renderOrigin.getX();
            float relY = y - renderOrigin.getY();
            float relZ = wz - renderOrigin.getZ();

            // --- 设置实例属性 ---

            // 1. 变换
            instance.setIdentityTransform()
                    .translate(relX, relY, relZ)
                    .scale(size, heightScale, size) // 非均匀缩放
                    .setChanged();

            // 2. 颜色
            instance.color(finalR, finalG, finalB, finalAlpha)
                    .setChanged();

            // 3. 光照
            instance.light(15, 15)
                    .setChanged();
        }
    }

    @Override
    public void update(float partialTick) {
    }

    @Override
    public void delete() {
        // 清理所有创建的实例
        chunkInstances.values().forEach(list -> list.forEach(Instance::delete));
        chunkInstances.clear();
    }
}
