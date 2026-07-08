package dev.stoshe.taleguard.mixin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.ParserContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {

    @Redirect(method = "runCommand", at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/command/system/AbstractCommand;acceptCall(Lcom/hypixel/hytale/server/core/command/system/CommandSender;Lcom/hypixel/hytale/server/core/command/system/ParserContext;Lcom/hypixel/hytale/server/core/command/system/ParseResult;)Ljava/util/concurrent/CompletableFuture;"), require = 0)
    private CompletableFuture<Void> taleguard$guardCommand(AbstractCommand command, CommandSender sender,
            ParserContext context, ParseResult parseResult, CommandSender senderArg, String rawCommand,
            AbstractCommand commandArg, CompletableFuture<Void> completionFuture) {
        UUID senderUuid = null;
        try {
            if (sender != null) {
                senderUuid = sender.getUuid();
            }
        } catch (Throwable ignored) {
        }
        if (!taleguard$isCommandAllowed(senderUuid, rawCommand != null ? rawCommand : "")) {
            String deny = taleguard$commandDenialMessage();
            if (deny == null || deny.isBlank()) {
                deny = "You don't have permission to do this.";
            }
            sender.sendMessage(Message.raw(deny));
            return CompletableFuture.completedFuture(null);
        }
        return command.acceptCall(sender, context, parseResult);
    }

    private static boolean taleguard$isCommandAllowed(UUID senderUuid, String rawCommand) {
        try {
            java.util.Map<?, ?> registry = (java.util.Map<?, ?>) System.getProperties().get("taleguard.hook.registry");
            if (registry == null || registry.isEmpty()) {
                return true;
            }
            for (Object hook : registry.values()) {
                try {
                    java.lang.reflect.Method method = hook.getClass().getMethod("isCommandAllowed", UUID.class, String.class);
                    boolean allowed = (boolean) method.invoke(hook, senderUuid, rawCommand);
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

    private static String taleguard$commandDenialMessage() {
        try {
            java.util.Map<?, ?> registry = (java.util.Map<?, ?>) System.getProperties().get("taleguard.hook.registry");
            if (registry == null || registry.isEmpty()) {
                return null;
            }
            for (Object hook : registry.values()) {
                try {
                    java.lang.reflect.Method method = hook.getClass().getMethod("getCommandDenialMessage");
                    Object r = method.invoke(hook);
                    if (r != null) {
                        return String.valueOf(r);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
