package dev.stoshe.taleguard.api;

import java.util.UUID;

public interface ProtectionHook {
    boolean isAllowed(UUID playerUuid, String worldName, double x, double y, double z, String type);
    void notifyDenied(UUID playerUuid, String worldName, double x, double y, double z, String type);
}
