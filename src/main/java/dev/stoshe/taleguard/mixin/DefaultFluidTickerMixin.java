package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(com.hypixel.hytale.server.core.asset.type.fluid.DefaultFluidTicker.class)
public abstract class DefaultFluidTickerMixin {

    @Redirect(method = "spread", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/FluidSection;setFluid(IIIIB)Z"), require = 0)
    private boolean taleguard$guardSetFluid(FluidSection section, int localX, int localY, int localZ, int fluidId, byte level,
            World world, long gameTick, FluidTicker.Accessor accessor, FluidSection sourceSection, BlockSection blockSection,
            Fluid fluid, int currentFluidId, byte currentLevel, int sourceX, int sourceY, int sourceZ) {
        int toX = (section.getX() << 4) + localX;
        int toY = (section.getY() << 4) + localY;
        int toZ = (section.getZ() << 4) + localZ;
        if (world != null && !taleguard$isFluidFlowAllowed(world.getName(), sourceX, sourceY, sourceZ, toX, toY, toZ)) {
            return false;
        }
        return section.setFluid(localX, localY, localZ, fluidId, level);
    }

    private static boolean taleguard$isFluidFlowAllowed(String worldName, int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        try {
            java.util.Map<?, ?> registry = (java.util.Map<?, ?>) System.getProperties().get("taleguard.hook.registry");
            if (registry == null || registry.isEmpty()) {
                return true;
            }
            for (Object hook : registry.values()) {
                try {
                    java.lang.reflect.Method method = hook.getClass().getMethod("isFluidFlowAllowed",
                        String.class, int.class, int.class, int.class, int.class, int.class, int.class);
                    boolean allowed = (boolean) method.invoke(hook, worldName, fromX, fromY, fromZ, toX, toY, toZ);
                    if (!allowed) {
                        return false;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return true;
    }
}
