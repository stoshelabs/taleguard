package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * Player block damage — the mining a {@code BreakBlockInteraction} does.
 *
 * <p>Up to 0.5 this went through the same {@code performBlockDamage} as explosions and was guarded there. In 0.6
 * the interaction calls two overloads of its own: one block, or a target plus a list of extra blocks (area tools).
 * Both lead with two entity refs; which one is the player isn't documented, so the first that has a
 * {@link PlayerRef} is the one asked about.
 */
@Mixin(com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.BreakBlockInteraction.class)
public abstract class BreakBlockInteractionMixin {

    @Redirect(method = "interactWithBlock", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/modules/interaction/BlockHarvestUtils;performBlockDamage(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Ref;Lorg/joml/Vector3i;Lcom/hypixel/hytale/server/core/inventory/ItemStack;Lcom/hypixel/hytale/server/core/asset/type/item/config/ItemTool;Ljava/lang/String;ZFIZLcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentAccessor;Lcom/hypixel/hytale/component/ComponentAccessor;)Z"), require = 0)
    private boolean taleguard$guardBlockDamage(Ref<EntityStore> first, Ref<EntityStore> second, Vector3i target,
            ItemStack itemStack, ItemTool tool, String toolId, boolean matchTool, float damageScale,
            int setBlockSettings, boolean flag, Ref<ChunkStore> chunkRef, ComponentAccessor<EntityStore> entityStore,
            ComponentAccessor<ChunkStore> chunkStore) {
        PlayerRef player = taleguard$player(first, second, entityStore);
        if (player != null && target != null
                && !taleguard$checkAllowed(player.getUuid(), taleguard$worldName(entityStore), target.x, target.y, target.z, "HARVEST")) {
            // false is what performBlockDamage returns when the block took no damage.
            return false;
        }
        return BlockHarvestUtils.performBlockDamage(first, second, target, itemStack, tool, toolId, matchTool,
                damageScale, setBlockSettings, flag, chunkRef, entityStore, chunkStore);
    }

    @Redirect(method = "interactWithBlock", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/modules/interaction/BlockHarvestUtils;performBlockDamage(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Ref;Ljava/util/List;Lorg/joml/Vector3i;Lcom/hypixel/hytale/server/core/inventory/ItemStack;Lcom/hypixel/hytale/server/core/asset/type/item/config/ItemTool;Ljava/lang/String;ZFIZZLcom/hypixel/hytale/component/ComponentAccessor;Lcom/hypixel/hytale/component/ComponentAccessor;)I"), require = 0)
    private int taleguard$guardAreaDamage(Ref<EntityStore> first, Ref<EntityStore> second, List<Vector3i> blocks,
            Vector3i target, ItemStack itemStack, ItemTool tool, String toolId, boolean matchTool, float damageScale,
            int setBlockSettings, boolean flagA, boolean flagB, ComponentAccessor<EntityStore> entityStore,
            ComponentAccessor<ChunkStore> chunkStore) {
        PlayerRef player = taleguard$player(first, second, entityStore);
        List<Vector3i> allowed = blocks;
        if (player != null) {
            String worldName = taleguard$worldName(entityStore);
            if (target != null
                    && !taleguard$checkAllowed(player.getUuid(), worldName, target.x, target.y, target.z, "HARVEST")) {
                return 0;
            }
            // The extra blocks can cross into land the player may not touch: keep only the allowed ones, in a copy
            // so the caller's list is never modified. Quietly — one denial message per swing is enough.
            if (blocks != null && !blocks.isEmpty()) {
                allowed = new ArrayList<>(blocks.size());
                for (Vector3i block : blocks) {
                    if (block == null
                            || taleguard$checkAllowedNoNotify(player.getUuid(), worldName, block.x, block.y, block.z, "HARVEST")) {
                        allowed.add(block);
                    }
                }
            }
        }
        return BlockHarvestUtils.performBlockDamage(first, second, allowed, target, itemStack, tool, toolId, matchTool,
                damageScale, setBlockSettings, flagA, flagB, entityStore, chunkStore);
    }

    private static PlayerRef taleguard$player(Ref<EntityStore> first, Ref<EntityStore> second,
            ComponentAccessor<EntityStore> entityStore) {
        if (entityStore == null) {
            return null;
        }
        try {
            if (first != null && first.isValid()) {
                PlayerRef playerRef = entityStore.getComponent(first, PlayerRef.getComponentType());
                if (playerRef != null) {
                    return playerRef;
                }
            }
            if (second != null && second.isValid()) {
                return entityStore.getComponent(second, PlayerRef.getComponentType());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String taleguard$worldName(ComponentAccessor<EntityStore> entityStore) {
        Object external = entityStore == null ? null : entityStore.getExternalData();
        return external instanceof EntityStore es && es.getWorld() != null ? es.getWorld().getName() : "";
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
                    if (!(boolean) method.invoke(hook, playerUuid, worldName, x, y, z, type)) {
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
