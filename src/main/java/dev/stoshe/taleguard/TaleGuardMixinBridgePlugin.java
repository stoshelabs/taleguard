package dev.stoshe.taleguard;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import dev.stoshe.taleguard.runtime.MixinTargetAudit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Bridge plugin entrypoint for runtime diagnostics/state flags.
 */
public final class TaleGuardMixinBridgePlugin extends JavaPlugin {
    private static final String ACTIVE_PROPERTY = "taleguard.bridge.active";
    private static final String BOOTSTRAP_READY_PROPERTY = "taleguard.bridge.bootstrap.ready";
    /** How many redirects were checked, and how many of those will not apply. */
    private static final String AUDIT_PROPERTY = "taleguard.redirects.audit";
    /** The guards that will not apply here, semicolon separated. Empty when clean. */
    private static final String AUDIT_BROKEN_PROPERTY = "taleguard.redirects.broken";

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
            MixinTargetAudit.Result result = new MixinTargetAudit(getClass().getClassLoader())
                    .audit(MIXIN_PACKAGE, MIXIN_CLASSES);

            System.setProperty(AUDIT_PROPERTY, result.broken().size() + "/" + result.checked());

            if (result.isClean()) {
                System.setProperty(AUDIT_BROKEN_PROPERTY, "");
                getLogger().at(Level.INFO).log("TaleGuard: " + MixinTargetAudit.summarise(result));
                return;
            }

            StringBuilder names = new StringBuilder();
            for (MixinTargetAudit.Broken broken : result.broken()) {
                if (names.length() > 0) {
                    names.append(';');
                }
                names.append(broken.mixin());
                getLogger().at(Level.SEVERE).log("TaleGuard guard will not apply: " + broken);
            }
            System.setProperty(AUDIT_BROKEN_PROPERTY, names.toString());
            getLogger().at(Level.SEVERE).log("TaleGuard: " + MixinTargetAudit.summarise(result)
                    + ". The flags behind them are NOT enforced on this server.");
        } catch (Throwable t) {
            // The audit is a diagnostic; it must never be the reason the bridge fails.
            getLogger().at(Level.WARNING).log("TaleGuard could not audit its redirects", t);
        }
    }

    private static final String MIXIN_PACKAGE = "dev.stoshe.taleguard.mixin";

    /** Mirrors taleguard.mixin.json. */
    private static final java.util.List<String> MIXIN_CLASSES = java.util.List.of(
            "HarvestCropInteractionMixin",
            "ChangeFarmingStageInteractionMixin",
            "PlayerItemEntityPickupSystemMixin",
            "SeatingInteractionMixin",
            "BlockHarvestUtilsMixin",
            "BuilderToolsPacketHandlerMixin",
            "ChangeStateInteractionMixin",
            "CommandManagerMixin",
            "CycleBlockGroupInteractionMixin",
            "DeathItemDropMixin",
            "DefaultFluidTickerMixin",
            "ExplosionBlockDamageMixin",
            "FiniteFluidTickerMixin",
            "NPCPluginSpawnMixin",
            "PlaceFluidInteractionMixin",
            "SpawnMarkerEntityMixin",
            "WorldSpawnJobSystemsMixin");
}
