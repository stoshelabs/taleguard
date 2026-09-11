package dev.stoshe.taleguard;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import dev.stoshe.taleguard.runtime.MixinTargetAudit;
import dev.stoshe.taleguard.runtime.RedirectAudit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Bridge plugin entrypoint for runtime diagnostics/state flags.
 *
 * <p>The server only starts this when the jar is also in {@code mods/}. From {@code earlyplugins/} alone it runs
 * just {@link TaleGuardMixinBridgeTransformer}, which audits the redirects itself.</p>
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
        auditRedirects();
        getLogger().at(Level.INFO).log("TaleGuard bridge early plugin loaded.");
    }

    /**
     * Reports which guards will not apply on this server.
     *
     * <p>Every redirect but one carries {@code require = 0}, so Mixin skips a
     * stale one without a word: the bridge loads, calls itself healthy, and
     * protects nothing. That is the failure this plugin cannot afford to have
     * happen quietly, so the targets are checked against the running server and
     * anything missing is named — at SEVERE, because a protection bridge with dead
     * guards is an incident, not a warning.</p>
     *
     * <p>Reporting rather than refusing is deliberate. Failing to load would take
     * every working guard down with the broken ones, and on Hytale 0.6 a core
     * plugin that fails to start stops the server outright.</p>
     */
    private void auditRedirects() {
        try {
            MixinTargetAudit.Result result = RedirectAudit.run(getClass().getClassLoader());
            if (result.isClean()) {
                getLogger().at(Level.INFO).log("TaleGuard: " + MixinTargetAudit.summarise(result));
                return;
            }
            for (MixinTargetAudit.Broken broken : result.broken()) {
                getLogger().at(Level.SEVERE).log("TaleGuard guard will not apply: " + broken);
            }
            getLogger().at(Level.SEVERE).log("TaleGuard: " + MixinTargetAudit.summarise(result)
                    + ". The flags behind them are NOT enforced on this server.");
        } catch (Throwable t) {
            // The audit is a diagnostic; it must never be the reason the bridge fails.
            getLogger().at(Level.WARNING).log("TaleGuard could not audit its redirects", t);
        }
    }
}
