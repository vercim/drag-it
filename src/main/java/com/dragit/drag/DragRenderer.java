package com.dragit.drag;

import com.dragit.DragItSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Draws the drop placeholder / blocked marker and the lifted entry. Pure drawing; all state lives in {@link DragController}. */
final class DragRenderer {
	private DragRenderer() {
	}

	/** The mod's lang files are only loaded when Fabric API is present, so the text carries its own fallback instead of showing the raw key. */
	private static Text lockedHint() {
		String language = MinecraftClient.getInstance().options.language;
		String fallback = language != null && language.startsWith("ru") ? "Закреплено" : "Fixed position";
		return Text.translatableWithFallback("drag-it.hint.locked", fallback);
	}

	static void render(DragController c, DrawContext context, float delta) {
		for (int i = 0; i < 2; i++) {
			drawIndicator(c, context, i);
		}
		if (c.phase == DragController.Phase.DRAGGING || c.phase == DragController.Phase.SETTLING) {
			drawGhost(c, context, delta);
		}
		if (c.phase == DragController.Phase.DRAGGING && c.drop.blocked()) {
			MinecraftClient client = MinecraftClient.getInstance();
			MatrixStack matrices = context.getMatrices();
			matrices.push();
			matrices.translate(0, 0, 300);
			context.drawTextWithShadow(client.textRenderer, lockedHint(), (int) c.pointerX + 10, (int) c.pointerY + 8, DragItSettings.BLOCKED_COLOR);
			matrices.pop();
		}
	}

	private static void drawIndicator(DragController c, DrawContext context, int index) {
		DragController.Indicator indicator = c.indicators[index];
		if (indicator.alpha <= 0.005) {
			return;
		}
		PackListWidget list = c.list(index);
		int color = indicator.blocked ? DragItSettings.BLOCKED_COLOR : DragItSettings.INDICATOR_COLOR;
		double a = indicator.alpha;

		int right = ListGeometry.cardRight(list);
		context.enableScissor(list.getX(), list.getY(), right, list.getBottom());
		int x = list.getRowLeft() - 2;
		int width = ListGeometry.cardWidth(list);
		int top = (int) Math.round(ListGeometry.originY(list) + indicator.y);
		if (indicator.blocked) {
			context.fill(x, top - 3, x + width, top - 1, scale(color, a));
		} else {
			int height = ListGeometry.entryHeight(list);
			context.fill(x, top - 2, x + width, top + height + 2, scale(color, 0.16 * a));
			context.drawBorder(x, top - 2, width, height + 4, scale(color, 0.85 * a));
		}
		context.disableScissor();

		// Frame around the list that would receive the pack (the scrollbar stays outside of it).
		context.drawBorder(list.getX(), list.getY(), right - list.getX(), list.getHeight(), scale(color, 0.55 * a));
	}

	private static void drawGhost(DragController c, DrawContext context, float delta) {
		int listIndex = c.phase == DragController.Phase.DRAGGING ? c.srcList : c.settleList;
		PackListWidget list = c.list(listIndex);
		int row = ListGeometry.indexOf(list, c.packId);
		PackListWidget.ResourcePackEntry entry = ListGeometry.find(list, c.packId);
		if (row < 0 || entry == null) {
			return;
		}

		int width = ListGeometry.cardWidth(list);
		int height = ListGeometry.entryHeight(list);
		int ix = (int) Math.floor(c.ghostX);
		int iy = (int) Math.floor(c.ghostY);
		float fx = (float) (c.ghostX - ix);
		float fy = (float) (c.ghostY - iy);
		float lift = (float) Math.max(0, Math.min(1, c.lift));

		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(0, 0, 200);
		float scale = 1.0F + (DragItSettings.GHOST_SCALE - 1.0F) * lift;
		float pivotX = (float) (c.ghostX - 2 + width / 2.0);
		float pivotY = (float) (c.ghostY + height / 2.0);
		matrices.translate(pivotX, pivotY, 0);
		matrices.scale(scale, scale, 1);
		matrices.translate(-pivotX, -pivotY, 0);
		matrices.translate(fx, fy, 0);

		int left = ix - 2;
		int right = left + width;
		int count = c.phase == DragController.Phase.DRAGGING ? c.dragIds.size() : 1;
		for (int layer = Math.min(count - 1, 2); layer >= 1; layer--) {
			// A dragged selection is shown as a small stack of cards under the one the cursor holds.
			int o = Math.round(layer * 4 * lift);
			context.fill(left + o, iy - 2 + o, right + o, iy + height + 2 + o, DragItSettings.GHOST_BACKGROUND);
			context.drawBorder(left + o, iy - 2 + o, width, height + 4, scale(DragItSettings.INDICATOR_COLOR, 0.35 + 0.35 * lift));
		}
		for (int i = 5; i >= 1; i--) {
			int alpha = (int) (lift * 22);
			context.fill(left - i + 2, iy - 2 - i + 4, right + i + 2, iy + height + 2 + i + 4, alpha << 24);
		}
		int accent = c.phase == DragController.Phase.DRAGGING && c.drop.blocked()
			? DragItSettings.BLOCKED_COLOR
			: DragItSettings.INDICATOR_COLOR;
		context.fill(left, iy - 2, right, iy + height + 2, DragItSettings.GHOST_BACKGROUND);
		context.drawBorder(left, iy - 2, width, height + 4, scale(accent, 0.35 + 0.55 * lift));

		DragController.setDrawingGhost(true);
		try {
			entry.render(context, row, iy, ix, list.getRowWidth(), height, -1000, -1000, false, delta);
		} finally {
			DragController.setDrawingGhost(false);
		}
		if (count > 1) {
			// Number of dragged packs, in the top-right corner of the card.
			TextRenderer font = MinecraftClient.getInstance().textRenderer;
			String label = String.valueOf(count);
			int badgeWidth = font.getWidth(label) + 8;
			int bx = right - badgeWidth - 3;
			int by = iy - 2 + 3;
			context.fill(bx, by, bx + badgeWidth, by + 12, scale(DragItSettings.INDICATOR_COLOR, lift));
			context.drawText(font, label, bx + 4, by + 2, 0xFFFFFFFF, false);
		}
		matrices.pop();
	}

	private static int scale(int argb, double factor) {
		int alpha = (int) ((argb >>> 24) * Math.max(0.0, Math.min(1.0, factor)));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}
}
