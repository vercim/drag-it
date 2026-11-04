package dev.vercim.dragit.mixin;

import dev.vercim.dragit.drag.ControllerHolder;
import dev.vercim.dragit.drag.DragController;
import java.nio.file.Path;
import java.util.List;
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

	// Called on first init, on window resize and when returning from a dialog. 1.20.1 has no repositionElements: a resize runs init again.
	//? if >=1.21.1 {
	@Inject(method = "repositionElements", at = @At("TAIL"))
	//?} else {
	/*@Inject(method = "init", at = @At("TAIL"))
	*///?}
	private void dragit$onLayout(CallbackInfo ci) {
		this.dragit$getController().onLayoutChanged();
	}

	@Inject(method = "onClose", at = @At("HEAD"))
	private void dragit$onClose(CallbackInfo ci) {
		this.dragit$getController().reset();
	}

	/**
	 * Files dropped on the window: vanilla copies them into the pack folder (after its confirmation) and reloads. The
	 * controller remembers where the cursor was, so a drop over the selected list can also enable the new packs there.
	 */
	@Inject(method = "onFilesDrop", at = @At("HEAD"))
	private void dragit$onFilesDrop(List<Path> paths, CallbackInfo ci) {
		this.dragit$getController().onFilesDrop(paths);
	}

	/**
	 * Vanilla's reload of the pack lists (the directory watcher and the file drop both end in one); declared by every
	 * version the mod supports. A pending file drop is applied here, once the new packs exist.
	 */
	@Inject(method = "reload", at = @At("TAIL"))
	private void dragit$onPacksReloaded(CallbackInfo ci) {
		this.dragit$getController().onPacksReloaded();
	}
}
