package yourscraft.jasdewstarfield.redshift_core.client.fog;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

public class FogSampler {

    private final SimplexNoise noise;

    public FogSampler(long seed) {
        // 使用固定的种子或世界种子初始化
        RandomSource random = RandomSource.create(seed);
        this.noise = new SimplexNoise(random);
    }

    /**
     * 计算某一点是否有雾
     * @param x 世界坐标 X
     * @param z 世界坐标 Z
     * @param timeOffset 时间偏移量 (用于让雾飘动)
     * @return 雾的密度 (0.0 ~ 1.0), 如果为 0 则表示没有雾
     */
    public float sample(double x, double z, double timeOffset) {
        // 1. 基础噪声采样 (加上时间偏移实现流动)
        // 坐标除以 scale: 放大噪声波形
        double baseValue = noise.getValue(x * FogConfig.SCALE + timeOffset, z * FogConfig.SCALE + timeOffset);

        // 2. 归一化到 0~1 范围 (Simplex 输出通常在 -1 ~ 1)
        float value = (float) (baseValue + 1.0) / 2.0f;

        // 3. 应用阈值过滤
        if (value < FogConfig.THRESHOLD) {
            return 0.0f;
        }

        // 4. 重新映射剩余的值到 0~1，让边缘更柔和
        return (value - FogConfig.THRESHOLD) / (1.0f - FogConfig.THRESHOLD);
    }
}
