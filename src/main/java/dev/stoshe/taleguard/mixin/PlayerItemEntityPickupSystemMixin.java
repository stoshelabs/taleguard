package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.joml.Vector3dc;

@Mixin(com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem.class)
public abstract class PlayerItemEntityPickupSystemMixin {

    // 0.6 gathers every player in pickup range with ordered() — passing the item position as a Vector3dc — and
    // no longer asks for the single closest one, so this is the only lookup left to filter. Players the hook
    // denies are dropped from the candidate list before the system walks it.
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/component/spatial/SpatialStructure;ordered(Lorg/joml/Vector3dc;DLjava/util/List;)V"), require = 0)
    private void taleguard$guardOrderedPickup(SpatialStructure<?> structure, Vector3dc position, double radius, java.util.List targetPlayerRefs,
            float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        structure.ordered(position, radius, targetPlayerRefs);
        try {
            Object external = store.getExternalData();
            String worldName = "";
            if (external instanceof EntityStore es && es.getWorld() != null) {
                worldName = es.getWorld().getName();
            }
            final String finalWorldName = worldName;
            final double x = position.x();
            final double y = position.y();
            final double z = position.z();
            targetPlayerRefs.removeIf(obj -> {
                try {
                    if (!(obj instanceof Ref<?> ref)) {
                        return false;
                    }
                    PlayerRef playerRef = store.getComponent((Ref) ref, PlayerRef.getComponentType());
                    if (playerRef == null) {
                        return false;
                    }
                    boolean allowed = taleguard$checkAllowed(playerRef.getUuid(), finalWorldName, x, y, z, "ITEM_PICKUP");
                    return !allowed;
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception ignored) {
        }
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
