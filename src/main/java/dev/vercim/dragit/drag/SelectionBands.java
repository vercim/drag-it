package dev.vercim.dragit.drag;

import dev.vercim.dragit.DragItSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Animated outline of the multi-selection: one shape per run of neighbouring selected rows instead of one fade per row.
 * A range selected with Shift grows out of the row it started at, a shortened range shrinks back, a pack added next to a
 * run extends it, and a run that appears out of nowhere opens up around its middle. Edges are measured in rows (a
 * fractional list index), so scrolling never disturbs them.
 * <p>
 * A band is tied to the first and last pack of its run, not to row numbers, so it stays with its packs when vanilla
 * rebuilds the list (a drop, "send to the other list"); only edits of the selection itself make an edge glide. Pure state:
 * no Minecraft types, drawing is {@link DragRenderer#drawSelection}.
 */
final class SelectionBands {
	/** An edge closer than this (in rows) to its target has arrived. */
	private static final double SNAP = 0.01;
	/** Tolerance (in rows) when deciding that one band lies inside another. */
	private static final double COVER = 0.02;

	static final class Band {
		/** First and last pack of the run this band outlines. */
		String topPack;
		String bottomPack;
		/** Distance (rows) of the top edge from the top of {@code topPack}, and of the bottom edge from the bottom of {@code bottomPack}; eased to 0. */
		double topOffset;
		double bottomOffset;
		/** Every pack of the run, to recognise the band again when the run changes shape. */
		Set<String> members = Set.of();
		/** Where the edges are right now, in rows from the top of the list; refreshed by every {@link #update}. */
		double top;
		double bottom;
		double alpha;
		/** False once no run claims the band: it fades out (or waits to be covered by the run that swallowed it). */
		boolean live;
	}

	/** Rows {@code start} (inclusive) to {@code end} (exclusive). */
	private record Run(int start, int end) {
	}

	private final List<List<Band>> bands = List.of(new ArrayList<>(), new ArrayList<>());

	List<Band> bands(int list) {
		return bands.get(list);
	}

	/**
	 * @param names    layout order of the list
	 * @param selected every multi-selected pack
	 * @param edited   the selection itself changed since the last call; if not, a band that has to be created (the packs just
	 *                 landed in this list) appears at once instead of growing
	 * @param seed     pack the outline of a new run grows out of (the row that was selected before), or {@code null}
	 */
	void update(int list, List<String> names, Set<String> selected, boolean edited, String seed, double dt) {
		List<Band> bands = this.bands.get(list);
		if (selected.isEmpty() && bands.isEmpty()) {
			return;
		}
		Map<String, Integer> index = new HashMap<>();
		for (int i = 0; i < names.size(); i++) {
			index.put(names.get(i), i);
		}
		bands.removeIf(band -> !resolve(band, index)); // the packs of this band left the list
		List<Run> runs = runs(names, selected);

		// Every run takes over the band that tracked most of its packs; a band serves one run only.
		record Match(Run run, Band band, int overlap) {
		}
		List<Match> matches = new ArrayList<>();
		for (Run run : runs) {
			for (Band band : bands) {
				int overlap = overlap(band, run, index);
				if (overlap > 0) {
					matches.add(new Match(run, band, overlap));
				}
			}
		}
		matches.sort((a, b) -> Integer.compare(b.overlap(), a.overlap()));
		Map<Run, Band> carriers = new HashMap<>();
		Set<Band> taken = new HashSet<>();
		for (Match match : matches) {
			if (!carriers.containsKey(match.run()) && taken.add(match.band())) {
				carriers.put(match.run(), match.band());
			}
		}

		// Spawn first: a run split off a band copies that band's edges as they were before this frame retargets it.
		List<Band> created = new ArrayList<>();
		for (Run run : runs) {
			if (!carriers.containsKey(run)) {
				created.add(spawn(run, names, index, bands, edited, seed));
			}
		}
		for (Map.Entry<Run, Band> entry : carriers.entrySet()) {
			retarget(entry.getValue(), entry.getKey(), names);
		}
		for (Band band : bands) {
			band.live = taken.contains(band);
		}
		bands.addAll(created);

		double edge = 1.0 - Math.exp(-dt * DragItSettings.SELECTION_EDGE_RATE);
		double fade = 1.0 - Math.exp(-dt * DragItSettings.SELECTION_FADE_RATE);
		Set<Band> swallowed = new HashSet<>();
		for (Band band : bands) {
			if (band.live) {
				band.topOffset = approach(band.topOffset, edge);
				band.bottomOffset = approach(band.bottomOffset, edge);
				double alpha = band.alpha + (1.0 - band.alpha) * fade;
				band.alpha = alpha > 0.995 ? 1.0 : alpha;
				resolve(band, index);
			} else if (isInside(band, runs, index)) {
				swallowed.add(band); // two runs merged: keep showing the old part until the wider band has grown over it
			} else {
				band.alpha *= 1.0 - fade;
			}
		}
		List<Band> gone = new ArrayList<>();
		for (Band band : bands) {
			if (!band.live && (band.alpha < 0.01 || swallowed.contains(band) && isCovered(band, bands))) {
				gone.add(band);
			}
		}
		bands.removeAll(gone);
	}

	/** Puts the edges at their current place in the list; {@code false} if one of the band's anchor packs is gone. */
	private static boolean resolve(Band band, Map<String, Integer> index) {
		Integer first = index.get(band.topPack);
		Integer last = index.get(band.bottomPack);
		if (first == null || last == null) {
			return false;
		}
		band.top = first + band.topOffset;
		band.bottom = last + 1 + band.bottomOffset;
		return true;
	}

	private static double approach(double offset, double blend) {
		double next = offset * (1.0 - blend);
		return Math.abs(next) < SNAP ? 0.0 : next;
	}

	private static List<Run> runs(List<String> names, Set<String> selected) {
		List<Run> runs = new ArrayList<>();
		int start = -1;
		for (int i = 0; i <= names.size(); i++) {
			boolean in = i < names.size() && selected.contains(names.get(i));
			if (in && start < 0) {
				start = i;
			} else if (!in && start >= 0) {
				runs.add(new Run(start, i));
				start = -1;
			}
		}
		return runs;
	}

	/** How many packs of the band currently sit inside the run. */
	private static int overlap(Band band, Run run, Map<String, Integer> index) {
		int count = 0;
		for (String member : band.members) {
			Integer row = index.get(member);
			if (row != null && row >= run.start() && row < run.end()) {
				count++;
			}
		}
		return count;
	}

	private static void anchor(Band band, Run run, List<String> names) {
		band.topPack = names.get(run.start());
		band.bottomPack = names.get(run.end() - 1);
		band.members = new HashSet<>(names.subList(run.start(), run.end()));
	}

	/** Keeps the edges where they are on screen and sends them to the new run. */
	private static void retarget(Band band, Run run, List<String> names) {
		double top = band.top;
		double bottom = band.bottom;
		anchor(band, run, names);
		band.topOffset = top - run.start();
		band.bottomOffset = bottom - run.end();
	}

	private static Band spawn(Run run, List<String> names, Map<String, Integer> index, List<Band> existing, boolean edited, String seed) {
		Band source = null; // a band that outlined these packs together with others: the run was split off it
		int best = 0;
		for (Band band : existing) {
			int overlap = overlap(band, run, index);
			if (overlap > best) {
				source = band;
				best = overlap;
			}
		}
		double top;
		double bottom;
		double alpha;
		if (source != null && Math.min(source.bottom, run.end()) > Math.max(source.top, run.start())) {
			top = Math.max(source.top, run.start());
			bottom = Math.min(source.bottom, run.end());
			alpha = source.alpha;
		} else if (!edited) {
			top = run.start();
			bottom = run.end();
			alpha = 1.0;
		} else {
			Integer seedRow = seed == null ? null : index.get(seed);
			if (seedRow != null && seedRow >= run.start() && seedRow < run.end()) {
				top = seedRow;
				bottom = seedRow + 1;
			} else if (run.end() - run.start() == 1) {
				top = bottom = run.start() + 0.5;
			} else {
				top = run.start();
				bottom = run.start() + 1;
			}
			alpha = 0.0;
		}
		Band band = new Band();
		anchor(band, run, names);
		band.topOffset = top - run.start();
		band.bottomOffset = bottom - run.end();
		band.top = top;
		band.bottom = bottom;
		band.alpha = alpha;
		band.live = true;
		return band;
	}

	/** True if every pack of the band belongs to one current run. */
	private static boolean isInside(Band band, List<Run> runs, Map<String, Integer> index) {
		for (Run run : runs) {
			if (overlap(band, run, index) == band.members.size()) {
				return true;
			}
		}
		return false;
	}

	/** True if a live band already draws everything this one draws, at least as strongly. */
	private static boolean isCovered(Band band, List<Band> bands) {
		for (Band other : bands) {
			if (other != band && other.live && other.top <= band.top + COVER && other.bottom >= band.bottom - COVER
				&& other.alpha >= band.alpha - 0.01) {
				return true;
			}
		}
		return false;
	}
}
