package dev.vercim.dragit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.vercim.dragit.drag.ControllerHolder;
import dev.vercim.dragit.drag.DragController;
import dev.vercim.dragit.drag.GuiCompat;
import dev.vercim.dragit.mixin.accessor.PackEntryAccessor;
import dev.vercim.dragit.mixin.accessor.TransferableSelectionListAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class PackEntryMixin {
	/**
	 * Wraps the whole row render: hides the entry that is currently lifted, shifts sliding entries by their animated offset
	 * (through the matrix stack, so sub-pixel motion is smooth) and removes vanilla's hover decoration while entries move.
	 * Up to 1.21.1 vanilla hands the row's position to {@code render}; from 1.21.10 {@code extractContent} reads it from the entry.
	 */
	//? if >=1.21.10 {
	@WrapMethod(method = "extractContent")
	private void dragit$render(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta, Operation<Void> original) {
		TransferableSelectionList.PackEntry self = (TransferableSelectionList.PackEntry) (Object) this;
		TransferableSelectionList widget = ((PackEntryAccessor) (Object) this).dragit$getWidget();
		dragit$draw(
			context, widget, DragController.rowOf(widget, self), self.getContentX(), self.getContentY(), self.getContentHeight(),
			hovered, original, new Object[]{context, mouseX, mouseY, hovered, tickDelta}, 3
		);
	}
	//?} else {
	/*@WrapMethod(method = "extractRenderState")
	private void dragit$render(
		GuiGraphicsExtractor context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered,
		float tickDelta, Operation<Void> original
	) {
		TransferableSelectionList widget = ((PackEntryAccessor) (Object) this).dragit$getWidget();
		dragit$draw(
			context, widget, index, x, y, entryHeight,
			hovered, original, new Object[]{context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta}, 8
		);
	}
	*///?}

	/**
	 * @param x            left edge of the row's content
	 * @param y            top edge of the row's content
	 * @param args         arguments of the wrapped call; the {@code hovered} flag at {@code hoveredSlot} is replaced when hover is suppressed
	 */
	@Unique
	private void dragit$draw(
		GuiGraphicsExtractor context, TransferableSelectionList widget, int index, int x, int y, int entryHeight, boolean hovered,
		Operation<Void> original, Object[] args, int hoveredSlot
	) {
		if (DragController.isDrawingGhost()
			|| !(((TransferableSelectionListAccessor) (Object) widget).dragit$getScreen() instanceof ControllerHolder holder)) {
			original.call(args);
			return;
		}

		DragController controller = holder.dragit$getController();
		String name = ((TransferableSelectionList.PackEntry) (Object) this).getPackId();
		if (controller.isHidden(name)) {
			return;
		}
		args[hoveredSlot] = hovered && !controller.suppressHover();
		float dy = controller.offsetFor(widget, name, index);
		if (dy != 0) {
			GuiCompat.push(context);
			GuiCompat.translate(context, 0, dy);
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
		original.call(args);
		if (dy != 0) {
			GuiCompat.pop(context);
		}
	}

	@Unique
	private static void dragit$frame(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int border, int fill, float alpha) {
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
