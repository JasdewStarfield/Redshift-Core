package yourscraft.jasdewstarfield.redshift_core.client.fog;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.level.LevelAccessor;

public record FogEffect(LevelAccessor level) implements Effect {
    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new FogVisual(ctx, this);
    }
}
