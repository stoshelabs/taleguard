package dev.stoshe.taleguard.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaleGuardDebugCommand extends CommandBase {

    public TaleGuardDebugCommand() {
        super("tgdebug", "TaleGuard Mixin and Protection Debug Command");
        this.requirePermission("taleguard.admin");
        this.addAliases(new String[]{"taleguarddebug"});
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        String input = context.getInputString() == null ? "" : context.getInputString().trim();
        String[] args = input.isBlank() ? new String[]{} : input.split("\\s+");

        if (args.length > 0 && "test".equalsIgnoreCase(args[0])) {
            handleTest(context, args);
            return;
        }

        handleStatus(context);
    }

    private void handleStatus(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("§b=== TaleGuard Debug Status ==="));
        
        boolean isActive = "true".equals(System.getProperty("taleguard.bridge.active"));
        boolean isBootstrap = "true".equals(System.getProperty("taleguard.bridge.bootstrap.ready"));
        context.sendMessage(Message.raw("§fBridge Active: " + (isActive ? "§aYes" : "§cNo")));
        context.sendMessage(Message.raw("§fBootstrap Ready: " + (isBootstrap ? "§aYes" : "§cNo")));

        String loadedList = System.getProperty("taleguard.mixins.loaded.list", "");
        context.sendMessage(Message.raw("§fLoaded Mixin Targets:"));
        if (loadedList.isBlank()) {
            context.sendMessage(Message.raw(" §7- None (No mixin targets transformed yet)"));
        } else {
            for (String target : loadedList.split(",")) {
                context.sendMessage(Message.raw(" §a✔ §f" + target));
            }
        }

        Map<?, ?> registry = (Map<?, ?>) System.getProperties().get("taleguard.hook.registry");
        context.sendMessage(Message.raw("§fRegistered Protection Hooks:"));
        if (registry == null || registry.isEmpty()) {
            context.sendMessage(Message.raw(" §7- None (No hooks registered in registry)"));
        } else {
            for (Map.Entry<?, ?> entry : registry.entrySet()) {
                Object hook = entry.getValue();
                String key = String.valueOf(entry.getKey());
                String className = hook != null ? hook.getClass().getName() : "null";
                context.sendMessage(Message.raw(" §e- §fKey: §b" + key + "§f | Class: §7" + className));
            }
        }
        context.sendMessage(Message.raw("§7Use '/tgdebug test <player_name_or_uuid> <world> <x> <y> <z> <type>' to simulate check."));
    }

    private void handleTest(@Nonnull CommandContext context, String[] args) {
        if (args.length < 7) {
            context.sendMessage(Message.raw("§cUsage: /tgdebug test <player_name_or_uuid> <world> <x> <y> <z> <type>"));
            context.sendMessage(Message.raw("§cExample: /tgdebug test Gustavo islands 8.6 82 17.8 CROP_HARVEST"));
            return;
        }

        String playerStr = args[1];
        String worldName = args[2];
        double x, y, z;
        try {
            x = Double.parseDouble(args[3]);
            y = Double.parseDouble(args[4]);
            z = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            context.sendMessage(Message.raw("§cInvalid coordinates (x, y, z must be numbers)."));
            return;
        }
        String type = args[6];

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(playerStr);
        } catch (IllegalArgumentException e) {
            // For debug command, generate a deterministic UUID from name if not a valid UUID
            playerUuid = UUID.nameUUIDFromBytes(playerStr.getBytes());
            context.sendMessage(Message.raw("§7Parsed player name as deterministic UUID: §e" + playerUuid));
        }

        context.sendMessage(Message.raw("§b=== Simulating Protection Check ==="));
        context.sendMessage(Message.raw(String.format("§fTarget: §e%s §fat §7(%s, %.2f, %.2f, %.2f)§f type §b%s",
                playerUuid, worldName, x, y, z, type)));

        Map<?, ?> registry = (Map<?, ?>) System.getProperties().get("taleguard.hook.registry");
        if (registry == null || registry.isEmpty()) {
            context.sendMessage(Message.raw("§cNo Hook Registry found! Allowed by default (true)."));
            return;
        }

        List<Object> hooks = new ArrayList<>(registry.values());
        try {
            hooks.sort((h1, h2) -> {
                int p1 = getPriority(h1);
                int p2 = getPriority(h2);
                return Integer.compare(p1, p2);
            });
        } catch (Throwable t) {
            context.sendMessage(Message.raw("§eWarning: Failed to sort hooks: " + t.getMessage()));
        }

        boolean overallAllowed = true;
        for (Object hook : hooks) {
            if (hook == null) continue;
            String hookClassName = hook.getClass().getName();
            try {
                Method method = hook.getClass().getMethod("isAllowed", 
                    UUID.class, String.class, double.class, double.class, double.class, String.class);
                boolean allowed = (boolean) method.invoke(hook, playerUuid, worldName, x, y, z, type);
                int priority = getPriority(hook);
                
                context.sendMessage(Message.raw(String.format(" §e- §fHook: §7%s §f(Priority: %d) -> %s",
                        hookClassName, priority, (allowed ? "§aALLOWED" : "§cDENIED"))));
                
                if (!allowed) {
                    overallAllowed = false;
                }
            } catch (Exception e) {
                context.sendMessage(Message.raw(" §c[Error] §fHook §7" + hookClassName + "§f threw exception: §c" + e.toString()));
            }
        }

        context.sendMessage(Message.raw("§fOverall Result: " + (overallAllowed ? "§aALLOWED (true)" : "§cDENIED (false)")));
    }

    private static int getPriority(Object hook) {
        if (hook == null) return 0;
        try {
            Method method = hook.getClass().getMethod("getPriority");
            return (int) method.invoke(hook);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
