package dev.vercim.dragit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.vercim.dragit.DragItSettings;
import dev.vercim.dragit.drag.ControllerHolder;
import dev.vercim.dragit.drag.DragController;
import dev.vercim.dragit.mixin.accessor.PackListWidgetAccessor;
import dev.vercim.dragit.mixin.accessor.ResourcePackEntryAccessor;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PackListWidget.ResourcePackEntry.class)
public abstract class ResourcePackEntryMixin {
	/**
	 * Wraps the whole row render: hides the entry that is currently lifted, shifts sliding entries by their animated offset
	 * (through the matrix stack, so sub-pixel motion is smooth) and removes vanilla's hover decoration while entries move.
	 */
	@WrapMethod(method = "render")
	private void dragit$render(
		DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered,
		float tickDelta, Operation<Void> original
	) {
		PackListWidget widget = ((ResourcePackEntryAccessor) (Object) this).dragit$getWidget();
		if (DragController.isDrawingGhost()
			|| !(((PackListWidgetAccessor) (Object) widget).dragit$getScreen() instanceof ControllerHolder holder)) {
			original.call(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta);
			return;
		}

		DragController controller = holder.dragit$getController();
		String name = ((PackListWidget.ResourcePackEntry) (Object) this).getName();
		if (controller.isHidden(name)) {
			return;
		}
		boolean showHover = hovered && !controller.suppressHover();
		float dy = controller.offsetFor(widget, name, index);
		MatrixStack matrices = context.getMatrices();
		if (dy != 0) {
			matrices.push();
			matrices.translate(0, dy, 0);
		}
		// Highlights are drawn under the row content, with vanilla's geometry (stops short of the scrollbar) and a fade.
		int left = x - 2;
		int right = DragController.highlightRight(widget);
		int top = y - 2;
		int bottom = y + entryHeight + 2;

		float single = controller.selectionAlpha(widget, name);
		if (single > 0.004F) {
			int border = widget.isFocused() ? 0xFFFFFFFF : 0xFF808080; // vanilla's focused / unfocused frame colour
			dragit$frame(context, left, top, right, bottom, border, 0xFF000000, single, single, single);
		}

		float multi = controller.multiAlpha(name);
		if (multi > 0.004F) {
			// Neighbouring selected rows share one outline: the edge between two rows only shows the difference of their
			// fade levels, so it is drawn exactly once, and the outer edges appear only where a run of rows starts/ends.
			List<PackListWidget.ResourcePackEntry> rows = widget.children();
			float prev = index > 0 ? controller.multiAlpha(rows.get(index - 1).getName()) : 0F;
			float next = index + 1 < rows.size() ? controller.multiAlpha(rows.get(index + 1).getName()) : 0F;
			dragit$frame(context, left, top, right, bottom, DragItSettings.MULTI_SELECT_BORDER, DragItSettings.MULTI_SELECT_FILL,
				multi, multi - prev, multi - next);
		}
		original.call(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, showHover, tickDelta);
		if (dy != 0) {
			matrices.pop();
		}
	}

	@Unique
	private static void dragit$frame(
		DrawContext context, int left, int top, int right, int bottom, int border, int fill, float alpha, float topAlpha, float bottomAlpha
	) {
		int edge = dragit$withAlpha(border, alpha);
		context.fill(left, top, right, bottom, dragit$withAlpha(fill, alpha));
		context.fill(left, top, left + 1, bottom, edge);
		context.fill(right - 1, top, right, bottom, edge);
		context.fill(left + 1, top, right - 1, top + 1, dragit$withAlpha(border, topAlpha));
		context.fill(left + 1, bottom - 1, right - 1, bottom, dragit$withAlpha(border, bottomAlpha));
	}

	@Unique
	private static int dragit$withAlpha(int argb, float factor) {
		int alpha = Math.round((argb >>> 24) * Math.max(0F, Math.min(1F, factor)));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}
}
