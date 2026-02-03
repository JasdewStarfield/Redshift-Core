package yourscraft.jasdewstarfield.redshift_core.common.geyser;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class GeyserSteamParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected GeyserSteamParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);

        // --- 物理属性关键设置 ---
        this.hasPhysics = true;
        this.gravity = -0.1F;

        // --- 视觉属性 ---
        this.sprites = sprites;
        this.scale(2.5F + this.random.nextFloat() * 0.5F); // 稍微随机一点的大小，模拟云雾
        this.setSize(0.25F, 0.25F); // 碰撞箱大小，不要太大以免在狭窄空间卡住

        int i = (int)(8.0 / (Math.random() * 0.8 + 0.3));
        this.lifetime = (int)Math.max(i * 2.5F, 1.0F);

        // 设置初始速度
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;

        // 摩擦力 (阻力)
        this.friction = 0.96F;

        this.quadSize *= 1.875F;

        // 设置贴图 (需要在 Provider 里传入 SpriteSet)
        this.setSpriteFromAge(sprites);

        // 稍微随机化一点颜色，让蒸汽更有质感 (可选)
        float grey = 1.0F - (float)(Math.random() * 0.3F);
        this.setColor(grey, grey, grey);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            if (this.yd == 0 && this.age > 1) {
                // 让它水平扩散一点，模拟蒸汽在天花板铺开的效果
                this.xd *= 1.05;
                this.zd *= 1.05;
            }
        }
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        // 半透明渲染
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp((this.age + scaleFactor) / this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    // --- 工厂类 (Provider) ---
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public @NotNull Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new GeyserSteamParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}
