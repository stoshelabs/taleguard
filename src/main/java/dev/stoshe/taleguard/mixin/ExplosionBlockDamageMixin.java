package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Explosion block damage.
 *
 * <p>Since 0.6 an explosion damages each block through the short {@code BlockHarvestUtils.performBlockDamage}
 * overload, which passes no tool id and no entity — so the old "tool id contains explosion" test could never
 * match again. The guard sits instead at that overload's call site in {@code ExplosionUtils.processTargetBlocks},
 * where every call is an explosion. Player block damage lives in {@link BreakBlockInteractionMixin}.
 */
@Mixin(com.hypixel.hytale.server.core.entity.ExplosionUtils.class)
public abstract class ExplosionBlockDamageMixin {

    @Redirect(method = "processTargetBlocks", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/modules/interaction/BlockHarvestUtils;performBlockDamage(Lorg/joml/Vector3i;Lcom/hypixel/hytale/server/core/inventory/ItemStack;Lcom/hypixel/hytale/server/core/asset/type/item/config/ItemTool;FIZLcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentAccessor;Lcom/hypixel/hytale/component/ComponentAccessor;)Z"), require = 0)
    private static boolean taleguard$guardExplosionBlock(Vector3i target, ItemStack itemStack, ItemTool tool,
            float damageScale, int setBlockSettings, boolean flag, Ref<ChunkStore> chunkRef,
            ComponentAccessor<EntityStore> entityStore, ComponentAccessor<ChunkStore> chunkStore) {
        if (target != null && entityStore != null) {
            Object external = entityStore.getExternalData();
            if (external instanceof EntityStore es && es.getWorld() != null
                    && !taleguard$checkAllowedNoNotify(null, es.getWorld().getName(), target.x, target.y, target.z, "EXPLOSION")) {
                // false is what performBlockDamage returns when the block took no damage.
                return false;
            }
        }
        return BlockHarvestUtils.performBlockDamage(target, itemStack, tool, damageScale, setBlockSettings, flag,
                chunkRef, entityStore, chunkStore);
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
