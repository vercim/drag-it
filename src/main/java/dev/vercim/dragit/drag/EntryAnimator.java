package dev.vercim.dragit.drag;

import dev.vercim.dragit.DragItSettings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smooth vertical positions of list entries, kept per pack id so the animation survives vanilla rebuilding every widget
 * entry after a change. Positions are in "content space" (pixels below the first row's top), so scrolling never disturbs them.
 */
final class EntryAnimator {
	private static final float SNAP = 0.04F;

	@SuppressWarnings("unchecked")
	private final Map<String, Float>[] positions = new Map[]{new HashMap<String, Float>(), new HashMap<String, Float>()};

	/**
	 * Eases every entry of {@code list} towards {@code slots[i] * itemHeight}.
	 *
	 * @param names layout order of the entries
	 * @param slots slot each entry should occupy right now (equals its index when nothing is being dragged)
	 */
	void update(int list, List<String> names, int[] slots, int itemHeight, double dt) {
		Map<String, Float> map = positions[list];
		double blend = 1.0 - Math.exp(-dt * DragItSettings.NEIGHBOR_RATE);
		HashMap<String, Float> next = new HashMap<>();
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			float target = slots[i] * itemHeight;
			Float current = map.get(name);
			float value;
			if (current == null) {
				value = target;
			} else {
				value = (float) (current + (target - current) * blend);
				if (Math.abs(target - value) < SNAP) {
					value = target;
				}
			}
			next.put(name, value);
		}
		map.clear();
		map.putAll(next);
	}

	/** Vertical offset to draw the entry at, relative to its layout row. Exactly {@code 0} when at rest. */
	float offset(int list, String name, int index, int itemHeight) {
		Float value = positions[list].get(name);
		if (value == null) {
			return 0;
		}
		float offset = value - index * itemHeight;
		return Math.abs(offset) < SNAP ? 0 : offset;
	}

	/** True while any entry of the list is still sliding. */
	boolean isMoving(int list, List<String> names, int itemHeight) {
		for (int i = 0; i < names.size(); i++) {
			if (offset(list, names.get(i), i, itemHeight) != 0) {
				return true;
			}
		}
		return false;
	}

	void snap(int list, String name, int index, int itemHeight) {
		positions[list].put(name, (float) (index * itemHeight));
	}
}
