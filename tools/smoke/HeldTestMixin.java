package dev.vercim.dragit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Temporary: lets the smoke test pretend the left mouse button is down. */
@Mixin(dev.vercim.dragit.drag.GuiCompat.class)
public abstract class HeldTestMixin {
	@Inject(method = "leftButtonHeld", at = @At("HEAD"), cancellable = true)
	private static void dragit$held(CallbackInfoReturnable<Boolean> cir) {
		if (!dev.vercim.dragit.drag.DragItSmoke.REAL_HELD) {
			cir.setReturnValue(dev.vercim.dragit.drag.DragItSmoke.HELD);
		}
	}
}
