package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(com.hypixel.hytale.builtin.adventure.farming.interactions.HarvestCropInteraction.class)
public abstract class HarvestCropInteractionMixin {

    // 0.6 dropped the World, block type and rotation from harvest(): it takes the chunk-store accessor first and
    // looks the block up itself, so the world name comes from the entity store instead.
    @Redirect(method = "interactWithBlock", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/builtin/adventure/farming/FarmingUtil;harvest(Lcom/hypixel/hytale/component/ComponentAccessor;Lcom/hypixel/hytale/component/ComponentAccessor;Lcom/hypixel/hytale/component/Ref;Lorg/joml/Vector3i;)Z"), require = 0)
    private boolean taleguard$guardHarvest(ComponentAccessor<ChunkStore> chunkAccessor,
            ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, Vector3i target) {
        PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && target != null) {
            Object external = accessor.getExternalData();
            String worldName = external instanceof EntityStore es && es.getWorld() != null ? es.getWorld().getName() : "";
            if (!taleguard$checkAllowed(playerRef.getUuid(), worldName, target.x, target.y, target.z, "CROP_HARVEST")) {
                // false is what harvest() returns when nothing was harvested.
                return false;
            }
        }
        return com.hypixel.hytale.builtin.adventure.farming.FarmingUtil.harvest(chunkAccessor, accessor, ref, target);
    }

    private static boolean taleguard$checkAllowed(java.util.UUID playerUuid, String worldName, double x, double y, double z, String type) {
        try {
            java.util.Map<?, ?> registry = (java.util.Map<?, ?>) System.getProperties().get("taleguard.hook.registry");
            if (registry == null || registry.isEmpty()) {
                return true;
            }
            java.util.List<Object> hooks = new java.util.ArrayList<>(registry.values());
            try {
                hooks.sort((h1, h2) -> {
                    int p1 = taleguard$getPriority(h1);
                    int p2 = taleguard$getPriority(h2);
                    return Integer.compare(p1, p2);
                });
            } catch (Throwable ignored) {
            }
            for (Object hook : hooks) {
                try {
                    java.lang.reflect.Method method = hook.getClass().getMethod("isAllowed", 
                        java.util.UUID.class, String.class, double.class, double.class, double.class, String.class);
                    boolean allowed = (boolean) method.invoke(hook, playerUuid, worldName, x, y, z, type);
                    if (!allowed) {
                        try {
                            java.lang.reflect.Method denyMethod = hook.getClass().getMethod("notifyDenied", 
                                java.util.UUID.class, String.class, double.class, double.class, double.class, String.class);
                            denyMethod.invoke(hook, playerUuid, worldName, x, y, z, type);
                        } catch (Throwable ignored) {
                        }
                        return false;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static int taleguard$getPriority(Object hook) {
        try {
            java.lang.reflect.Method method = hook.getClass().getMethod("getPriority");
            return (int) method.invoke(hook);
        } catch (Throwable ignored) {
            return 2; // Default priority
        }
    }
}
