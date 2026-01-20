package yourscraft.jasdewstarfield.redshift_core.client.fog;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
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
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                .build();

        // 2. 构建 Mesh
        int vertexCount = 20;
        FogVertexList vertices = new FogVertexList(vertexCount);


        // 防止 Z-Fighting，引入微小偏移量，让 Mesh 比标准 1x1x1 稍微小一点点
        float eps = 0.002f;

        float xMax = 1.0f - eps;
        float zMax = 1.0f - eps;

        float yMin = 0f;
        float yMax = 1.0f;

        // UV 计算
        float uMin = 0;
        float vMin = 0;
        float uMax = size / 32.0f;
        float vMax = size / 32.0f;

        int i = 0;

        // --- 顶面 (Y = 1) ---
        writeVertex(vertices, i++, eps, yMax, eps, uMin, vMin, 0, 1, 0);
        writeVertex(vertices, i++, eps, yMax, zMax, uMin, vMax, 0, 1, 0);
        writeVertex(vertices, i++, xMax, yMax, zMax, uMax, vMax, 0, 1, 0);
        writeVertex(vertices, i++, xMax, yMax, eps, uMax, vMin, 0, 1, 0);

        // --- 北面 (Z-) ---
        writeVertex(vertices, i++, xMax, yMax, eps, uMax, vMin, 0, 0, -1);
        writeVertex(vertices, i++, eps, yMax, eps, uMin, vMin, 0, 0, -1);
        writeVertex(vertices, i++, eps, yMin, eps, uMin, vMax, 0, 0, -1);
        writeVertex(vertices, i++, xMax, yMin, eps, uMax, vMax, 0, 0, -1);

        // --- 南面 (Z+) ---
        writeVertex(vertices, i++, eps, yMax, zMax, uMin, vMin, 0, 0, 1);
        writeVertex(vertices, i++, xMax, yMax, zMax, uMax, vMin, 0, 0, 1);
        writeVertex(vertices, i++, xMax, yMin, zMax, uMax, vMax, 0, 0, 1);
        writeVertex(vertices, i++, eps, yMin, zMax, uMin, vMax, 0, 0, 1);

        // --- 西面 (X-) ---
        writeVertex(vertices, i++, eps, yMax, eps, uMax, vMin, -1, 0, 0);
        writeVertex(vertices, i++, eps, yMax, zMax, uMin, vMin, -1, 0, 0);
        writeVertex(vertices, i++, eps, yMin, zMax, uMin, vMax, -1, 0, 0);
        writeVertex(vertices, i++, eps, yMin, eps, uMax, vMax, -1, 0, 0);

        // --- 东面 (X+) ---
        writeVertex(vertices, i++, xMax, yMax, zMax, uMax, vMin, 1, 0, 0);
        writeVertex(vertices, i++, xMax, yMax, eps, uMin, vMin, 1, 0, 0);
        writeVertex(vertices, i++, xMax, yMin, eps, uMin, vMax, 1, 0, 0);
        writeVertex(vertices, i++, xMax, yMin, zMax, uMax, vMax, 1, 0, 0);

        // 3. 创建 SimpleQuadMesh
        SimpleQuadMesh mesh = new SimpleQuadMesh(vertices, "fog_lod_" + size);

        // 4. 组合成 Model
        Model.ConfiguredMesh configuredMesh = new Model.ConfiguredMesh(material, mesh);
        return new SimpleModel(List.of(configuredMesh));
    }

    private static void writeVertex(FogVertexList v, int index, float x, float y, float z, float u, float vTex, float nx, float ny, float nz) {
        v.x[index] = x;
        v.y[index] = y;
        v.z[index] = z;
        v.u[index] = u;
        v.v[index] = vTex;
        v.nx[index] = nx;
        v.ny[index] = ny;
        v.nz[index] = nz;
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
