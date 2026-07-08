package dev.stoshe.taleguard;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Bridge plugin entrypoint for runtime diagnostics/state flags.
 */
public final class TaleGuardMixinBridgePlugin extends JavaPlugin {
    private static final String ACTIVE_PROPERTY = "taleguard.bridge.active";
    private static final String BOOTSTRAP_READY_PROPERTY = "taleguard.bridge.bootstrap.ready";

    public TaleGuardMixinBridgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();
        System.setProperty(ACTIVE_PROPERTY, "true");
        System.setProperty(BOOTSTRAP_READY_PROPERTY, "true");
        try {
            this.getCommandRegistry().registerCommand((com.hypixel.hytale.server.core.command.system.AbstractCommand) new dev.stoshe.taleguard.commands.TaleGuardDebugCommand());
            getLogger().at(Level.INFO).log("TaleGuard bridge debug command registered successfully.");
        } catch (Throwable t) {
            getLogger().at(Level.SEVERE).log("Failed to register TaleGuard debug command", t);
        }
        getLogger().at(Level.INFO).log("TaleGuard bridge early plugin loaded.");
    }
}
