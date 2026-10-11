package dev.vercim.dragit.mixin;

import dev.vercim.dragit.drag.ControllerHolder;
import dev.vercim.dragit.drag.DragController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every {@link PackSelectionScreen} (resource packs and data packs alike) its {@link DragController}, and makes sure a
 * gesture never survives a re-layout or closing the screen.
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen implements ControllerHolder {
	@Unique
	private DragController dragit$controller;

	protected PackSelectionScreenMixin(Component title) {
		super(title);
	}

	@Override
	public DragController dragit$getController() {
		if (this.dragit$controller == null) {
			this.dragit$controller = new DragController((PackSelectionScreen) (Object) this);
		}
		return this.dragit$controller;
	}

	/** Called on first init, on window resize and when returning from a dialog. */
	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void dragit$onLayout(CallbackInfo ci) {
		this.dragit$getController().onLayoutChanged();
	}

	@Inject(method = "onClose", at = @At("HEAD"))
	private void dragit$onClose(CallbackInfo ci) {
		this.dragit$getController().reset();
	}
}
