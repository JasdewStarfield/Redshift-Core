package yourscraft.jasdewstarfield.redshift_core.client.fog;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.vertex.DefaultVertexList;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FogModels {

    private static final Map<Integer, Model> CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation FOG_TEXTURE = ResourceLocation.fromNamespaceAndPath("redshift",
            "textures/environment/fog_cloud.png");

    private record RedshiftFogShader() implements MaterialShaders {
        @Override
        public ResourceLocation vertexSource() {
            return ResourceLocation.fromNamespaceAndPath("redshift", "shaders/fog.vert");
        }
        @Override
        public ResourceLocation fragmentSource() {
            return ResourceLocation.fromNamespaceAndPath("flywheel", "material/default.frag");
        }
    }

    private static final MaterialShaders FOG_SHADER = new RedshiftFogShader();

    public static Model get(int voxelSize) {
        return CACHE.computeIfAbsent(voxelSize, FogModels::createFogModel);
    }

    private static Model createFogModel(int size) {
        // 1. 构建材质
        SimpleMaterial material = SimpleMaterial.builder()
                .texture(FOG_TEXTURE)
                .transparency(Transparency.TRANSLUCENT)
                .backfaceCulling(false)
                .writeMask(WriteMask.COLOR)
                .cardinalLightingMode(CardinalLightingMode.OFF)
                .useLight(true)
                .shaders(FOG_SHADER)
                .build();

        // 2. 构建 Mesh
        int vertexCount = 20;
        FogVertexList vertices = new FogVertexList(vertexCount);

        // 防止 Z-Fighting，引入微小偏移量，让 Mesh 比标准 1x1x1 稍微小一点点
        float eps = 0.05f;

        float xMax = 1.0f - eps;
        float zMax = 1.0f - eps;

        float yMin = 0f;
        float yMax = 1.0f;

        int i = 0;

        // --- 顶面 (Y = 1) ---
        writeVertex(vertices, i++, eps, yMax, eps);
        writeVertex(vertices, i++, eps, yMax, zMax);
        writeVertex(vertices, i++, xMax, yMax, zMax);
        writeVertex(vertices, i++, xMax, yMax, eps);

        // --- 北面 (Z-) ---
        writeVertex(vertices, i++, xMax, yMax, eps);
        writeVertex(vertices, i++, eps, yMax, eps);
        writeVertex(vertices, i++, eps, yMin, eps);
        writeVertex(vertices, i++, xMax, yMin, eps);

        // --- 南面 (Z+) ---
        writeVertex(vertices, i++, eps, yMax, zMax);
        writeVertex(vertices, i++, xMax, yMax, zMax);
        writeVertex(vertices, i++, xMax, yMin, zMax);
        writeVertex(vertices, i++, eps, yMin, zMax);

        // --- 西面 (X-) ---
        writeVertex(vertices, i++, eps, yMax, eps);
        writeVertex(vertices, i++, eps, yMax, zMax);
        writeVertex(vertices, i++, eps, yMin, zMax);
        writeVertex(vertices, i++, eps, yMin, eps);

        // --- 东面 (X+) ---
        writeVertex(vertices, i++, xMax, yMax, zMax);
        writeVertex(vertices, i++, xMax, yMax, eps);
        writeVertex(vertices, i++, xMax, yMin, eps);
        writeVertex(vertices, i++, xMax, yMin, zMax);

        // 3. 创建 SimpleQuadMesh
        SimpleQuadMesh mesh = new SimpleQuadMesh(vertices, "fog_lod_" + size);

        // 4. 组合成 Model
        Model.ConfiguredMesh configuredMesh = new Model.ConfiguredMesh(material, mesh);
        return new SimpleModel(List.of(configuredMesh));
    }

    private static void writeVertex(FogVertexList v, int index, float x, float y, float z) {
        v.x[index] = x;
        v.y[index] = y;
        v.z[index] = z;
        v.u[index] = 0;
        v.v[index] = y;
        v.nx[index] = -1;
        v.ny[index] = 0;
        v.nz[index] = 0;
    }

    private static class FogVertexList implements DefaultVertexList {
        private final int vertexCount;
        public final float[] x, y, z;
        public final float[] u, v;
        public final float[] nx, ny, nz;

        public FogVertexList(int vertexCount) {
            this.vertexCount = vertexCount;
            this.x = new float[vertexCount];
            this.y = new float[vertexCount];
            this.z = new float[vertexCount];
            this.u = new float[vertexCount];
            this.v = new float[vertexCount];
            this.nx = new float[vertexCount];
            this.ny = new float[vertexCount];
            this.nz = new float[vertexCount];
        }

        @Override public int vertexCount() { return vertexCount; }

        @Override public float x(int index) { return x[index]; }
        @Override public float y(int index) { return y[index]; }
        @Override public float z(int index) { return z[index]; }
        @Override public float u(int index) { return u[index]; }
        @Override public float v(int index) { return v[index]; }
        @Override public float normalX(int index) { return nx[index]; }
        @Override public float normalY(int index) { return ny[index]; }
        @Override public float normalZ(int index) { return nz[index]; }

        // 颜色默认返回 1.0 (白色)，由 Instance 染色
        // Light 默认返回 0
    }
}
