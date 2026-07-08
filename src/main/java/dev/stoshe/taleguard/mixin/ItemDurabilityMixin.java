package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LivingEntity.class)
public abstract class ItemDurabilityMixin {

    /**
     * Base LivingEntity already returns false; keep explicit to avoid callback-based injection.
     * Consults the TaleGuard hook registry so a hook can request durability loss be prevented.
     */
    @Overwrite
    public boolean canDecreaseItemStackDurability(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        try {
            if (ref != null && accessor != null) {
                PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
                TransformComponent transform = accessor.getComponent(ref, TransformComponent.getComponentType());
                Object external = accessor.getExternalData();
                if (playerRef != null && transform != null && external instanceof EntityStore es && es.getWorld() != null) {
                    var pos = transform.getPosition();
                    if (!taleguard$checkAllowedNoNotify(playerRef.getUuid(), es.getWorld().getName(),
                            (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z), "DURABILITY")) {
                        return false;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean taleguard$checkAllowedNoNotify(java.util.UUID playerUuid, String worldName, double x, double y, double z, String type) {
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
