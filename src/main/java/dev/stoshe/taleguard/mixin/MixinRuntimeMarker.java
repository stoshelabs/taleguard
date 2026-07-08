package dev.stoshe.taleguard.mixin;

final class MixinRuntimeMarker {
    static final String MIXINS_LOADED_PROPERTY = "taleguard.mixins.loaded";
    static final String MIXINS_LOADED_LIST_PROPERTY = "taleguard.mixins.loaded.list";

    private MixinRuntimeMarker() {
    }

    static void markLoaded() {
        System.setProperty(MIXINS_LOADED_PROPERTY, "true");
        String caller = resolveCaller();
        if (caller == null || caller.isBlank()) {
            return;
        }
        synchronized (MixinRuntimeMarker.class) {
            String current = System.getProperty(MIXINS_LOADED_LIST_PROPERTY, "");
            if (current.isBlank()) {
                System.setProperty(MIXINS_LOADED_LIST_PROPERTY, caller);
                return;
            }
            String token = "," + caller + ",";
            String wrapped = "," + current + ",";
            if (!wrapped.contains(token)) {
                System.setProperty(MIXINS_LOADED_LIST_PROPERTY, current + "," + caller);
            }
        }
    }

    private static String resolveCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : stack) {
            String cn = ste.getClassName();
            if (cn == null) {
                continue;
            }
            if (!cn.equals(MixinRuntimeMarker.class.getName())
                    && cn.startsWith("dev.stoshe.taleguard.mixin.")) {
                return cn.substring("dev.stoshe.taleguard.mixin.".length());
            }
        }
        return null;
    }
}
