package dev.vercim.dragit.drag;

import dev.vercim.dragit.mixin.accessor.AbstractSelectionListAccessor;
import dev.vercim.dragit.mixin.accessor.PackEntryAccessor;
import dev.vercim.dragit.mixin.accessor.TransferableSelectionListAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.util.Mth;

/** Row geometry of a vanilla {@link TransferableSelectionList}, recomputed from live widget state on every call (GUI scale / resize safe). */
final class ListGeometry {
	private ListGeometry() {
	}

	static int itemHeight(TransferableSelectionList list) {
		return ((AbstractSelectionListAccessor) (Object) list).dragit$getItemHeight();
	}

	static int headerHeight(TransferableSelectionList list) {
		return ((AbstractSelectionListAccessor) (Object) list).dragit$getHeaderHeight();
	}

	static boolean scrollbarVisible(TransferableSelectionList list) {
		return ((AbstractSelectionListAccessor) (Object) list).dragit$isScrollbarVisible();
	}

	static int scrollbarX(TransferableSelectionList list) {
		return ((TransferableSelectionListAccessor) (Object) list).dragit$getScrollbarX();
	}

	/** Height of the content drawn by one entry (vanilla passes {@code itemHeight - 4} to {@code render}). */
	static int entryHeight(TransferableSelectionList list) {
		return itemHeight(list) - 4;
	}

	/** Screen Y of the top of row 0 (same formula as vanilla's {@code getRowTop(0)}). */
	static int originY(TransferableSelectionList list) {
		return list.getY() + 4 - (int) list.getScrollAmount() + headerHeight(list);
	}

	static int rowTop(TransferableSelectionList list, int index) {
		return originY(list) + index * itemHeight(list);
	}

	/**
	 * Right edge (exclusive) of every card, highlight and zone frame: the list's right edge, or one pixel short of the
	 * scrollbar when there is one (vanilla's own selection highlight stops there as well, so nothing touches the scrollbar).
	 */
	static int cardRight(TransferableSelectionList list) {
		return scrollbarVisible(list) ? scrollbarX(list) - 1 : list.getRowLeft() - 2 + list.getRowWidth();
	}

	/** Width of the card drawn for a lifted entry / the placeholder (starts at {@code rowLeft - 2}). */
	static int cardWidth(TransferableSelectionList list) {
		return cardRight(list) - (list.getRowLeft() - 2);
	}

	/** Index of the entry under the point, using vanilla's own hit-test arithmetic; {@code -1} if none. */
	static int hit(TransferableSelectionList list, double x, double y) {
		int half = list.getRowWidth() / 2;
		int center = list.getX() + list.getWidth() / 2;
		int m = Mth.floor(y - list.getY()) - headerHeight(list) + (int) list.getScrollAmount() - 4;
		int n = m / itemHeight(list);
		return x >= center - half && x <= center + half && m >= 0 && n >= 0 && n < list.children().size() ? n : -1;
	}

	static TransferableSelectionList.PackEntry find(TransferableSelectionList list, String name) {
		for (TransferableSelectionList.PackEntry entry : list.children()) {
			if (entry.getPackId().equals(name)) {
				return entry;
			}
		}
		return null;
	}

	static int indexOf(TransferableSelectionList list, String name) {
		List<TransferableSelectionList.PackEntry> children = list.children();
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i).getPackId().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	static PackSelectionModel.Entry packOf(TransferableSelectionList.PackEntry entry) {
		return ((PackEntryAccessor) (Object) entry).dragit$getPack();
	}

	static List<String> names(TransferableSelectionList list) {
		List<String> names = new ArrayList<>(list.children().size());
		for (TransferableSelectionList.PackEntry entry : list.children()) {
			names.add(entry.getPackId());
		}
		return names;
	}
}
