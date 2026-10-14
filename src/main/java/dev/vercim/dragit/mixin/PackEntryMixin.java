package dev.vercim.dragit.mixin;

//? if >=1.21.1 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//?}
import dev.vercim.dragit.drag.ControllerHolder;
import dev.vercim.dragit.drag.DragController;
import dev.vercim.dragit.drag.GuiCompat;
import dev.vercim.dragit.mixin.accessor.PackEntryAccessor;
import dev.vercim.dragit.mixin.accessor.TransferableSelectionListAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//? if <1.21.1 {
/*import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class PackEntryMixin {
	// What dragit$begin decided about a row.
	@Unique
	private static final int DRAW_PLAIN = 0; // vanilla draws the row as it is
	@Unique
	private static final int DRAW_SKIP = 1; // the row is lifted: nothing is drawn
	@Unique
	private static final int DRAW_SHIFTED = 2; // the matrix is translated and has to be popped after the row

	/**
	 * Hooks the whole row render: hides the entry that is currently lifted, shifts sliding entries by their animated offset
	 * (through the matrix stack, so sub-pixel motion is smooth) and removes vanilla's hover decoration while entries move.
	 * Up to 1.21.1 vanilla hands the row's position to {@code render}; from 1.21.10 {@code extractContent} reads it from the entry.
	 * From 1.21.1 the row is wrapped with MixinExtras. Forge 1.20.1 does not ship it (and the mod carries no libraries), so there
	 * the same steps run as plain injections: the hover flag is replaced on entry, the rest runs at the head and at every return.
	 */
	//? if >=1.21.10 {
	@WrapMethod(method = "extractContent")
	private void dragit$render(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta, Operation<Void> original) {
		TransferableSelectionList.PackEntry self = (TransferableSelectionList.PackEntry) (Object) this;
		TransferableSelectionList widget = ((PackEntryAccessor) (Object) this).dragit$getWidget();
		int state = dragit$begin(context, widget, DragController.rowOf(widget, self), self.getContentX(), self.getContentY(), self.getContentHeight());
		if (state != DRAW_SKIP) {
			original.call(context, mouseX, mouseY, dragit$hover(widget, hovered), tickDelta);
			dragit$end(context, state);
		}
	}
	//?} else if >=1.21.1 {
	/*@WrapMethod(method = "extractRenderState")
	private void dragit$render(
		GuiGraphicsExtractor context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered,
		float tickDelta, Operation<Void> original
	) {
		TransferableSelectionList widget = ((PackEntryAccessor) (Object) this).dragit$getWidget();
		int state = dragit$begin(context, widget, index, x, y, entryHeight);
		if (state != DRAW_SKIP) {
			original.call(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, dragit$hover(widget, hovered), tickDelta);
			dragit$end(context, state);
		}
	}
	*///?} else {
	/*@Unique
	private int dragit$state = DRAW_PLAIN;

	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private boolean dragit$hoverFlag(boolean hovered) {
		return dragit$hover(((PackEntryAccessor) (Object) this).dragit$getWidget(), hovered);
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void dragit$renderBefore(
		GuiGraphicsExtractor context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered,
		float tickDelta, CallbackInfo ci
	) {
		TransferableSelectionList widget = ((PackEntryAccessor) (Object) this).dragit$getWidget();
		this.dragit$state = dragit$begin(context, widget, index, x, y, entryHeight);
		if (this.dragit$state == DRAW_SKIP) {
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void dragit$renderAfter(
		GuiGraphicsExtractor context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered,
		float tickDelta, CallbackInfo ci
	) {
		dragit$end(context, this.dragit$state);
		this.dragit$state = DRAW_PLAIN;
	}

	// Vanilla 1.20.1 answers a click on the body of a row with false, so the list never selects the row and the selection
	// frame never shows (only the keyboard selects). From 1.20.2 the click falls through to a handler that answers true and
	// the list selects the row; this does the same. (Line comments: this branch sits inside a block comment.)
	@Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
	private void dragit$selectOnClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (button == GuiCompat.MOUSE_LEFT && !cir.getReturnValueZ()) {
			cir.setReturnValue(true);
		}
	}
	*///?}

	/**
	 * Draws what lies under the row (the selection highlights) and shifts the matrix when the row is sliding.
	 *
	 * @param x left edge of the row's content
	 * @param y top edge of the row's content
	 * @return a {@code DRAW_*} value: how the row itself is to be drawn
	 */
	@Unique
	private int dragit$begin(GuiGraphicsExtractor context, TransferableSelectionList widget, int index, int x, int y, int entryHeight) {
		if (DragController.isDrawingGhost()
			|| !(((TransferableSelectionListAccessor) (Object) widget).dragit$getScreen() instanceof ControllerHolder holder)) {
			return DRAW_PLAIN;
		}

		DragController controller = holder.dragit$getController();
		String name = ((TransferableSelectionList.PackEntry) (Object) this).getPackId();
		if (controller.isHidden(name)) {
			return DRAW_SKIP;
		}
		float dy = controller.offsetFor(widget, name, index);
		int state = DRAW_PLAIN;
		if (dy != 0) {
			GuiCompat.push(context);
			GuiCompat.translate(context, 0, dy);
			state = DRAW_SHIFTED;
		}
		// Highlights are drawn under the row content, with vanilla's geometry (stops short of the scrollbar).
		int left = x - 2;
		int right = DragController.highlightRight(widget);
		int top = y - 2;
		int bottom = y + entryHeight + 2;

		// Both selections are animated outlines (vanilla's single frame, and one per run of rows for the multi-selection);
		// this row draws the slice of each that lies on it.
		controller.drawSingleSelection(context, widget, index, left, top, right, bottom);
		controller.drawMultiSelection(context, widget, index, left, top, right, bottom);
		return state;
	}

	/** Undoes what {@link #dragit$begin} set up once the row has been drawn. */
	@Unique
	private static void dragit$end(GuiGraphicsExtractor context, int state) {
		if (state == DRAW_SHIFTED) {
			GuiCompat.pop(context);
		}
	}

	/** The hover flag the row is drawn with: vanilla's, unless the mod suppresses hover while entries move. */
	@Unique
	private boolean dragit$hover(TransferableSelectionList widget, boolean hovered) {
		if (!hovered || DragController.isDrawingGhost()
			|| !(((TransferableSelectionListAccessor) (Object) widget).dragit$getScreen() instanceof ControllerHolder holder)) {
			return hovered;
		}
		return !holder.dragit$getController().suppressHover();
	}
}
