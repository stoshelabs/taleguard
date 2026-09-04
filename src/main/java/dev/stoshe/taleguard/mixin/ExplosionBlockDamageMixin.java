package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils.class)
public abstract class ExplosionBlockDamageMixin {

    @Redirect(method = "performBlockDamage(Lcom/hypixel/hytale/component/Ref;Lorg/joml/Vector3i;Lcom/hypixel/hytale/server/core/inventory/ItemStack;Lcom/hypixel/hytale/server/core/asset/type/item/config/ItemTool;Ljava/lang/String;ZFILcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentAccessor;Lcom/hypixel/hytale/component/ComponentAccessor;)Z", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/component/ComponentAccessor;getComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;"), require = 0)
    private static Object taleguard$guardExplosionChunkLookup(ComponentAccessor<ChunkStore> chunkAccessor,
            Ref<ChunkStore> chunkRef, Object worldChunkType, Ref<EntityStore> ref,
            Vector3i targetBlockPos, Object itemStack, Object tool, String toolId, boolean matchTool,
            float damageScale, int setBlockSettings, Ref<ChunkStore> chunkReference,
            ComponentAccessor<EntityStore> entityStore, ComponentAccessor<ChunkStore> chunkStore) {
        if (targetBlockPos != null && toolId != null && toolId.toLowerCase().contains("explosion")) {
            Object external = entityStore.getExternalData();
            if (external instanceof EntityStore es && es.getWorld() != null
                    && !taleguard$checkAllowedNoNotify(null, es.getWorld().getName(), targetBlockPos.x, targetBlockPos.y, targetBlockPos.z, "EXPLOSION")) {
                return null;
            }
        }
        if (ref != null && targetBlockPos != null) {
            PlayerRef playerRef = entityStore.getComponent(ref, PlayerRef.getComponentType());
            Object external = entityStore.getExternalData();
            if (playerRef != null && external instanceof EntityStore es && es.getWorld() != null
                    && !taleguard$checkAllowed(playerRef.getUuid(), es.getWorld().getName(), targetBlockPos.x, targetBlockPos.y,
                            targetBlockPos.z, "HARVEST")) {
                return null;
            }
        }
        return chunkAccessor.getComponent(chunkRef, (com.hypixel.hytale.component.ComponentType<ChunkStore, WorldChunk>) worldChunkType);
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
