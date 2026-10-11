package dev.vercim.dragit;

/**
 * Every tunable of the mod lives here. Rates are "per second": the per-frame blend factor is
 * {@code 1 - exp(-dt * rate)}, so motion is identical at 30 and at 300 FPS.
 */
public final class DragItSettings {
	private DragItSettings() {
	}

	/** Pointer travel, in physical window pixels, before a press turns into a drag. */
	public static final double DRAG_THRESHOLD_PX = 4.0;

	/** How fast the lifted entry catches up with the cursor. */
	public static final double GHOST_FOLLOW_RATE = 26.0;
	/** How fast the other entries slide aside / back into their slots. */
	public static final double NEIGHBOR_RATE = 18.0;
	/** How fast the dropped (or cancelled) entry glides into its slot. */
	public static final double SETTLE_RATE = 20.0;
	/** How fast the "lifted" look (scale, shadow) fades in and out. */
	public static final double LIFT_RATE = 16.0;
	/** How fast the insertion placeholder slides and fades. */
	public static final double INDICATOR_RATE = 24.0;

	/** How fast the selection highlight of a pack (single and multi) fades in / out. */
	public static final double SELECTION_FADE_RATE = 20.0;

	/** Scale of the fully lifted entry. */
	public static final float GHOST_SCALE = 1.05F;

	/** Distance (GUI px) from the list edge where auto-scroll kicks in. */
	public static final int AUTOSCROLL_EDGE = 30;
	/** How far (GUI px) beyond the list edge auto-scroll still works. */
	public static final int AUTOSCROLL_SLACK = 40;
	/** Auto-scroll speed at the very edge, GUI px per second. */
	public static final double AUTOSCROLL_MAX_SPEED = 420.0;

	/** Extra horizontal reach (GUI px) of each list column when picking a drop list. */
	public static final int COLUMN_PADDING = 14;

	/** ARGB colour of the insertion placeholder and the active-list frame. */
	public static final int INDICATOR_COLOR = 0xFF55AAFF;
	/** ARGB colour used when the insertion point is not allowed (pinned packs). */
	public static final int BLOCKED_COLOR = 0xFFE05555;
	/** ARGB frame and fill of a pack that is part of the Shift multi-selection. */
	public static final int MULTI_SELECT_BORDER = 0xFF55AAFF;
	public static final int MULTI_SELECT_FILL = 0xFF1F3550;
	/** ARGB fill of the lifted entry's card. */
	public static final int GHOST_BACKGROUND = 0xFF1B1B1B;
}
