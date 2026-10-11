package com.dragit.mixin;

import com.dragit.dev.SelfTest;
import com.dragit.drag.DragController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code PackScreen} does not declare {@code render} or {@code keyPressed} itself, and Mixin cannot inject into inherited
 * methods, so these hooks live on {@link Screen} and bail out immediately for every screen that is not a {@link PackScreen}.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void dragit$beforeRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if ((Object) this instanceof PackScreen screen) {
			DragController.of(screen).beginFrame();
			if (SelfTest.ENABLED) {
				SelfTest.onPackScreenFrame(screen);
			}
		} else if (SelfTest.ENABLED) {
			SelfTest.onOtherScreenFrame((Screen) (Object) this);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void dragit$afterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if ((Object) this instanceof PackScreen screen) {
			DragController.of(screen).endFrame(context, delta);
			if (SelfTest.ENABLED) {
				SelfTest.afterPackScreenFrame(screen);
			}
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void dragit$onKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof PackScreen screen)) {
			return;
		}
		DragController controller = DragController.of(screen);
		if (keyCode == GLFW.GLFW_KEY_ESCAPE ? controller.onEscape() : controller.onKey(keyCode, modifiers)) {
			cir.setReturnValue(true); // Esc cancels the drag (the screen stays open); Ctrl+A / Enter act on the selection
		}
	}
}
