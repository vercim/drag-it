package dev.vercim.dragit.mixin;

import dev.vercim.dragit.drag.DragController;
import dev.vercim.dragit.drag.GuiCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
//? if >=1.21.10 {
/*import net.minecraft.client.input.KeyEvent;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code PackSelectionScreen} does not declare {@code render} or {@code keyPressed} itself, and Mixin cannot inject into inherited
 * methods, so these hooks live on {@link Screen} and bail out immediately for every screen that is not a {@link PackSelectionScreen}.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void dragit$beforeRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if ((Object) this instanceof PackSelectionScreen screen) {
			DragController.of(screen).beginFrame();
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void dragit$afterRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if ((Object) this instanceof PackSelectionScreen screen) {
			DragController.of(screen).endFrame(context, delta);
		}
	}

	//? if >=1.21.10 {
	/*@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void dragit$onKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		dragit$handleKey(event.key(), event.modifiers(), cir);
	}
	*///?} else {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void dragit$onKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		dragit$handleKey(keyCode, modifiers, cir);
	}
	//?}

	@Unique
	private void dragit$handleKey(int keyCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof PackSelectionScreen screen)) {
			return;
		}
		DragController controller = DragController.of(screen);
		if (keyCode == GuiCompat.KEY_ESCAPE ? controller.onEscape() : controller.onKey(keyCode, modifiers)) {
			cir.setReturnValue(true); // Esc cancels the drag (the screen stays open); Ctrl+A / Enter act on the selection
		}
	}
}
