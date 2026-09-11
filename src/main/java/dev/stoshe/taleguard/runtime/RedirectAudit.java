package dev.stoshe.taleguard.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@link MixinTargetAudit} over every mixin in {@code taleguard.mixin.json} and publishes the result as system
 * properties, so any plugin can read which guards are live on this server.
 *
 * <p>It is called from the early-plugin transformer, because that is the only part of TaleGuard a server starts
 * from {@code earlyplugins/}: the JavaPlugin entry point is only loaded when the jar is also in {@code mods/}, so an
 * audit that lived there alone never ran on a normal install. The mixin list is read from the same config Mixin
 * loads rather than mirrored by hand — a mirrored list is one more place a new mixin can be forgotten.</p>
 */
public final class RedirectAudit {

    /** How many redirects will not apply, over how many were checked — {@code "0/28"} when clean. */
    public static final String AUDIT_PROPERTY = "taleguard.redirects.audit";
    /** The mixins whose guards will not apply here, semicolon separated. Empty when clean. */
    public static final String AUDIT_BROKEN_PROPERTY = "taleguard.redirects.broken";

    private static final String CONFIG = "taleguard.mixin.json";

    private RedirectAudit() {
    }

    /** Audits every configured mixin against the server visible to {@code loader} and publishes the result. */
    public static MixinTargetAudit.Result run(ClassLoader loader) throws Exception {
        JsonObject config;
        try (InputStream in = loader.getResourceAsStream(CONFIG)) {
            if (in == null) {
                throw new IllegalStateException(CONFIG + " is not on the classpath");
            }
            config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String mixinPackage = config.get("package").getAsString();
        List<String> mixins = new ArrayList<>();
        for (JsonElement element : config.getAsJsonArray("mixins")) {
            mixins.add(element.getAsString());
        }

        MixinTargetAudit.Result result = new MixinTargetAudit(loader).audit(mixinPackage, mixins);

        StringBuilder names = new StringBuilder();
        for (MixinTargetAudit.Broken broken : result.broken()) {
            if (names.length() > 0) {
                names.append(';');
            }
            names.append(broken.mixin());
        }
        System.setProperty(AUDIT_PROPERTY, result.broken().size() + "/" + result.checked());
        System.setProperty(AUDIT_BROKEN_PROPERTY, names.toString());
        return result;
    }
}
