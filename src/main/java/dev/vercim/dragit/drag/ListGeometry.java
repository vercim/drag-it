package dev.vercim.dragit.drag;

import dev.vercim.dragit.mixin.accessor.EntryListWidgetAccessor;
import dev.vercim.dragit.mixin.accessor.PackListWidgetAccessor;
import dev.vercim.dragit.mixin.accessor.ResourcePackEntryAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.util.math.MathHelper;

/** Row geometry of a vanilla {@link PackListWidget}, recomputed from live widget state on every call (GUI scale / resize safe). */
final class ListGeometry {
	private ListGeometry() {
	}

	static int itemHeight(PackListWidget list) {
		return ((EntryListWidgetAccessor) (Object) list).dragit$getItemHeight();
	}

	static int headerHeight(PackListWidget list) {
		return ((EntryListWidgetAccessor) (Object) list).dragit$getHeaderHeight();
	}

	static boolean scrollbarVisible(PackListWidget list) {
		return ((EntryListWidgetAccessor) (Object) list).dragit$isScrollbarVisible();
	}

	static int scrollbarX(PackListWidget list) {
		return ((PackListWidgetAccessor) (Object) list).dragit$getScrollbarX();
	}

	/** Height of the content drawn by one entry (vanilla passes {@code itemHeight - 4} to {@code render}). */
	static int entryHeight(PackListWidget list) {
		return itemHeight(list) - 4;
	}

	/** Screen Y of the top of row 0 (same formula as vanilla's {@code getRowTop(0)}). */
	static int originY(PackListWidget list) {
		return list.getY() + 4 - (int) list.getScrollAmount() + headerHeight(list);
	}

	static int rowTop(PackListWidget list, int index) {
		return originY(list) + index * itemHeight(list);
	}

	/**
	 * Right edge (exclusive) of every card, highlight and zone frame: the list's right edge, or one pixel short of the
	 * scrollbar when there is one (vanilla's own selection highlight stops there as well, so nothing touches the scrollbar).
	 */
	static int cardRight(PackListWidget list) {
		return scrollbarVisible(list) ? scrollbarX(list) - 1 : list.getRowLeft() - 2 + list.getRowWidth();
	}

	/** Width of the card drawn for a lifted entry / the placeholder (starts at {@code rowLeft - 2}). */
	static int cardWidth(PackListWidget list) {
		return cardRight(list) - (list.getRowLeft() - 2);
	}

	/** Index of the entry under the point, using vanilla's own hit-test arithmetic; {@code -1} if none. */
	static int hit(PackListWidget list, double x, double y) {
		int half = list.getRowWidth() / 2;
		int center = list.getX() + list.getWidth() / 2;
		int m = MathHelper.floor(y - list.getY()) - headerHeight(list) + (int) list.getScrollAmount() - 4;
		int n = m / itemHeight(list);
		return x >= center - half && x <= center + half && m >= 0 && n >= 0 && n < list.children().size() ? n : -1;
	}

	static PackListWidget.ResourcePackEntry find(PackListWidget list, String name) {
		for (PackListWidget.ResourcePackEntry entry : list.children()) {
			if (entry.getName().equals(name)) {
				return entry;
			}
		}
		return null;
	}

	static int indexOf(PackListWidget list, String name) {
		List<PackListWidget.ResourcePackEntry> children = list.children();
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i).getName().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	static ResourcePackOrganizer.Pack packOf(PackListWidget.ResourcePackEntry entry) {
		return ((ResourcePackEntryAccessor) (Object) entry).dragit$getPack();
	}

	static List<String> names(PackListWidget list) {
		List<String> names = new ArrayList<>(list.children().size());
		for (PackListWidget.ResourcePackEntry entry : list.children()) {
			names.add(entry.getName());
		}
		return names;
	}
}
