package dev.vercim.dragit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vercim.dragit.drag.ControllerHolder;
import dev.vercim.dragit.drag.DragController;
import dev.vercim.dragit.mixin.accessor.PackEntryAccessor;
import dev.vercim.dragit.mixin.accessor.TransferableSelectionListAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class PackEntryMixin {
	/**
	 * Wraps the whole row render: hides the entry that is currently lifted, shifts sliding entries by their animated offset
	 * (through the matrix stack, so sub-pixel motion is smooth) and removes vanilla's hover decoration while entries move.
	 */
	@WrapMethod(method = "render")
	private void dragit$render(
		GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered,
		float tickDelta, Operation<Void> original
	) {
		TransferableSelectionList widget = ((PackEntryAccessor) (Object) this).dragit$getWidget();
		if (DragController.isDrawingGhost()
			|| !(((TransferableSelectionListAccessor) (Object) widget).dragit$getScreen() instanceof ControllerHolder holder)) {
			original.call(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta);
			return;
		}

		DragController controller = holder.dragit$getController();
		String name = ((TransferableSelectionList.PackEntry) (Object) this).getPackId();
		if (controller.isHidden(name)) {
			return;
		}
		boolean showHover = hovered && !controller.suppressHover();
		float dy = controller.offsetFor(widget, name, index);
		PoseStack matrices = context.pose();
		if (dy != 0) {
			matrices.pushPose();
			matrices.translate(0, dy, 0);
		}
		// Highlights are drawn under the row content, with vanilla's geometry (stops short of the scrollbar).
		int left = x - 2;
		int right = DragController.highlightRight(widget);
		int top = y - 2;
		int bottom = y + entryHeight + 2;

		float single = controller.selectionAlpha(widget, name);
		if (single > 0.004F) {
			int border = widget.isFocused() ? 0xFFFFFFFF : 0xFF808080; // vanilla's focused / unfocused frame colour
			dragit$frame(context, left, top, right, bottom, border, 0xFF000000, single);
		}

		// The multi-selection is one animated outline per run of rows; this row draws the slice of it that lies on it.
		controller.drawMultiSelection(context, widget, index, left, top, right, bottom);
		original.call(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, showHover, tickDelta);
		if (dy != 0) {
			matrices.popPose();
		}
	}

	@Unique
	private static void dragit$frame(GuiGraphics context, int left, int top, int right, int bottom, int border, int fill, float alpha) {
		int edge = dragit$withAlpha(border, alpha);
		context.fill(left, top, right, bottom, dragit$withAlpha(fill, alpha));
		context.fill(left, top, left + 1, bottom, edge);
		context.fill(right - 1, top, right, bottom, edge);
		context.fill(left + 1, top, right - 1, top + 1, edge);
		context.fill(left + 1, bottom - 1, right - 1, bottom, edge);
	}

	@Unique
	private static int dragit$withAlpha(int argb, float factor) {
		int alpha = Math.round((argb >>> 24) * Math.max(0F, Math.min(1F, factor)));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}
}
