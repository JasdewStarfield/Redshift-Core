package yourscraft.jasdewstarfield.redshift_core.client.fog;

public class FogConfig {
    // 基础高度：海平面
    public static final float FOG_HEIGHT = 60.5f;

    // 雾气厚度范围 (Y轴波动)
    public static final float Y_RANGE = 2.0f;

    // 生成距离 (以 Chunk 为单位)
    public static final int RENDER_DISTANCE_CHUNKS = 12;

    // 噪声缩放 (越小越平缓，越大越碎)
    public static final float SCALE = 0.05f;

    // 阈值：大于这个值的噪声才生成雾 (0.1 ~ 0.5 之间调整)
    // 越小雾越浓，越大雾越稀疏
    public static final float THRESHOLD = 0.1f;

    // 动画速度
    public static final double ANIMATION_SPEED = 0.002; // 水平流动速度
    public static final float BREATHING_SPEED = 0.005f; // 垂直呼吸速度
    public static final float BREATHING_AMPLITUDE = 0.5f; // 垂直呼吸幅度

    /**
     * 根据距离中心的区块数，决定体素大小
     * @param chunkDistance 距离玩家所在的 Chunk 距离
     * @return 体素大小 (必须是 16 的因数: 1, 2, 4, 8, 16)
     */
    public static int getLODVoxelSize(int chunkDistance) {
        if (chunkDistance <= 3) {
            return 4; // 近景：高精度
        } else if (chunkDistance <= 7) {
            return 8; // 中景：中等精度
        } else {
            return 16; // 远景：低精度 (一整个区块一个体素)
        }
    }
}
