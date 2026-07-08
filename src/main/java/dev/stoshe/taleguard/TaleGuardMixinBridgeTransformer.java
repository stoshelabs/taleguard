package dev.stoshe.taleguard;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import dev.stoshe.taleguard.runtime.LaunchEnvironment;
import dev.stoshe.taleguard.runtime.MixinBootstrapService;
import dev.stoshe.taleguard.runtime.MixinServiceImpl;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TaleGuardMixinBridgeTransformer implements ClassTransformer {
    public static final String ACTIVE_PROPERTY = "taleguard.bridge.active";
    public static final String BOOTSTRAP_READY_PROPERTY = "taleguard.bridge.bootstrap.ready";
    public static final String MIXINS_LOADED_PROPERTY = "taleguard.mixins.loaded";
    public static final String MIXINS_LOADED_LIST_PROPERTY = "taleguard.mixins.loaded.list";
    private static volatile boolean transformErrorLogged;
    private static volatile boolean transformerMissingLogged;
    private static volatile boolean runtimeClasspathInjected;
    private static final Set<String> SEEN_TARGET_LOGS = ConcurrentHashMap.newKeySet();
    private static final Set<String> MIXIN_TARGETS = Set.of(
            "com.hypixel.hytale.builtin.adventure.farming.interactions.HarvestCropInteraction",
            "com.hypixel.hytale.builtin.adventure.farming.interactions.ChangeFarmingStageInteraction",
            "com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem",
            "com.hypixel.hytale.builtin.mounts.interactions.SeatingInteraction"
    );

    public TaleGuardMixinBridgeTransformer() {
        System.setProperty(ACTIVE_PROPERTY, "true");
        if (!System.getProperties().containsKey("taleguard.hook.registry")) {
            System.getProperties().put("taleguard.hook.registry", new java.util.concurrent.ConcurrentHashMap<String, Object>());
        }
        LaunchEnvironment.create(this.getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
        LaunchEnvironment.get().captureRuntimeLoader(Thread.currentThread().getContextClassLoader());
        ensureBridgeRuntimeVisible(LaunchEnvironment.get().getRuntimeLoader());
        System.setProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager");
        System.setProperty("mixin.bootstrapService", MixinBootstrapService.class.getName());
        System.setProperty("mixin.service", MixinServiceImpl.class.getName());
        MixinBootstrap.init();
        Mixins.addConfiguration("taleguard.mixin.json");
        MixinServiceImpl.changePhase(MixinEnvironment.Phase.INIT);
        MixinServiceImpl.changePhase(MixinEnvironment.Phase.DEFAULT);
        System.setProperty(BOOTSTRAP_READY_PROPERTY, "true");
        String version = readVersion();
        System.out.println("[TaleGuard] Loaded as EARLY PLUGIN (ClassTransformer) (v" + version + ").");
        System.out.println("[TaleGuard] Early mixin bootstrap is active.");
    }

    @Override
    public int priority() {
        return -90; // Load early
    }

    @Nullable
    @Override
    public byte[] transform(@Nonnull String name, @Nonnull String path, @Nonnull byte[] bytes) {
        if (LaunchEnvironment.get().getRuntimeLoader() == null) {
            LaunchEnvironment.get().captureRuntimeLoader(Thread.currentThread().getContextClassLoader());
        }
        ensureBridgeRuntimeVisible(LaunchEnvironment.get().getRuntimeLoader());
        if (MixinServiceImpl.transformer == null) {
            if (!transformerMissingLogged) {
                transformerMissingLogged = true;
                System.err.println("[TaleGuard] Mixin transformer is not initialized; classes are passing through unmodified.");
            }
            return bytes;
        }
        String originalName = normalizeClassName(name);
        String transformedName = normalizeClassName(path);
        if (transformedName == null || transformedName.isBlank()) {
            transformedName = originalName;
        }
        try {
            byte[] transformed = MixinServiceImpl.transformer.transformClassBytes(originalName, transformedName, bytes);
            if (MIXIN_TARGETS.contains(originalName) && SEEN_TARGET_LOGS.add(originalName)) {
                boolean changed = transformed != null && transformed != bytes;
                System.out.println("[TaleGuard] Target seen: " + originalName + " changed=" + changed);
            }
            if (transformed != null && transformed != bytes) {
                markMixinTransformApplied(originalName);
            }
            return transformed != null ? transformed : bytes;
        } catch (Throwable t) {
            if (!transformErrorLogged) {
                transformErrorLogged = true;
                System.err.println("[TaleGuard] Failed to apply mixin transform for class: "
                        + originalName + " (path=" + path + ")");
                t.printStackTrace(System.err);
            }
            return bytes;
        }
    }

    private String normalizeClassName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.replace('/', '.');
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        return normalized;
    }

    private static void markMixinTransformApplied(String targetClassName) {
        System.setProperty(MIXINS_LOADED_PROPERTY, "true");
        if (targetClassName == null || targetClassName.isBlank()) {
            return;
        }
        synchronized (TaleGuardMixinBridgeTransformer.class) {
            String current = System.getProperty(MIXINS_LOADED_LIST_PROPERTY, "");
            if (current.isBlank()) {
                System.setProperty(MIXINS_LOADED_LIST_PROPERTY, targetClassName);
                return;
            }
            String token = "," + targetClassName + ",";
            String wrapped = "," + current + ",";
            if (!wrapped.contains(token)) {
                System.setProperty(MIXINS_LOADED_LIST_PROPERTY, current + "," + targetClassName);
            }
        }
    }

    private void ensureBridgeRuntimeVisible(ClassLoader runtimeLoader) {
        if (runtimeClasspathInjected || runtimeLoader == null) {
            return;
        }
        try {
            CodeSource source = TaleGuardMixinBridgeTransformer.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return;
            }
            URL self = source.getLocation();
            boolean injected = injectIntoLoaderChain(runtimeLoader, self);
            if (injected) {
                runtimeClasspathInjected = true;
                boolean callbackVisible = runtimeLoader.getResource(
                        "org/spongepowered/asm/mixin/injection/callback/CallbackInfo.class") != null;
                System.out.println("[TaleGuard] Runtime classpath patched with bridge jar: " + self
                        + " callbackVisible=" + callbackVisible);
            }
        } catch (Throwable t) {
            System.err.println("[TaleGuard] Failed to patch runtime classpath: " + t.getMessage());
        }
    }

    private boolean injectIntoLoaderChain(ClassLoader loader, URL jarUrl) {
        ClassLoader current = loader;
        boolean injectedAny = false;
        while (current != null) {
            if (current instanceof URLClassLoader urlLoader) {
                try {
                    var addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    addUrl.setAccessible(true);
                    addUrl.invoke(urlLoader, jarUrl);
                    injectedAny = true;
                } catch (Throwable ignored) {
                }
            }
            current = current.getParent();
        }
        return injectedAny;
    }

    private String readVersion() {
        Package pkg = TaleGuardMixinBridgeTransformer.class.getPackage();
        if (pkg != null) {
            String implVersion = pkg.getImplementationVersion();
            if (implVersion != null && !implVersion.isBlank()) {
                return implVersion;
            }
        }
        return "1.0.0";
    }
}
