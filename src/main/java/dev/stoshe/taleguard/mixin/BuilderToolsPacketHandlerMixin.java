package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolExtrudeAction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolLineAction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolOnUseInteraction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPasteClipboard;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolStackArea;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.sneakythrow.consumer.ThrowableTriConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(com.hypixel.hytale.builtin.buildertools.BuilderToolsPacketHandler.class)
public abstract class BuilderToolsPacketHandlerMixin {

    @Redirect(method = "handleBuilderToolOnUseInteraction", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/builtin/buildertools/BuilderToolsPlugin;addToQueue(Lcom/hypixel/hytale/server/core/entity/entities/Player;Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/sneakythrow/consumer/ThrowableTriConsumer;)V"), require = 0)
    private void taleguard$guardOnUse(Player player, PlayerRef playerRef, ThrowableTriConsumer op,
            BuilderToolOnUseInteraction packet) {
        if (taleguard$deny(playerRef, packet.x, packet.y, packet.z)) {
            return;
        }
        BuilderToolsPlugin.addToQueue(player, playerRef, op);
    }

    @Redirect(method = "handleBuilderToolPasteClipboard", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/builtin/buildertools/BuilderToolsPlugin;addToQueue(Lcom/hypixel/hytale/server/core/entity/entities/Player;Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/sneakythrow/consumer/ThrowableTriConsumer;)V"), require = 0)
    private void taleguard$guardPaste(Player player, PlayerRef playerRef, ThrowableTriConsumer op,
            BuilderToolPasteClipboard packet) {
        if (taleguard$deny(playerRef, packet.x, packet.y, packet.z)) {
            return;
        }
        BuilderToolsPlugin.addToQueue(player, playerRef, op);
    }

    @Redirect(method = "handleBuilderToolExtrudeAction", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/builtin/buildertools/BuilderToolsPlugin;addToQueue(Lcom/hypixel/hytale/server/core/entity/entities/Player;Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/sneakythrow/consumer/ThrowableTriConsumer;)V"), require = 0)
    private void taleguard$guardExtrude(Player player, PlayerRef playerRef, ThrowableTriConsumer op,
            BuilderToolExtrudeAction packet) {
        if (taleguard$deny(playerRef, packet.x, packet.y, packet.z)) {
            return;
        }
        BuilderToolsPlugin.addToQueue(player, playerRef, op);
    }

    @Redirect(method = "handleBuilderToolLineAction", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/builtin/buildertools/BuilderToolsPlugin;addToQueue(Lcom/hypixel/hytale/server/core/entity/entities/Player;Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/sneakythrow/consumer/ThrowableTriConsumer;)V"), require = 0)
    private void taleguard$guardLine(Player player, PlayerRef playerRef, ThrowableTriConsumer op,
            BuilderToolLineAction packet) {
        if (taleguard$deny(playerRef, packet.xStart, packet.yStart, packet.zStart)
                || taleguard$deny(playerRef, packet.xEnd, packet.yEnd, packet.zEnd)) {
            return;
        }
        BuilderToolsPlugin.addToQueue(player, playerRef, op);
    }

    @Redirect(method = "handleBuilderToolStackArea", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/builtin/buildertools/BuilderToolsPlugin;addToQueue(Lcom/hypixel/hytale/server/core/entity/entities/Player;Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/sneakythrow/consumer/ThrowableTriConsumer;)V"), require = 0)
    private void taleguard$guardStack(Player player, PlayerRef playerRef, ThrowableTriConsumer op,
            BuilderToolStackArea packet) {
        BlockPosition min = packet.selectionMin;
        BlockPosition max = packet.selectionMax;
        if (min != null && taleguard$deny(playerRef, min.x, min.y, min.z)) {
            return;
        }
        if (max != null && taleguard$deny(playerRef, max.x, max.y, max.z)) {
            return;
        }
        BuilderToolsPlugin.addToQueue(player, playerRef, op);
    }

    private boolean taleguard$deny(PlayerRef playerRef, int x, int y, int z) {
        if (playerRef == null) {
            return false;
        }
        World world = null;
        if (playerRef.getWorldUuid() != null) {
            world = Universe.get().getWorld(playerRef.getWorldUuid());
        }
        if (world == null) {
            return false;
        }
        String worldName = world.getName();
        UUID uuid = playerRef.getUuid();
        return !taleguard$checkAllowed(uuid, worldName, x, y, z, "BUILDER");
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
