package dev.vercim.dragit.drag;

import dev.vercim.dragit.mixin.accessor.AbstractScrollAreaAccessor;
import dev.vercim.dragit.mixin.accessor.AbstractSelectionListAccessor;
import dev.vercim.dragit.mixin.accessor.PackEntryAccessor;
import dev.vercim.dragit.mixin.accessor.TransferableSelectionListAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
//? if <1.21.10 {
/*import net.minecraft.util.Mth;
*///?}

/**
 * Row geometry of a vanilla {@link TransferableSelectionList}, recomputed from live widget state on every call (GUI scale / resize safe).
 *
 * <p>Rows are counted over the packs only. From 1.21.10 the list also holds a header as its first child; {@link #entries} leaves it
 * out, so an index here is the same number as an index into the model's list.
 */
final class ListGeometry {
	private ListGeometry() {
	}

	/** The pack rows of the list, in order. */
	static List<TransferableSelectionList.PackEntry> entries(TransferableSelectionList list) {
		//? if >=1.21.10 {
		List<TransferableSelectionList.PackEntry> packs = new ArrayList<>(list.children().size());
		for (TransferableSelectionList.Entry entry : list.children()) {
			if (entry instanceof TransferableSelectionList.PackEntry pack) {
				packs.add(pack);
			}
		}
		return packs;
		//?} else {
		/*return list.children();
		*///?}
	}

	/** Vanilla's single selection, or {@code null}. */
	static TransferableSelectionList.PackEntry selected(TransferableSelectionList list) {
		//? if >=1.21.10 {
		return list.getSelected() instanceof TransferableSelectionList.PackEntry pack ? pack : null;
		//?} else {
		/*return list.getSelected();
		*///?}
	}

	/** Pitch of the pack rows. */
	static int itemHeight(TransferableSelectionList list) {
		return ((AbstractSelectionListAccessor) (Object) list).dragit$getItemHeight();
	}

	/** Height of the title above the first pack row. */
	static int headerHeight(TransferableSelectionList list) {
		//? if >=1.21.10 {
		List<TransferableSelectionList.Entry> all = list.children();
		return !all.isEmpty() && !(all.get(0) instanceof TransferableSelectionList.PackEntry) ? all.get(0).getHeight() : 0;
		//?} else {
		/*return ((AbstractSelectionListAccessor) (Object) list).dragit$getHeaderHeight();
		*///?}
	}

	static boolean scrollbarVisible(TransferableSelectionList list) {
		return ((AbstractScrollAreaAccessor) (Object) list).dragit$isScrollbarVisible();
	}

	/** Current scroll offset in pixels. */
	static double scrollAmount(TransferableSelectionList list) {
		//? if >=1.21.4 {
		return list.scrollAmount();
		//?} else {
		/*return list.getScrollAmount();
		*///?}
	}

	/** Re-applies the scroll limits after the content height changed. */
	static void clampScroll(TransferableSelectionList list) {
		//? if >=1.21.4 {
		list.refreshScrollAmount();
		//?} else {
		/*list.clampScrollAmount();
		*///?}
	}

	static int scrollbarX(TransferableSelectionList list) {
		return ((TransferableSelectionListAccessor) (Object) list).dragit$getScrollbarX();
	}

	/** Height of the content drawn by one entry (vanilla passes {@code itemHeight - 4} to {@code render}). */
	static int entryHeight(TransferableSelectionList list) {
		return itemHeight(list) - 4;
	}

	/** Screen Y of the content of row 0, i.e. two pixels below the top of its cell (same as vanilla's {@code getRowTop(0)} up to 1.21.1). */
	static int originY(TransferableSelectionList list) {
		return list.getY() + 4 - (int) scrollAmount(list) + headerHeight(list);
	}

	static int rowTop(TransferableSelectionList list, int index) {
		return originY(list) + index * itemHeight(list);
	}

	/** X of the content of every row (the icon's left edge); the cell of a row starts two pixels before it. */
	static int contentLeft(TransferableSelectionList list) {
		//? if >=1.21.10 {
		return list.getRowLeft() + 2;
		//?} else {
		/*return list.getRowLeft();
		*///?}
	}

	/** Left edge of every card, highlight and zone frame. */
	static int cellLeft(TransferableSelectionList list) {
		return contentLeft(list) - 2;
	}

	/**
	 * Right edge (exclusive) of every card, highlight and zone frame. Vanilla's own selection highlight stops there as well,
	 * so nothing touches the scrollbar.
	 */
	static int cardRight(TransferableSelectionList list) {
		//? if >=1.21.10 {
		return list.getRowLeft() + list.getRowWidth() - (scrollbarVisible(list) ? 6 : 0);
		//?} else {
		/*return scrollbarVisible(list) ? scrollbarX(list) - 1 : list.getRowLeft() - 2 + list.getRowWidth();
		*///?}
	}

	/** Right edge (exclusive) of the frame drawn around a list that would receive a pack; the scrollbar stays outside of it. */
	static int frameRight(TransferableSelectionList list) {
		//? if >=1.21.10 {
		return scrollbarVisible(list) ? scrollbarX(list) - 1 : list.getRight();
		//?} else {
		/*return cardRight(list);
		*///?}
	}

	/** Width of the card drawn for a lifted entry / the placeholder. */
	static int cardWidth(TransferableSelectionList list) {
		return cardRight(list) - cellLeft(list);
	}

	/** Index of the pack row under the point, using vanilla's own hit test; {@code -1} if none. */
	static int hit(TransferableSelectionList list, double x, double y) {
		//? if >=1.21.10 {
		List<TransferableSelectionList.PackEntry> packs = entries(list);
		for (int i = 0; i < packs.size(); i++) {
			if (packs.get(i).isMouseOver(x, y)) {
				return i;
			}
		}
		return -1;
		//?} else {
		/*int half = list.getRowWidth() / 2;
		int center = list.getX() + list.getWidth() / 2;
		int m = Mth.floor(y - list.getY()) - headerHeight(list) + (int) scrollAmount(list) - 4;
		int n = m / itemHeight(list);
		return x >= center - half && x <= center + half && m >= 0 && n >= 0 && n < list.children().size() ? n : -1;
		*///?}
	}

	static TransferableSelectionList.PackEntry find(TransferableSelectionList list, String name) {
		for (TransferableSelectionList.PackEntry entry : entries(list)) {
			if (entry.getPackId().equals(name)) {
				return entry;
			}
		}
		return null;
	}

	static int indexOf(TransferableSelectionList list, String name) {
		List<TransferableSelectionList.PackEntry> children = entries(list);
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i).getPackId().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * True when a plain click at {@code x, y} would hit the enable/disable arrow of this row, with vanilla's own hit zones
	 * (the left 32 px of the row, or its left half when the pack can only be disabled). The zones are measured from the row's
	 * layout origin up to 1.21.10 (so a click in the padding to the left of the icon counts) and from its icon from 1.21.11.
	 */
	static boolean onToggleArrow(TransferableSelectionList list, TransferableSelectionList.PackEntry entry, double x, double y) {
		PackSelectionModel.Entry pack = packOf(entry);
		//? if >=1.21.11 {
		int dx = (int) x - entry.getContentX();
		int dy = (int) y - entry.getContentY();
		return dy >= 0 && dy < 32 && dx >= 0 && ((pack.canSelect() && dx < 32) || (pack.canUnselect() && dx < 16));
		//?} else {
		/*double d = x - list.getRowLeft();
		return d <= 32.0 && (pack.canSelect() || (d < 16.0 && pack.canUnselect()));
		*///?}
	}

	static PackSelectionModel.Entry packOf(TransferableSelectionList.PackEntry entry) {
		return ((PackEntryAccessor) (Object) entry).dragit$getPack();
	}

	static List<String> names(TransferableSelectionList list) {
		List<TransferableSelectionList.PackEntry> children = entries(list);
		List<String> names = new ArrayList<>(children.size());
		for (TransferableSelectionList.PackEntry entry : children) {
			names.add(entry.getPackId());
		}
		return names;
	}
}
