package dev.vercim.dragit.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.vercim.dragit.drag.DragController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
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
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private double xpos;
	@Shadow
	private double ypos;

	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
	private void dragit$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
		if (!(this.minecraft.screen instanceof PackSelectionScreen screen) || this.minecraft.getOverlay() != null) {
			return;
		}
		Window w = this.minecraft.getWindow();
		if (window != w.getWindow()) {
			return;
		}
		double guiX = this.xpos * w.getGuiScaledWidth() / w.getScreenWidth();
		double guiY = this.ypos * w.getGuiScaledHeight() / w.getScreenHeight();
		if (DragController.of(screen).onMouseButton(guiX, guiY, button, action == GLFW.GLFW_PRESS, mods)) {
			ci.cancel();
		}
	}
}
