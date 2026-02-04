package yourscraft.jasdewstarfield.redshift_core.client.rhythm;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class RhythmBlastSound extends AbstractTickableSoundInstance {

    private final Player player;
    private float targetVolume = 0.0f;

    public RhythmBlastSound(Player player, SoundEvent soundEvent) {
        super(soundEvent, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.player = player;

        this.looping = false;
        this.delay = 0;
        this.volume = 0.01f;
        this.pitch = 1.0f;
        this.relative = true;
    }

    public void setTargetVolume(float vol) {
        this.targetVolume = Mth.clamp(vol, 0.0f, 1.0f);
    }

    @Override
    public void tick() {
        if (this.player.isRemoved()) {
            this.stop();
            return;
        }

        // 1. 实时跟随玩家位置
        this.x = 0;
        this.y = 0;
        this.z = 0;

        // 2. 实时平滑调整音量
        if (Math.abs(this.volume - this.targetVolume) > 0.001f) {
            this.volume = Mth.lerp(0.1f, this.volume, this.targetVolume);
        }
    }
}
