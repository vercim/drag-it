package dev.vercim.dragit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.vercim.dragit.drag.ControllerHolder;
import dev.vercim.dragit.mixin.accessor.TransferableSelectionListAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TransferableSelectionList.class)
public abstract class TransferableSelectionListMixin {
	/**
	 * Vanilla draws the selection frame instantly at the row's layout position. The mod draws it itself from the entry's
	 * render hook (same geometry and colours, but fading in/out and following the sliding entry), so vanilla's is skipped.
	 */
	@WrapMethod(method = "renderSelection")
	private void dragit$skipVanillaHighlight(
		GuiGraphics context, int y, int entryWidth, int entryHeight, int borderColor, int fillColor, Operation<Void> original
	) {
		if (((TransferableSelectionListAccessor) (Object) this).dragit$getScreen() instanceof ControllerHolder) {
			return;
		}
		original.call(context, y, entryWidth, entryHeight, borderColor, fillColor);
	}
}
