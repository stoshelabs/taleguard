package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy;
import com.hypixel.hytale.server.core.asset.type.fluid.FiniteFluidTicker;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(com.hypixel.hytale.server.core.asset.type.fluid.FiniteFluidTicker.class)
public abstract class FiniteFluidTickerMixin {

    // Both helpers are private on the target; @Shadow is how a mixin calls them.
    // The bodies are never executed — Mixin replaces them with the real methods.

    @Shadow
    private boolean spreadDownwards(FluidTicker.Accessor accessor, FluidSection fromFluid, BlockSection fromBlock,
            FluidSection toFluid, BlockSection toBlock, int x, int y, int z, Fluid fluid, int fluidId, byte level,
            int arg12, byte arg13) {
        throw new AssertionError("shadow");
    }

    @Shadow
    private BlockTickStrategy spreadSideways(long gameTick, FluidTicker.Accessor accessor, FluidSection section,
            BlockSection blockSection, int x, int y, int z, Fluid fluid, int fluidId, byte level) {
        throw new AssertionError("shadow");
    }

    // Guarded one level up from the sibling DefaultFluidTicker, and not by choice.
    //
    // There, spread() writes the fluid itself, so the guard can sit on the
    // setFluid call. Here spread() delegates to spreadDownwards and
    // spreadSideways, and those are where the writes happen — but neither
    // receives the World, and FluidTicker.Accessor cannot reach one either, so a
    // guard inside them has no world name to ask about.
    //
    // Redirecting the delegation instead keeps the world in scope. The unit of
    // protection is coarser: a denied check stops that whole spread step rather
    // than one block write. For a flow that is being refused anyway, refusing it
    // before it starts is the same outcome.

    @Redirect(method = "spread", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/asset/type/fluid/FiniteFluidTicker;spreadDownwards(Lcom/hypixel/hytale/server/core/asset/type/fluid/FluidTicker$Accessor;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/FluidSection;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/FluidSection;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;IIILcom/hypixel/hytale/server/core/asset/type/fluid/Fluid;IBIB)Z"), require = 0)
    private boolean taleguard$guardSpreadDownwards(FiniteFluidTicker self, FluidTicker.Accessor accessor,
            FluidSection fromFluid, BlockSection fromBlock, FluidSection toFluid, BlockSection toBlock,
            int x, int y, int z, Fluid fluid, int fluidId, byte level, int arg12, byte arg13,
            World world, long gameTick, FluidTicker.Accessor spreadAccessor, FluidSection spreadSection,
            BlockSection spreadBlockSection, Fluid spreadFluid, int currentFluidId, byte currentLevel,
            int sourceX, int sourceY, int sourceZ) {
        // Downwards: the destination is the block below the source.
        if (!taleguard$allows(world, sourceX, sourceY, sourceZ, sourceX, sourceY - 1, sourceZ)) {
            return false;
        }
        return spreadDownwards(accessor, fromFluid, fromBlock, toFluid, toBlock, x, y, z, fluid, fluidId,
                level, arg12, arg13);
    }

    @Redirect(method = "spread", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/asset/type/fluid/FiniteFluidTicker;spreadSideways(JLcom/hypixel/hytale/server/core/asset/type/fluid/FluidTicker$Accessor;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/FluidSection;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;IIILcom/hypixel/hytale/server/core/asset/type/fluid/Fluid;IB)Lcom/hypixel/hytale/server/core/asset/type/blocktick/BlockTickStrategy;"), require = 0)
    private BlockTickStrategy taleguard$guardSpreadSideways(FiniteFluidTicker self, long gameTickArg,
            FluidTicker.Accessor accessor, FluidSection section, BlockSection blockSection,
            int x, int y, int z, Fluid fluid, int fluidId, byte level,
            World world, long gameTick, FluidTicker.Accessor spreadAccessor, FluidSection spreadSection,
            BlockSection spreadBlockSection, Fluid spreadFluid, int currentFluidId, byte currentLevel,
            int sourceX, int sourceY, int sourceZ) {
        // Sideways: the destination is not known until the step picks one, so the
        // source is checked. A source inside a protected area is what a plot's
        // neighbour is being protected from.
        if (!taleguard$allows(world, sourceX, sourceY, sourceZ, sourceX, sourceY, sourceZ)) {
            // IGNORED is this method's own "nothing happened" answer.
            return BlockTickStrategy.IGNORED;
        }
        return spreadSideways(gameTickArg, accessor, section, blockSection, x, y, z, fluid, fluidId, level);
    }

    private static boolean taleguard$allows(World world, int fromX, int fromY, int fromZ,
            int toX, int toY, int toZ) {
        if (world == null) {
            return true;
        }
        return taleguard$isFluidFlowAllowed(world.getName(), fromX, fromY, fromZ, toX, toY, toZ);
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
