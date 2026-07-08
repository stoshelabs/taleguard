package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.server.spawning.SpawnTestResult;
import com.hypixel.hytale.server.spawning.SpawningContext;
import com.hypixel.hytale.server.spawning.world.system.WorldSpawnJobSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldSpawnJobSystems.class)
public class WorldSpawnJobSystemsMixin {

    @Redirect(method = "trySpawn", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/spawning/SpawningContext;canSpawn()Lcom/hypixel/hytale/server/spawning/SpawnTestResult;"), require = 0)
    private static SpawnTestResult taleguard$redirectCanSpawnNoArg(SpawningContext context) {
        String taleguard$wn = taleguard$worldName(context);
        if (context != null && taleguard$wn != null && !taleguard$checkAllowedNoNotify(null,
                taleguard$wn,
                (int) Math.floor(context.xSpawn),
                (int) Math.floor(context.ySpawn),
                (int) Math.floor(context.zSpawn), "MOB_SPAWN")) {
            return SpawnTestResult.FAIL_INVALID_POSITION;
        }
        return context.canSpawn();
    }

    @Redirect(method = "trySpawn", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/spawning/SpawningContext;canSpawn(ZZ)Lcom/hypixel/hytale/server/spawning/SpawnTestResult;"), require = 0)
    private static SpawnTestResult taleguard$redirectCanSpawn2Arg(SpawningContext context, boolean testOverlapBlocks,
            boolean testOverlapEntities) {
        String taleguard$wn = taleguard$worldName(context);
        if (context != null && taleguard$wn != null && !taleguard$checkAllowedNoNotify(null,
                taleguard$wn,
                (int) Math.floor(context.xSpawn),
                (int) Math.floor(context.ySpawn),
                (int) Math.floor(context.zSpawn), "MOB_SPAWN")) {
            return SpawnTestResult.FAIL_INVALID_POSITION;
        }
        return context.canSpawn(testOverlapBlocks, testOverlapEntities);
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

    private static String taleguard$worldName(Object context) {
        if (context == null) {
            return null;
        }
        try {
            java.lang.reflect.Field f = context.getClass().getDeclaredField("world");
            f.setAccessible(true);
            Object w = f.get(context);
            if (w != null) {
                Object name = w.getClass().getMethod("getName").invoke(w);
                return name != null ? name.toString() : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
