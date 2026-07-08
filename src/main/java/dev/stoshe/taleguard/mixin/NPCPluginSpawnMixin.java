package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NPCPlugin.class)
public class NPCPluginSpawnMixin {

    @Redirect(method = "spawnEntity(Lcom/hypixel/hytale/component/Store;ILorg/joml/Vector3d;Lorg/joml/Vector3f;Lcom/hypixel/hytale/server/core/asset/type/model/config/Model;Lcom/hypixel/hytale/function/consumer/TriConsumer;Lcom/hypixel/hytale/function/consumer/TriConsumer;)Lit/unimi/dsi/fastutil/Pair;", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/component/Store;addEntity(Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/component/AddReason;)Lcom/hypixel/hytale/component/Ref;"), require = 0)
    private Ref<EntityStore> taleguard$redirectAddEntity(Store<EntityStore> store, Holder<EntityStore> holder, AddReason reason) {
        TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
        Vector3d pos = transform != null ? transform.getPosition() : null;
        String worldName = "";
        Object external = store.getExternalData();
        if (external instanceof EntityStore es && es.getWorld() != null) {
            worldName = es.getWorld().getName();
        }

        if (pos != null && !taleguard$checkAllowedNoNotify(null, worldName, (int) Math.floor(pos.x), (int) Math.floor(pos.y),
                (int) Math.floor(pos.z), "MOB_SPAWN")) {
            return null;
        }
        return store.addEntity(holder, reason);
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
