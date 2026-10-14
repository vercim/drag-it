package dev.vercim.dragit.drag;

import dev.vercim.dragit.DragItSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Animated outline of a selection: one shape per run of neighbouring selected rows instead of one fade per row.
 * A range selected with Shift grows out of the row it started at, a shortened range shrinks back, a pack added next to a
 * run extends it, and a run that appears out of nowhere opens up around its middle. Edges are measured in rows (a
 * fractional list index), so scrolling never disturbs them.
 * <p>
 * Every edge and every fade is a critically damped spring that keeps its velocity when the selection changes again before it
 * has arrived. Clicking faster than the animation plays therefore bends the motion instead of restarting it: the edges
 * never jump, turn around on the spot or stall, however fast the selection is edited.
 * <p>
 * A band is tied to the first and last pack of its run, not to row numbers, so it stays with its packs when vanilla
 * rebuilds the list (a drop, "send to the other list"); only edits of the selection itself make an edge glide. Pure state:
 * no Minecraft types, drawing is {@link DragRenderer#drawSelection}.
 */
final class SelectionBands {
	/** An edge closer than this (in rows) to its target, and slower than {@link #REST_SPEED}, has arrived. */
	private static final double SNAP = 0.01;
	/** Rows per second below which an edge counts as standing still. */
	private static final double REST_SPEED = 0.05;
	/** Tolerance (in rows) when deciding that one band lies inside another. */
	private static final double COVER = 0.02;
	/** A faded-out frame (opacity below this) is not worth flying to a new selection; a fresh one fades in there instead. */
	private static final double ADOPTABLE = 0.05;

	/** One value that approaches its target along a critically damped spring and keeps its velocity across new targets. */
	static final class Spring {
		double value;
		double velocity;

		/** Advances by {@code dt} seconds; exact for any step length, so the motion is the same at every frame rate. */
		void step(double target, double stiffness, double dt) {
			double distance = value - target;
			double carry = velocity + stiffness * distance;
			double decay = Math.exp(-stiffness * dt);
			value = target + (distance + carry * dt) * decay;
			velocity = (velocity - stiffness * carry * dt) * decay;
			if (Math.abs(value - target) < SNAP && Math.abs(velocity) < REST_SPEED) {
				value = target;
				velocity = 0;
			}
		}

		/** A fade stays between invisible and opaque; the spring does not overshoot out of that range. */
		void clamp() {
			if (value < 0) {
				value = 0;
				velocity = Math.max(0, velocity);
			} else if (value > 1) {
				value = 1;
				velocity = Math.min(0, velocity);
			}
		}
	}

	static final class Band {
		/** First and last pack of the run this band outlines. */
		String topPack;
		String bottomPack;
		/** Distance (rows) of the top edge from the top of {@code topPack}, and of the bottom edge from the bottom of {@code bottomPack}; sprung to 0. */
		final Spring topOffset = new Spring();
		final Spring bottomOffset = new Spring();
		final Spring fade = new Spring();
		/** Every pack of the run, to recognise the band again when the run changes shape. */
		Set<String> members = Set.of();
		/** Where the edges are right now, in rows from the top of the list; refreshed by every {@link #update}. */
		double top;
		double bottom;
		double alpha;
		/** False once no run claims the band: it fades out (or waits to be covered by the run that swallowed it). */
		boolean live;
	}

	/**
	 * The part of a band that falls on one row, in pixels: {@code y0..y1} (exclusive) and whether the row draws the band's top and
	 * bottom edge line.
	 */
	record Slice(int y0, int y1, boolean topEdge, boolean bottomEdge) {
	}

	/**
	 * Works out the slice of {@code band} on row {@code index}, whose cell spans {@code top .. top + height} pixels, or {@code null}
	 * if the band does not touch it. An edge belongs to the one row it lies in (the top edge to {@code [index, index + 1)}, the
	 * bottom edge to {@code (index, index + 1]}). A slice thinner than half a pixel would round away together with that edge, and
	 * the edge would be missing for the frames in which it glides across a row boundary or settles just short of one; the row that
	 * owns an edge therefore keeps a one-pixel slice inside its own cell.
	 */
	static Slice slice(Band band, int index, int top, int height) {
		double from = Math.max(band.top, index);
		double to = Math.min(band.bottom, index + 1);
		if (to < from) {
			return null;
		}
		boolean topEdge = band.top >= index && band.top < index + 1;
		boolean bottomEdge = band.bottom > index && band.bottom <= index + 1;
		int y0 = top + (int) Math.round((from - index) * height);
		int y1 = top + (int) Math.round((to - index) * height);
		if (y1 <= y0) {
			if (!topEdge && !bottomEdge) {
				return null;
			}
			y0 = Math.min(y0, top + height - 1);
			y1 = y0 + 1;
		}
		return new Slice(y0, y1, topEdge, bottomEdge);
	}

	/** Rows {@code start} (inclusive) to {@code end} (exclusive). */
	private record Run(int start, int end) {
	}

	private final List<List<Band>> bands = List.of(new ArrayList<>(), new ArrayList<>());
	private final boolean follow;

	/**
	 * @param follow the selection is a single frame that moves to wherever the selection goes (vanilla's own one-pack selection);
	 *               the frame does not open up around its pack but fades in once, and a new run takes over the band of the old one
	 *               even when they share no pack. Without it (the multi-selection) a run only continues a band it overlaps.
	 */
	SelectionBands(boolean follow) {
		this.follow = follow;
	}

	List<Band> bands(int list) {
		return bands.get(list);
	}

	/**
	 * @param names    layout order of the list
	 * @param selected every selected pack
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
		if (follow) {
			// A lone frame moves on: the run takes the most visible band that has nothing to follow any more.
			for (Run run : runs) {
				Band best = null;
				for (Band band : bands) {
					if (!taken.contains(band) && band.fade.value > ADOPTABLE && (best == null || band.fade.value > best.fade.value)) {
						best = band;
					}
				}
				if (!carriers.containsKey(run) && best != null) {
					carriers.put(run, best);
					taken.add(best);
				}
			}
		}

		// Spawn first: a run split off a band copies that band's edges as they were before this frame retargets it.
		List<Band> created = new ArrayList<>();
		for (Run run : runs) {
			if (!carriers.containsKey(run)) {
				created.add(spawn(run, names, index, bands, edited, seed, follow));
			}
		}
		for (Map.Entry<Run, Band> entry : carriers.entrySet()) {
			retarget(entry.getValue(), entry.getKey(), names);
		}
		for (Band band : bands) {
			band.live = taken.contains(band);
		}
		bands.addAll(created);

		// Edges first, then the fades: whether a swallowed band is hidden by the band over it depends on where both edges are now.
		for (Band band : bands) {
			band.topOffset.step(0, DragItSettings.SELECTION_EDGE_STIFFNESS, dt);
			band.bottomOffset.step(0, DragItSettings.SELECTION_EDGE_STIFFNESS, dt);
			resolve(band, index);
		}
		// A band the run left (two runs merged) keeps showing until the wider band has grown over it, then it fades; any other
		// band nobody claims fades at once.
		List<Band> gone = new ArrayList<>();
		for (Band band : bands) {
			boolean show = band.live || isInside(band, runs, index) && !isCovered(band, bands);
			band.fade.step(show ? 1.0 : 0.0, DragItSettings.SELECTION_FADE_STIFFNESS, dt);
			band.fade.clamp();
			band.alpha = band.fade.value;
			if (!show && band.alpha == 0.0) {
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
		band.top = first + band.topOffset.value;
		band.bottom = last + 1 + band.bottomOffset.value;
		return true;
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

	/** Keeps the edges where they are on screen, and moving as they were, and sends them to the new run. */
	private static void retarget(Band band, Run run, List<String> names) {
		double top = band.top;
		double bottom = band.bottom;
		anchor(band, run, names);
		band.topOffset.value = top - run.start();
		band.bottomOffset.value = bottom - run.end();
	}

	private static Band spawn(Run run, List<String> names, Map<String, Integer> index, List<Band> existing, boolean edited, String seed, boolean follow) {
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
		Band band = new Band();
		if (source != null && Math.min(source.bottom, run.end()) > Math.max(source.top, run.start())) {
			top = Math.max(source.top, run.start());
			bottom = Math.min(source.bottom, run.end());
			alpha = source.alpha;
			// An edge that is still the source's edge keeps moving as it did; an edge cut by the run starts at rest.
			if (source.top >= run.start()) {
				band.topOffset.velocity = source.topOffset.velocity;
			}
			if (source.bottom <= run.end()) {
				band.bottomOffset.velocity = source.bottomOffset.velocity;
			}
			band.fade.velocity = source.fade.velocity;
		} else if (follow) {
			top = run.start();
			bottom = run.end();
			alpha = 0.0;
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
		anchor(band, run, names);
		band.topOffset.value = top - run.start();
		band.bottomOffset.value = bottom - run.end();
		band.fade.value = alpha;
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
