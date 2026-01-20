#include "flywheel:internal/uniforms/uniforms.glsl"
#include "flywheel:internal/api_impl.glsl"

vec2 getDrift(vec3 pos, float time) {
    float dx = sin(time * 0.01 + pos.x * 0.3 + pos.z * 0.1) * 0.35;
    float dz = cos(time * 0.008 + pos.x * 0.1 - pos.z * 0.3) * 0.35;
    return vec2(dx, dz);
}

void flw_materialVertex() {
    // 相对坐标
    vec3 relativePos = flw_vertexPos.xyz;
    // 绝对坐标
    vec3 absolutePos = relativePos + flw_renderOrigin;

    // 动态 UV (世界坐标对齐 + 时间流动)
    float scale = 1.0 / 64.0;

    // 控制流动速度
    float speedX = 0.2;
    float speedZ = 0.15;

    float time = flw_renderTicks + flw_partialTick;

    flw_vertexTexCoord.x = (absolutePos.x + time * speedX) * scale;
    flw_vertexTexCoord.y = (absolutePos.z + time * speedZ) * scale;

    float localY = flw_vertexTexCoord.y;

    vec2 drift = getDrift(absolutePos, time);

    flw_vertexPos.x += drift.x;
    flw_vertexPos.z += drift.y;

    // 使用 smoothstep 让渐变更自然，而不是线性的
    float alpha = smoothstep(0.0, 1.0, localY);
    flw_vertexColor.a *= alpha;
}