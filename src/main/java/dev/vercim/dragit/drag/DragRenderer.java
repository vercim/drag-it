package dev.vercim.dragit.drag;

import dev.vercim.dragit.DragItSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;

/** Draws the drop placeholder / blocked marker and the lifted entry. Pure drawing; all state lives in {@link DragController}. */
final class DragRenderer {
	private DragRenderer() {
	}

	static void render(DragController c, GuiGraphics context, float delta) {
		for (int i = 0; i < 2; i++) {
			drawIndicator(c, context, i);
		}
		if (c.phase == DragController.Phase.DRAGGING || c.phase == DragController.Phase.SETTLING) {
			drawGhost(c, context, delta);
		}
	}

	private static void drawIndicator(DragController c, GuiGraphics context, int index) {
		DragController.Indicator indicator = c.indicators[index];
		if (indicator.alpha <= 0.005) {
			return;
		}
		TransferableSelectionList list = c.list(index);
		boolean blocked = indicator.block != DropTarget.Block.NONE;
		int color = blocked ? DragItSettings.BLOCKED_COLOR : DragItSettings.INDICATOR_COLOR;
		double a = indicator.alpha;

		int right = ListGeometry.frameRight(list);
		context.enableScissor(ListGeometry.left(list), ListGeometry.top(list), right, ListGeometry.bottom(list));
		int x = ListGeometry.cellLeft(list);
		int width = ListGeometry.cardWidth(list);
		int top = (int) Math.round(ListGeometry.originY(list) + indicator.y);
		if (indicator.block == DropTarget.Block.PINNED) {
			context.fill(x, top - 3, x + width, top - 1, scale(color, a));
		} else if (!blocked) {
			int height = ListGeometry.entryHeight(list);
			context.fill(x, top - 2, x + width, top + height + 2, scale(color, 0.16 * a));
			GuiCompat.outline(context, x, top - 2, width, height + 4, scale(color, 0.85 * a));
		}
		context.disableScissor();

		// Frame around the list that would receive the pack (the scrollbar stays outside of it).
		GuiCompat.outline(context, ListGeometry.left(list), ListGeometry.top(list), right - ListGeometry.left(list), ListGeometry.height(list), scale(color, 0.55 * a));
	}

	private static void drawGhost(DragController c, GuiGraphics context, float delta) {
		boolean settling = c.phase == DragController.Phase.SETTLING;
		if (settling) {
			// Gliding cards may head for rows that are scrolled out of view; keep them inside the lists' area.
			TransferableSelectionList first = c.list(0);
			TransferableSelectionList second = c.list(1);
			int pad = DragItSettings.COLUMN_PADDING;
			context.enableScissor(
				Math.min(ListGeometry.left(first), ListGeometry.left(second)) - pad, Math.min(ListGeometry.top(first), ListGeometry.top(second)),
				Math.max(ListGeometry.frameRight(first), ListGeometry.frameRight(second)) + pad, Math.max(ListGeometry.bottom(first), ListGeometry.bottom(second))
			);
		}
		// The rest of a dropped stack glides below the card the cursor held.
		for (DragController.Follower f : c.followers) {
			drawCard(c, context, delta, c.list(f.list), f.name, f.x, f.y, f.mix, 1);
		}
		int listIndex = c.phase == DragController.Phase.DRAGGING ? c.srcList : c.settleList;
		int count = c.phase == DragController.Phase.DRAGGING ? c.dragIds.size() : 1;
		drawCard(c, context, delta, c.list(listIndex), c.packId, c.ghostX, c.ghostY, c.blockedMix[0], count);
		if (settling) {
			context.disableScissor();
		}
	}

	/** One lifted card; {@code count} above 1 stacks cards under it and puts the number of dragged packs on it. */
	private static void drawCard(
		DragController c, GuiGraphics context, float delta, TransferableSelectionList list, String name,
		double gx, double gy, double blocked, int count
	) {
		int row = ListGeometry.indexOf(list, name);
		TransferableSelectionList.PackEntry entry = ListGeometry.find(list, name);
		if (row < 0 || entry == null) {
			return;
		}

		int width = ListGeometry.cardWidth(list);
		int height = ListGeometry.entryHeight(list);
		int ix = (int) Math.floor(gx);
		int iy = (int) Math.floor(gy);
		float fx = (float) (gx - ix);
		float fy = (float) (gy - iy);
		float lift = (float) Math.max(0, Math.min(1, c.lift));

		GuiCompat.push(context);
		GuiCompat.raise(context);
		float scale = 1.0F + (DragItSettings.GHOST_SCALE - 1.0F) * lift;
		float pivotX = (float) (gx - 2 + width / 2.0);
		float pivotY = (float) (gy + height / 2.0);
		GuiCompat.translate(context, pivotX, pivotY);
		GuiCompat.scale(context, scale, scale);
		GuiCompat.translate(context, -pivotX, -pivotY);
		GuiCompat.translate(context, fx, fy);

		int left = ix - 2;
		int right = left + width;
		for (int layer = Math.min(count - 1, 2); layer >= 1; layer--) {
			// A dragged selection is shown as a small stack of cards under the one the cursor holds.
			int o = Math.round(layer * 4 * lift);
			double layerMix = c.blockedMix[layer];
			int layerAccent = mix(DragItSettings.INDICATOR_COLOR, DragItSettings.BLOCKED_COLOR, layerMix);
			int layerBackground = mix(DragItSettings.GHOST_BACKGROUND, DragItSettings.GHOST_BLOCKED_BACKGROUND, layerMix);
			context.fill(left + o, iy - 2 + o, right + o, iy + height + 2 + o, layerBackground);
			GuiCompat.outline(context, left + o, iy - 2 + o, width, height + 4, scale(layerAccent, 0.35 + 0.35 * lift));
		}
		for (int i = 5; i >= 1; i--) {
			int alpha = (int) (lift * 22);
			context.fill(left - i + 2, iy - 2 - i + 4, right + i + 2, iy + height + 2 + i + 4, alpha << 24);
		}
		int accent = mix(DragItSettings.INDICATOR_COLOR, DragItSettings.BLOCKED_COLOR, blocked);
		int background = mix(DragItSettings.GHOST_BACKGROUND, DragItSettings.GHOST_BLOCKED_BACKGROUND, blocked);
		context.fill(left, iy - 2, right, iy + height + 2, background);
		GuiCompat.outline(context, left, iy - 2, width, height + 4, scale(accent, 0.35 + 0.55 * lift));

		DragController.setDrawingGhost(true);
		try {
			GuiCompat.drawEntry(context, entry, row, ix, iy, list.getRowWidth(), height, delta);
		} finally {
			DragController.setDrawingGhost(false);
		}
		if (count > 1) {
			// Number of dragged packs, in the top-right corner of the card.
			GuiCompat.raise(context); // older versions order by depth: the entry's title text would otherwise cover the badge
			Font font = Minecraft.getInstance().font;
			String label = String.valueOf(count);
			int badgeWidth = font.width(label) + 8;
			int bx = right - badgeWidth - 3;
			int by = iy - 2 + 3;
			context.fill(bx, by, bx + badgeWidth, by + 12, scale(DragItSettings.INDICATOR_COLOR, lift));
			GuiCompat.text(context, font, label, bx + 4, by + 2, 0xFFFFFFFF);
		}
		GuiCompat.pop(context);
	}

	/**
	 * The slice of the multi-selection outline that falls on one row. {@code left..right} and {@code top..bottom} are the
	 * row's whole cell (cells of neighbouring rows touch), so a band spanning several rows is drawn as one continuous shape.
	 */
	static void drawSelection(GuiGraphics context, SelectionBands bands, int list, int index, int left, int top, int right, int bottom) {
		int height = bottom - top;
		for (SelectionBands.Band band : bands.bands(list)) {
			double from = Math.max(band.top, index);
			double to = Math.min(band.bottom, index + 1);
			if (band.alpha <= 0.004 || to <= from) {
				continue;
			}
			int y0 = top + (int) Math.round((from - index) * height);
			int y1 = top + (int) Math.round((to - index) * height);
			if (y1 <= y0) {
				continue;
			}
			int edge = scale(DragItSettings.MULTI_SELECT_BORDER, band.alpha);
			context.fill(left, y0, right, y1, scale(DragItSettings.MULTI_SELECT_FILL, band.alpha));
			context.fill(left, y0, left + 1, y1, edge);
			context.fill(right - 1, y0, right, y1, edge);
			if (band.top >= index) { // the top edge of the band lies in this row
				context.fill(left + 1, y0, right - 1, y0 + 1, edge);
			}
			if (band.bottom <= index + 1) { // and its bottom edge
				context.fill(left + 1, y1 - 1, right - 1, y1, edge);
			}
		}
	}

	/** Linear blend of two ARGB colours, {@code t} in 0..1. */
	private static int mix(int from, int to, double t) {
		t = Math.max(0.0, Math.min(1.0, t));
		int result = 0;
		for (int shift = 24; shift >= 0; shift -= 8) {
			int a = (from >>> shift) & 0xFF;
			int b = (to >>> shift) & 0xFF;
			result |= (int) Math.round(a + (b - a) * t) << shift;
		}
		return result;
	}

	private static int scale(int argb, double factor) {
		int alpha = (int) ((argb >>> 24) * Math.max(0.0, Math.min(1.0, factor)));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}
}
