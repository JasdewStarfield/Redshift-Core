package yourscraft.jasdewstarfield.redshift_core.client.fog;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FogFlywheelManager {

    private static FogFlywheelManager INSTANCE;

    // 缓存 Chunk 对应的实例列表
    private final ConcurrentHashMap<Long, List<TransformedInstance>> chunkInstances = new ConcurrentHashMap<>();

    // 我们使用的模型
    public static final PartialModel FOG_MODEL = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath("redshift", "block/fog_cloud")
    );

    public static FogFlywheelManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FogFlywheelManager();
        }
        return INSTANCE;
    }

    /**
     * 在每一帧渲染时调用，用于更新实例的动画（呼吸、飘动）
     */
    public void tick(Level level, RenderLevelStageEvent event) {
        // 1. 获取 VisualizationManager，这是 Flywheel 的入口
        if (!VisualizationManager.supportsVisualization(level)) {
            reset(); // 如果不支持（例如后端被禁用），清理所有实例
            return;
        }

        VisualizationManager manager = VisualizationManager.get(level);
        if (manager == null) return;

        // 3. 获取 Instancer
        // 关键点：使用 RenderType.translucent() 以支持半透明混合
        InstancerProvider provider;
        if (manager instanceof InstancerProvider instancerProvider) {
            provider = instancerProvider;
        } else {
            return;
        }

        Model model = Models.block(Blocks.WHITE_STAINED_GLASS.defaultBlockState());

        Instancer<TransformedInstance> instancer = provider.instancer(
                InstanceTypes.TRANSFORMED,
                model
        );

        // 4. 获取 FogGenerator 逻辑数据
        ConcurrentHashMap<Long, FogGenerator.FogChunkData> logicChunks = FogRenderer.getGenerator().getVisibleChunks();

        // 5. 清理失效的 Chunk 实例
        chunkInstances.keySet().removeIf(key -> {
            if (!logicChunks.containsKey(key)) {
                List<TransformedInstance> instances = chunkInstances.get(key);
                if (instances != null) {
                    instances.forEach(Instance::delete); // 删除 Flywheel 实例
                }
                return true;
            }
            return false;
        });

        // 准备动画参数
        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double smoothTime = gameTime + partialTick;
        float flowTime = (float) (smoothTime * FogConfig.ANIMATION_SPEED);
        float breathingTime = (float) (smoothTime * FogConfig.BREATHING_SPEED);
        float baseWorldBrightness = FogRenderer.calculateWorldBrightness(Minecraft.getInstance(), partialTick);
        float immersion = FogEventHandler.getCurrentFogWeight();
        double maxDist = (FogConfig.RENDER_DISTANCE_CHUNKS - 1) * 16.0;

        // 6. 更新或创建实例
        for (Map.Entry<Long, FogGenerator.FogChunkData> entry : logicChunks.entrySet()) {
            long chunkKey = entry.getKey();
            FogGenerator.FogChunkData data = entry.getValue();

            List<TransformedInstance> instances = chunkInstances.computeIfAbsent(chunkKey, k -> new ArrayList<>());

            // 如果数量不匹配（LOD 变化或新生成），重建该 Chunk 的所有实例
            if (instances.size() != data.points().size()) {
                instances.forEach(Instance::delete);
                instances.clear();
                for (int i = 0; i < data.points().size(); i++) {
                    // 创建新实例
                    instances.add(instancer.createInstance());
                }
            }

            // 更新实例属性 (CPU -> GPU 属性上传)
            updateInstances(instances, data, chunkKey, flowTime, breathingTime, baseWorldBrightness, immersion, maxDist, event.getCamera().getPosition());
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
            float fade = FogRenderer.computeFade(dist, maxDist); // 提取出来的公共方法

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

            // --- 设置实例属性 ---

            // 1. 变换 (Transform)
            instance.setIdentityTransform()
                    .translate(wx, y, wz)
                    .scale(size, size, size)
                    .setChanged();

            // 2. 颜色 (Color) - TransformedInstance 支持这个！
            // 注意：Alpha 必须配合 RenderType.translucent() 才能生效
            instance.color(finalR, finalG, finalB, finalAlpha)
                    .setChanged();

            // 3. 光照 (Light) - 设置为最大天空光和方块光，或者根据环境动态调整
            // 这里设为 (15, 15) 让它看起来发光，或者 (0, 0) 让它受环境光影响
            instance.light(15, 15)
                    .setChanged();
        }
    }

    public void reset() {
        chunkInstances.values().forEach(list -> list.forEach(Instance::delete));
        chunkInstances.clear();
    }
}
