package dev.comfyfluffy.caustica.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Register optional targets only for the release whose storage and startup ABI we use. */
public final class CausticaMixinPlugin implements IMixinConfigPlugin {
    @Override public List<String> getMixins() {
        boolean supported = FabricLoader.getInstance().getModContainer("voxy")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("0.2.19-beta"))
                .orElse(false);
        return supported ? List.of("VoxyClientMixin", "VoxyConfigMixin") : List.of();
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
