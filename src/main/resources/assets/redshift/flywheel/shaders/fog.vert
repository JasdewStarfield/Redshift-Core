#include "flywheel:internal/uniforms/uniforms.glsl"

void flw_materialVertex() {
    // 1. 获取世界坐标 (用于计算 UV)
    vec4 worldPos = _flw_modelMatrix * vec4(flw_vertexPos, 1.0);

    // 2. 动态 UV (世界坐标对齐 + 时间流动)
    float scale = 1.0 / 64.0;

    // 控制流动速度
    float speedX = 0.5;
    float speedZ = 0.3;

    float time = flw_renderTicks + flw_partialTick;

    // 覆盖原本的 UV
    flw_vertexTexCoord.x = (worldPos.x + time * speedX) * scale;
    flw_vertexTexCoord.y = (worldPos.z + time * speedZ) * scale;

    // 使用 smoothstep 让渐变更自然，而不是线性的
    float alpha = smoothstep(0.0, 1.0, flw_vertexPos.y);

    // 将计算出的透明度应用到顶点颜色上
    flw_vertexColor.a *= alpha;
}