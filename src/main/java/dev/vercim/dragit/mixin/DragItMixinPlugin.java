package dev.vercim.dragit.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gates the mixins whose targets exist only in some Minecraft versions: the SDL drop-hover hooks need
 * {@code com.mojang.blaze3d.platform.SDLEventHandler} (26.3+; older versions run on GLFW, which has no file-drag hover
 * events at all). Without this gate Mixin would fail to find the target and refuse to start the game.
 */
public final class DragItMixinPlugin implements IMixinConfigPlugin {
	private static final String SDL_ANCHOR = "com.mojang.blaze3d.platform.SDLEventHandler";
	private static final Set<String> SDL_MIXINS = Set.of(
		"dev.vercim.dragit.mixin.SDLEventHandlerMixin",
		"dev.vercim.dragit.mixin.WindowMixin"
	);

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (SDL_MIXINS.contains(mixinClassName)) {
			return classExists(SDL_ANCHOR);
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	private static boolean classExists(String name) {
		try {
			Class.forName(name, false, DragItMixinPlugin.class.getClassLoader());
			return true;
		} catch (Throwable ignored) { // NoClassDefFoundError included: a superclass of the probe may be missing too
			return false;
		}
	}
}
