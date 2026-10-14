package dev.vercim.dragit.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary: starts the smoke test the first time the title screen is built. The same hook works on every loader and version. */
@Mixin(TitleScreen.class)
public abstract class SmokeStartTestMixin {
	@Inject(method = "init", at = @At("HEAD"))
	private void dragit$startSmoke(CallbackInfo ci) {
		dev.vercim.dragit.drag.DragItSmoke.startOnce();
	}
}
