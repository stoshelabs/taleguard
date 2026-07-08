package dev.stoshe.taleguard.runtime;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

public final class MixinBootstrapService implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "TaleGuard";
    }

    @Override
    public String getServiceClassName() {
        return "dev.stoshe.taleguard.runtime.MixinServiceImpl";
    }

    @Override
    public void bootstrap() {
        // no-op
    }
}
