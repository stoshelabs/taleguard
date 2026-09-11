package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockInteractionUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils.class)
public abstract class BlockHarvestUtilsMixin {

    private static final ThreadLocal<Boolean> taleguard$denied = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Vector3i> taleguard$target = new ThreadLocal<>();

    // 0.6 hands removeBlock the block's section too, so a denial can invalidate it directly.
    @Shadow
    protected static void removeBlock(Vector3i target, BlockType blockType, int setSettings, Ref<ChunkStore> chunkRef,
            BlockSection section, ComponentAccessor<ChunkStore> chunkAccessor) {
        throw new UnsupportedOperationException("shadow");
    }

    @Redirect(method = "performPickupByInteraction", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType;isUnknown()Z"), require = 0)
    private static boolean taleguard$initState(BlockType blockType) {
        taleguard$denied.set(Boolean.FALSE);
        taleguard$target.remove();
        return blockType.isUnknown();
    }

    @Redirect(method = "performPickupByInteraction", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;getRotationIndex(III)I"), require = 0)
    private static int taleguard$captureTarget(BlockSection section, int x, int y, int z) {
        taleguard$target.set(new Vector3i(x, y, z));
        return section.getRotationIndex(x, y, z);
    }

    @Redirect(method = "performPickupByInteraction", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/modules/interaction/BlockInteractionUtils;isNaturalAction(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentAccessor;)Z"), require = 0)
    private static boolean taleguard$checkPermission(Ref<EntityStore> playerRefEntity,
            ComponentAccessor<EntityStore> entityAccessor) {
        try {
            if (playerRefEntity != null && entityAccessor != null) {
                PlayerRef playerRef = entityAccessor.getComponent(playerRefEntity, PlayerRef.getComponentType());
                Vector3i target = taleguard$target.get();
                if (playerRef != null && target != null) {
                    String worldName = "";
                    Object ext = entityAccessor.getExternalData();
                    if (ext instanceof EntityStore es && es.getWorld() != null) {
                        worldName = es.getWorld().getName();
                    }
                    if (!taleguard$checkAllowed(playerRef.getUuid(), worldName, target.x, target.y, target.z, "HARVEST")) {
                        taleguard$denied.set(Boolean.TRUE);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return BlockInteractionUtils.isNaturalAction(playerRefEntity, entityAccessor);
    }

    @Redirect(method = "performPickupByInteraction", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/modules/interaction/BlockHarvestUtils;removeBlock(Lorg/joml/Vector3i;Lcom/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType;ILcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;Lcom/hypixel/hytale/component/ComponentAccessor;)V"), require = 0)
    private static void taleguard$guardRemoveBlock(Vector3i target, BlockType blockType, int setSettings,
            Ref<ChunkStore> chunkRef, BlockSection section, ComponentAccessor<ChunkStore> chunkAccessor) {
        if (Boolean.TRUE.equals(taleguard$denied.get())) {
            // Denied: keep the block, and invalidate it so the client's predicted removal is resent and rolled back.
            try {
                if (section != null) {
                    section.invalidateBlock(target.x, target.y, target.z);
                }
            } catch (Exception ignored) {
            }
            return;
        }
        removeBlock(target, blockType, setSettings, chunkRef, section, chunkAccessor);
    }

    @Redirect(method = "performPickupByInteraction", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/entity/ItemUtils;interactivelyPickupItem(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/inventory/ItemStack;Lorg/joml/Vector3d;Lcom/hypixel/hytale/component/ComponentAccessor;)V"), require = 0)
    private static void taleguard$guardPickup(Ref<EntityStore> playerRefEntity, ItemStack drop, Vector3d pos,
            ComponentAccessor<EntityStore> entityAccessor) {
        if (Boolean.TRUE.equals(taleguard$denied.get())) {
            return;
        }
        ItemUtils.interactivelyPickupItem(playerRefEntity, drop, pos, entityAccessor);
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
