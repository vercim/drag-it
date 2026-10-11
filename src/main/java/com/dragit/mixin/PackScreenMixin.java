package com.dragit.mixin;

import com.dragit.drag.ControllerHolder;
import com.dragit.drag.DragController;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every {@link PackScreen} (resource packs and data packs alike) its {@link DragController}, and makes sure a
 * gesture never survives a re-layout or closing the screen.
 */
@Mixin(PackScreen.class)
public abstract class PackScreenMixin extends Screen implements ControllerHolder {
	@Unique
	private DragController dragit$controller;

	protected PackScreenMixin(Text title) {
		super(title);
	}

	@Override
	public DragController dragit$getController() {
		if (this.dragit$controller == null) {
			this.dragit$controller = new DragController((PackScreen) (Object) this);
		}
		return this.dragit$controller;
	}

	/** Called on first init, on window resize and when returning from a dialog. */
	@Inject(method = "initTabNavigation", at = @At("TAIL"))
	private void dragit$onLayout(CallbackInfo ci) {
		this.dragit$getController().onLayoutChanged();
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void dragit$onClose(CallbackInfo ci) {
		if (com.dragit.dev.SelfTest.ENABLED) {
			com.dragit.DragIt.LOGGER.info("[SelfTest] PackScreen.close()", new Throwable());
		}
		this.dragit$getController().reset();
	}
}
