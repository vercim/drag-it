package dev.vercim.dragit.drag;

import dev.vercim.dragit.DragItSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.resource.ResourcePackProfile;

/**
 * Works out where a dragged pack would land: which list, and at which gap.
 *
 * <p>A <i>gap</i> {@code g} is an index into the target list <b>with the dragged pack removed</b>: the pack ends up
 * at {@code list.add(g, pack)}. That keeps the number identical for "move inside a list" and "come from the other list".
 */
final class DropTarget {
	private DropTarget() {
	}

	static final int NO_LIST = -1;

	/**
	 * A pack that must keep its place: fixed-position ("pinned") packs. Nothing may be dropped across them and they are
	 * never lifted. Required packs such as the built-in "Default" pack (required but not pinned in vanilla 1.21.1) are
	 * <i>not</i> fixed: they can be re-ordered like any other pack, they just can never leave the enabled list.
	 */
	static boolean isFixed(ResourcePackProfile profile) {
		return profile.isPinned();
	}

	/**
	 * @param list    {@code DragController.AVAILABLE}/{@code SELECTED}, or {@link #NO_LIST} when the cursor is outside both lists
	 * @param gap     insertion gap (already clamped into the allowed range)
	 * @param valid   the pack may be dropped here
	 * @param blocked the cursor is on a spot forbidden by pinned packs (gap is the nearest allowed boundary)
	 */
	record Result(int list, int gap, boolean valid, boolean blocked) {
		static final Result NONE = new Result(NO_LIST, -1, false, false);
	}

	/**
	 * @param lists    widgets, indexed by list id
	 * @param profiles organizer lists, indexed by list id
	 * @param dragged  the pack under the cursor (the lifted card)
	 * @param group    every pack that moves with it, in list order (just {@code dragged} for a single drag)
	 * @param srcList  list the packs were taken from
	 * @param pointerX cursor, GUI px
	 * @param pointerY cursor, GUI px
	 * @param probeY   vertical position used to pick the gap (centre of the lifted card), GUI px
	 * @param allowed  per list: whether any of the packs may be dropped there at all (a required pack cannot be disabled...)
	 */
	static Result compute(
		PackListWidget[] lists, List<ResourcePackProfile>[] profiles, ResourcePackProfile dragged, List<ResourcePackProfile> group,
		int srcList, double pointerX, double pointerY, double probeY, boolean[] allowed
	) {
		int target = NO_LIST;
		for (int i = 0; i < lists.length; i++) {
			PackListWidget list = lists[i];
			int pad = DragItSettings.COLUMN_PADDING;
			if (pointerX >= list.getX() - pad && pointerX <= list.getRight() + pad && pointerY >= list.getY() && pointerY <= list.getBottom()) {
				target = i;
				break;
			}
		}
		if (target == NO_LIST) {
			return Result.NONE;
		}

		PackListWidget list = lists[target];
		List<ResourcePackProfile> base = new ArrayList<>(profiles[target]);
		base.removeAll(group);
		int n = base.size();
		if (!allowed[target]) {
			return new Result(target, n, false, false); // e.g. a required pack can be moved around but never disabled
		}
		int itemHeight = ListGeometry.itemHeight(list);
		int origin = ListGeometry.originY(list);

		int raw = (int) Math.floor((probeY - origin) / itemHeight + 0.5);
		raw = Math.max(0, Math.min(n, raw));
		boolean emptySpace = pointerY - origin >= (double) n * itemHeight;

		// Pinned packs split the list into zones the pack cannot cross. The zone is the one around where the pack
		// is now (inside a list) or where vanilla would insert it (coming from the other list).
		int lo = 0;
		int hi = n;
		if (target == DragController.SELECTED) {
			int anchor;
			if (target == srcList) {
				anchor = 0; // packs before the lifted one that stay behind
				for (int p = 0, at = profiles[target].indexOf(dragged); p < at; p++) {
					if (!group.contains(profiles[target].get(p))) {
						anchor++;
					}
				}
			} else {
				anchor = dragged.getInitialPosition().insert(new ArrayList<>(base), dragged, ResourcePackProfile::getPosition, true);
			}
			anchor = Math.max(0, Math.min(n, anchor));
			for (int p = 0; p < anchor; p++) {
				if (isFixed(base.get(p))) {
					lo = p + 1;
				}
			}
			for (int q = anchor; q < n; q++) {
				if (isFixed(base.get(q))) {
					hi = q;
					break;
				}
			}
		}

		if (raw < lo) {
			return new Result(target, lo, false, true);
		}
		if (raw > hi) {
			// Empty space below the last pack means "put it last": that is the end of the allowed range, not an error.
			return emptySpace ? new Result(target, hi, true, false) : new Result(target, hi, false, true);
		}
		return new Result(target, raw, true, false);
	}
}
