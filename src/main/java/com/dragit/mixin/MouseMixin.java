package com.dragit.mixin;

import com.dragit.drag.DragController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Button events are the one place where a press can be held back from vanilla (so the arrows only fire on a real
 * click, not when a drag starts on them) and where a release is seen no matter what is under the cursor.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Shadow
	@Final
	private MinecraftClient client;
	@Shadow
	private double x;
	@Shadow
	private double y;

	@Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
	private void dragit$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
		if (!(this.client.currentScreen instanceof PackScreen screen) || this.client.getOverlay() != null) {
			return;
		}
		Window w = this.client.getWindow();
		if (window != w.getHandle()) {
			return;
		}
		double guiX = this.x * w.getScaledWidth() / w.getWidth();
		double guiY = this.y * w.getScaledHeight() / w.getHeight();
		if (DragController.of(screen).onMouseButton(guiX, guiY, button, action == GLFW.GLFW_PRESS, mods)) {
			ci.cancel();
		}
	}
}
