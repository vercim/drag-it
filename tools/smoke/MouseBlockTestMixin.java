package dev.vercim.dragit.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary: while the smoke test runs, the real mouse is ignored so it cannot disturb the scripted pointer. */
@Mixin(MouseHandler.class)
public abstract class MouseBlockTestMixin {
	@Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
	private void dragit$blockMove(CallbackInfo ci) {
		if (dev.vercim.dragit.drag.DragItSmoke.ACTIVE) {
			ci.cancel();
		}
	}

	@Inject(method = {"onPress", "onButton"}, at = @At("HEAD"), cancellable = true, require = 0)
	private void dragit$blockButton(CallbackInfo ci) {
		if (dev.vercim.dragit.drag.DragItSmoke.ACTIVE && !dev.vercim.dragit.drag.DragItSmoke.SYNTH) {
			ci.cancel();
		}
	}
}
