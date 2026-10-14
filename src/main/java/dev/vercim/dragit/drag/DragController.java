package dev.vercim.dragit.drag;

import com.mojang.blaze3d.platform.Window;
import dev.vercim.dragit.DragIt;
import dev.vercim.dragit.DragItSettings;
import dev.vercim.dragit.mixin.accessor.PackSelectionModelAccessor;
import dev.vercim.dragit.mixin.accessor.PackSelectionScreenAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

/**
 * Drag & drop state machine of one {@link PackSelectionScreen}.
 *
 * <pre>
 * IDLE --press on a movable entry--> PENDING --cursor travels past the threshold--> DRAGGING
 * PENDING --release--> IDLE (the swallowed click is replayed to vanilla)
 * DRAGGING --release / Esc--> SETTLING (the lifted entry glides into its slot) --arrived--> IDLE
 * </pre>
 *
 * Everything is driven by real time ({@code System.nanoTime()} per frame), never by ticks. All order/enable changes go
 * through the vanilla {@link PackSelectionModel}; this class stores no pack state of its own.
 */
public final class DragController {
	public static final int AVAILABLE = 0;
	public static final int SELECTED = 1;

	public enum Phase {
		IDLE, PENDING, DRAGGING, SETTLING
	}

	private static boolean drawingGhost;

	/** True while the lifted entry is being drawn through the entry's own {@code render()} (so the render hook leaves it alone). */
	public static boolean isDrawingGhost() {
		return drawingGhost;
	}

	static void setDrawingGhost(boolean value) {
		drawingGhost = value;
	}

	/** Row of a pack entry among the pack rows of its list (a title row, where the list has one, is not counted). */
	public static int rowOf(TransferableSelectionList widget, TransferableSelectionList.PackEntry entry) {
		return ListGeometry.entries(widget).indexOf(entry);
	}

	public static DragController of(PackSelectionScreen screen) {
		return ((ControllerHolder) screen).dragit$getController();
	}

	static final class Indicator {
		double y;
		double alpha;
		DropTarget.Block block = DropTarget.Block.NONE;
	}

	private record Grab(int list, int index, String name) {
	}

	final PackSelectionScreen screen;
	final EntryAnimator animator = new EntryAnimator();
	final Indicator[] indicators = {new Indicator(), new Indicator()};

	Phase phase = Phase.IDLE;
	int srcList;
	String packId;
	/** Every pack lifted by the current drag (list order); more than one when a multi-selection is dragged. */
	final List<String> dragIds = new ArrayList<>();
	final List<Pack> dragProfiles = new ArrayList<>();
	int settleList;
	/** The rest of a dropped stack: each card glides from its place in the stack into the row of its pack. */
	final List<Follower> followers = new ArrayList<>();
	DropTarget.Result drop = DropTarget.Result.NONE;
	double ghostX;
	double ghostY;
	double lift;
	/**
	 * 0..1 per card of the lifted stack (0 = the card under the cursor, 1 and 2 = the cards behind it): how red it currently
	 * is; eases towards 1 while the pack(s) it stands for cannot go to the list under the cursor.
	 */
	final double[] blockedMix = new double[3];
	double pointerX;
	double pointerY;

	private final Set<String> multi = new LinkedHashSet<>();
	private final SelectionBands bands = new SelectionBands();
	/** The multi-selection as the outline bands last saw it, to tell an edit of the selection from packs merely changing lists. */
	private final Set<String> bandSelection = new HashSet<>();
	/** Pack the outline of a newly selected run grows out of (the row that was selected before the click); consumed by the next frame. */
	private String bandSeed;
	private final Map<String, Double> selectionAlpha = new HashMap<>();
	/** Vanilla's single selection (pack id) of each list as {@link #settleSelection} last saw it, to tell which one is the newer. */
	private final String[] lastSelected = new String[2];
	private int multiList = -1;
	private String multiAnchor;

	private boolean buttonDown;
	private boolean swallowRelease;
	private double pressX;
	private double pressY;
	private double grabDX;
	private double grabDY;
	private double ghostTX;
	private double ghostTY;
	private long lastNanos;
	private double dt;
	private boolean faulted;

	public DragController(PackSelectionScreen screen) {
		this.screen = screen;
	}

	// ---------------------------------------------------------------- queries used by the render hooks

	public Phase phase() {
		return phase;
	}

	/** The dragged entry is drawn as the lifted card instead of in its row. */
	public boolean isHidden(String name) {
		if (phase == Phase.DRAGGING) {
			return dragIds.contains(name); // the whole group leaves its rows
		}
		return phase == Phase.SETTLING && (name.equals(packId) || followers.stream().anyMatch(f -> f.name.equals(name)));
	}

	/** Vanilla "hovered" decorations (arrows) are meaningless while entries slide around. */
	public boolean suppressHover() {
		return phase == Phase.DRAGGING || phase == Phase.SETTLING;
	}

	/** Extra vertical offset (GUI px) to draw this entry at. */
	public float offsetFor(TransferableSelectionList widget, String name, int index) {
		int list = listOf(widget);
		return list < 0 ? 0 : animator.offset(list, name, index, ListGeometry.itemHeight(widget));
	}

	// ---------------------------------------------------------------- screen access

	private boolean ready() {
		PackSelectionScreenAccessor access = (PackSelectionScreenAccessor) (Object) screen;
		return access.dragit$getAvailableList() != null
			&& access.dragit$getSelectedList() != null
			&& access.dragit$getOrganizer() != null;
	}

	TransferableSelectionList list(int index) {
		PackSelectionScreenAccessor access = (PackSelectionScreenAccessor) (Object) screen;
		return index == AVAILABLE ? access.dragit$getAvailableList() : access.dragit$getSelectedList();
	}

	private int listOf(TransferableSelectionList widget) {
		PackSelectionScreenAccessor access = (PackSelectionScreenAccessor) (Object) screen;
		if (widget == access.dragit$getAvailableList()) {
			return AVAILABLE;
		}
		return widget == access.dragit$getSelectedList() ? SELECTED : -1;
	}

	private PackSelectionModelAccessor organizer() {
		return (PackSelectionModelAccessor) (Object) ((PackSelectionScreenAccessor) (Object) screen).dragit$getOrganizer();
	}

	private List<Pack> profiles(int list) {
		return list == AVAILABLE ? organizer().dragit$getDisabledPacks() : organizer().dragit$getEnabledPacks();
	}

	/** Vanilla's "the model changed" callback: rebuilds both widget lists from the organizer's lists. */
	private void refreshLists() {
		//? if >=1.21.10 {
		/*organizer().dragit$getUpdateCallback().accept(null);
		*///?} else {
		organizer().dragit$getUpdateCallback().run();
		//?}
	}

	private Pack findProfile(String id) {
		for (int i = 0; i < 2; i++) {
			for (Pack profile : profiles(i)) {
				if (profile.getId().equals(id)) {
					return profile;
				}
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- input (called from the mixins)

	/**
	 * Raw mouse button event, before vanilla sees it.
	 *
	 * @return true if the event was consumed and must not reach vanilla
	 */
	/** @param mods modifier bits of the event (Shift selects several packs, Ctrl sends them to the other list) */
	public boolean onMouseButton(double x, double y, int button, boolean pressed, int mods) {
		if (!ready()) {
			return false;
		}
		try {
			pointerX = x;
			pointerY = y;
			return pressed ? onPress(x, y, button, mods) : onRelease(x, y, button);
		} catch (RuntimeException e) {
			fail(e);
			return false;
		}
	}

	private boolean onPress(double x, double y, int button, int mods) {
		if (phase == Phase.PENDING || phase == Phase.DRAGGING) {
			return true; // another button during our gesture: ignore it completely
		}
		if (button != GuiCompat.MOUSE_LEFT) {
			return false;
		}
		if (phase == Phase.SETTLING) {
			finishSettle();
		}
		swallowRelease = false;
		boolean ctrl = GuiCompat.hasControl(mods);
		boolean shift = GuiCompat.hasShift(mods);
		Grab grab = findGrabbable(x, y);
		if (grab != null && (ctrl || shift)) {
			modifierClick(grab, ctrl, shift);
			swallowRelease = true; // the whole click is ours; vanilla must not see the matching release either
			return true;
		}
		if (!ctrl && !shift && (grab == null || !isMultiMember(grab.list(), grab.name()))) {
			// Like Explorer: a plain click outside the selection (or on empty space) drops it. A click *on* a selected pack
			// keeps it until the button is released, so the selection survives a click on its arrow.
			clearMulti();
			if (grab != null) {
				multiAnchor = grab.name();
			}
		}
		if (grab == null) {
			return false; // vanilla handles it (arrows, selection, scrollbar, pinned packs...)
		}
		phase = Phase.PENDING;
		buttonDown = true;
		srcList = grab.list();
		packId = grab.name();
		pressX = x;
		pressY = y;
		return true;
	}

	private boolean onRelease(double x, double y, int button) {
		if (button != GuiCompat.MOUSE_LEFT) {
			return false;
		}
		buttonDown = false;
		if (swallowRelease) {
			swallowRelease = false;
			return true;
		}
		if (phase == Phase.PENDING) {
			// Nothing moved: this was a plain click. Give vanilla the press it never saw, then the release.
			phase = Phase.IDLE;
			String clicked = packId;
			packId = null;
			if (isMultiMember(srcList, clicked)) {
				if (onEnableDisableArrow(srcList, clicked, pressX, pressY)) {
					moveSelection(); // the arrow of a selected pack acts on the whole selection
					return true;
				}
				clearMulti(); // Explorer: a click on a selected item collapses the selection to that item
				multiAnchor = clicked;
			}
			GuiCompat.click(screen, pressX, pressY);
			GuiCompat.release(screen, x, y);
			return true;
		}
		if (phase == Phase.DRAGGING) {
			drop(x, y);
			return true;
		}
		return false;
	}

	/** Esc. @return true if it was used to cancel a drag (the screen must then stay open). */
	public boolean onEscape() {
		if (phase == Phase.PENDING) {
			phase = Phase.IDLE;
			packId = null;
			swallowRelease = buttonDown;
			return true;
		}
		if (phase == Phase.DRAGGING) {
			cancelDrag();
			return true;
		}
		return false;
	}

	/** The screen was re-laid out (window resized, GUI scale changed, returned from a dialog): drop any gesture safely. */
	public void onLayoutChanged() {
		if (phase != Phase.IDLE) {
			reset();
		}
	}

	/** Forget the gesture. If the button is still held, its release is swallowed. */
	public void reset() {
		boolean gesture = phase == Phase.PENDING || phase == Phase.DRAGGING;
		phase = Phase.IDLE;
		packId = null;
		dragIds.clear();
		dragProfiles.clear();
		followers.clear();
		drop = DropTarget.Result.NONE;
		lift = 0;
		swallowRelease = gesture && buttonDown;
	}

	private void fail(RuntimeException e) {
		if (!faulted) {
			faulted = true;
			DragIt.LOGGER.error("Drag & drop failed, cancelling the gesture", e);
		}
		reset();
	}

	private Grab findGrabbable(double x, double y) {
		for (int i = 0; i < 2; i++) {
			TransferableSelectionList list = list(i);
			if (!list.isMouseOver(x, y)) {
				continue;
			}
			if (ListGeometry.scrollbarVisible(list) && x >= ListGeometry.scrollbarX(list)) {
				return null;
			}
			int index = ListGeometry.hit(list, x, y);
			if (index < 0) {
				return null;
			}
			TransferableSelectionList.PackEntry entry = ListGeometry.entries(list).get(index);
			PackSelectionModel.Entry pack = ListGeometry.packOf(entry);
			if (pack.isFixedPosition()) {
				return null; // pinned packs are never lifted
			}
			return new Grab(i, index, entry.getPackId());
		}
		return null;
	}

	// ---------------------------------------------------------------- multi-selection (Shift) and quick move (Ctrl)

	/** Right edge (exclusive) of highlights and cards of this list; stops short of the scrollbar. */
	public static int highlightRight(TransferableSelectionList list) {
		return ListGeometry.cardRight(list);
	}

	/**
	 * Draws the part of the animated multi-selection outline that lies on one row, under the row's content and in the row's
	 * own (possibly sliding) frame. {@code left..right} and {@code top..bottom} are the row's whole cell.
	 */
	public void drawMultiSelection(GuiGraphics context, TransferableSelectionList widget, int index, int left, int top, int right, int bottom) {
		int list = listOf(widget);
		if (list >= 0) {
			DragRenderer.drawSelection(context, bands, list, index, left, top, right, bottom);
		}
	}

	/** Same for vanilla's single selection (the white frame), which the mod draws itself so that it can fade. */
	public float selectionAlpha(TransferableSelectionList widget, String name) {
		Double alpha = selectionAlpha.get(listOf(widget) + name);
		return alpha == null ? 0F : alpha.floatValue();
	}

	/**
	 * The screen holds one selection, in one list. Vanilla does not guarantee it: moving the keyboard focus to the other list
	 * leaves the old list's selected row behind (its frame stays, and a list that was ever focused keeps drawing it white), and
	 * the keyboard can select a row while a multi-selection exists. The newer selection wins and the older one is dropped.
	 */
	private void settleSelection() {
		boolean[] fresh = new boolean[2];
		String[] now = new String[2];
		for (int i = 0; i < 2; i++) {
			TransferableSelectionList.PackEntry entry = ListGeometry.selected(list(i));
			now[i] = entry == null ? null : entry.getPackId();
			fresh[i] = now[i] != null && !now[i].equals(lastSelected[i]);
		}
		if (now[0] != null && now[1] != null) {
			int keep = fresh[0] != fresh[1] ? (fresh[0] ? AVAILABLE : SELECTED) : (screen.getFocused() == list(AVAILABLE) ? AVAILABLE : SELECTED);
			int stale = 1 - keep;
			list(stale).setSelected(null);
			now[stale] = null;
			fresh[stale] = false;
		}
		if ((fresh[0] || fresh[1]) && !multi.isEmpty()) {
			clearMulti();
		}
		System.arraycopy(now, 0, lastSelected, 0, 2);
	}

	private void updateHighlightAlpha() {
		settleSelection();
		double blend = 1.0 - Math.exp(-dt * DragItSettings.SELECTION_FADE_RATE);
		boolean edited = !multi.equals(bandSelection);
		if (edited) {
			bandSelection.clear();
			bandSelection.addAll(multi);
		}
		for (int i = 0; i < 2; i++) {
			bands.update(i, ListGeometry.names(list(i)), multi, edited, bandSeed, dt);
		}
		bandSeed = null;
		Set<String> selected = new HashSet<>();
		for (int i = 0; i < 2; i++) {
			TransferableSelectionList.PackEntry entry = ListGeometry.selected(list(i));
			if (entry != null) {
				selected.add(i + entry.getPackId());
			}
		}
		fade(selectionAlpha, selected, blend);
	}

	/** Eases every key of {@code alphas} towards 1 if it is in {@code active}, else towards 0 (and forgets it once invisible). */
	private static void fade(Map<String, Double> alphas, Set<String> active, double blend) {
		if (active.isEmpty() && alphas.isEmpty()) {
			return;
		}
		for (String key : active) {
			double alpha = alphas.getOrDefault(key, 0.0);
			alpha += (1.0 - alpha) * blend;
			alphas.put(key, alpha > 0.995 ? 1.0 : alpha);
		}
		alphas.entrySet().removeIf(entry -> {
			if (active.contains(entry.getKey())) {
				return false;
			}
			double alpha = entry.getValue() * (1.0 - blend);
			entry.setValue(alpha);
			return alpha < 0.01;
		});
	}

	/** Forgets the selection but not the anchor (Explorer keeps its "focus item" after a plain click). */
	private void clearMulti() {
		multi.clear();
		multiList = -1;
	}

	private boolean isMultiMember(int list, String name) {
		return multiList == list && multi.contains(name);
	}

	private static boolean isFixed(TransferableSelectionList.PackEntry entry) {
		return ListGeometry.packOf(entry).isFixedPosition();
	}

	/**
	 * Explorer rules. The anchor is the pack last clicked without Shift.
	 * <ul>
	 * <li>Ctrl+click: toggles the pack and makes it the anchor.</li>
	 * <li>Shift+click: selects the range anchor..clicked, replacing the selection; the anchor stays.</li>
	 * <li>Ctrl+Shift+click: adds that range to the selection; the anchor stays.</li>
	 * </ul>
	 * The selection lives in one list; a click in the other list starts a new one there. Vanilla's own single selection
	 * (a plain click) counts as the first selected pack.
	 */
	private void modifierClick(Grab grab, boolean ctrl, boolean shift) {
		int id = grab.list();
		TransferableSelectionList list = list(id);
		String name = grab.name();
		TransferableSelectionList.PackEntry vanilla = ListGeometry.selected(list);
		boolean seed = vanilla != null && !isFixed(vanilla);
		if (multiList != id) {
			multi.clear();
			multiList = id;
		}
		if (multi.isEmpty() && seed) {
			multi.add(vanilla.getPackId());
		}

		if (shift) {
			String anchor = multiAnchor;
			if (anchor == null || ListGeometry.indexOf(list, anchor) < 0) {
				anchor = seed ? vanilla.getPackId() : name;
			}
			if (!ctrl) {
				multi.clear();
			}
			int a = ListGeometry.indexOf(list, anchor);
			int b = ListGeometry.indexOf(list, name);
			for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
				TransferableSelectionList.PackEntry entry = ListGeometry.entries(list).get(i);
				if (!isFixed(entry)) {
					multi.add(entry.getPackId());
				}
			}
			multiAnchor = anchor;
			bandSeed = anchor; // the outline grows from the anchor towards the clicked pack
		} else {
			if (!multi.remove(name)) {
				multi.add(name);
			}
			multiAnchor = name;
			bandSeed = seed ? vanilla.getPackId() : null; // a lone pack that was selected the vanilla way turns into the first row of the outline
		}
		if (multi.isEmpty()) {
			multiList = -1;
		}
		clearVanillaSelection(); // the multi-selection replaces vanilla's single highlight
		focusList(id);
	}

	/** Ctrl+A: selects every movable pack of the list under the cursor (else of the list that already holds the selection). */
	private boolean selectAll() {
		int id = -1;
		for (int i = 0; i < 2; i++) {
			if (list(i).isMouseOver(pointerX, pointerY)) {
				id = i;
			}
		}
		if (id < 0) {
			id = multiList;
		}
		if (id < 0) {
			return false;
		}
		multi.clear();
		multiList = id;
		for (TransferableSelectionList.PackEntry entry : ListGeometry.entries(list(id))) {
			if (!isFixed(entry)) {
				multi.add(entry.getPackId());
			}
		}
		if (multi.isEmpty()) {
			multiList = -1;
		}
		bandSeed = null;
		clearVanillaSelection();
		return true;
	}

	/**
	 * Keyboard. Enter / Space send the selection to the other list (vanilla's Enter toggles the single selected pack,
	 * so this is the same key for a whole selection); Ctrl+A selects all. Nothing here fires while the search box has the
	 * focus: the keys are its text then.
	 *
	 * @return true if the key was used
	 */
	public boolean onKey(int keyCode, int modifiers) {
		if (!ready() || phase != Phase.IDLE || screen.getFocused() instanceof EditBox) {
			return false;
		}
		try {
			if (keyCode == GuiCompat.KEY_A && GuiCompat.hasControl(modifiers)) {
				return selectAll();
			}
			boolean space = keyCode == GuiCompat.KEY_SPACE;
			boolean toggle = space || keyCode == GuiCompat.KEY_ENTER || keyCode == GuiCompat.KEY_KP_ENTER;
			if (toggle && !multi.isEmpty()) {
				moveSelection();
				return true;
			}
			if (space) {
				return toggleSingle();
			}
		} catch (RuntimeException e) {
			fail(e);
		}
		return false;
	}

	/** Space on the single pack of vanilla's own selection: vanilla's pack list only reacts to Enter from 1.21.10, Space does the same here. */
	private boolean toggleSingle() {
		for (int i = 0; i < 2; i++) {
			if (screen.getFocused() == list(i)) {
				TransferableSelectionList.PackEntry entry = ListGeometry.selected(list(i));
				if (entry != null) {
					entry.keyboardSelection();
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Editing the selection counts as a click on the list: the keyboard focus leaves the search box (the mod swallows these
	 * clicks, so vanilla never moves it), and Enter, Space and Ctrl+A then act on the selection instead of being its text.
	 */
	private void focusList(int id) {
		if (screen.getFocused() != list(id)) {
			screen.setFocused(list(id));
		}
	}

	/** True when a plain click at {@code x, y} would hit the enable/disable arrow of this pack (vanilla's own hit zones). */
	private boolean onEnableDisableArrow(int listId, String name, double x, double y) {
		TransferableSelectionList.PackEntry entry = ListGeometry.find(list(listId), name);
		return entry != null && ListGeometry.onToggleArrow(list(listId), entry, x, y);
	}

	/** Clears vanilla's single selection of both lists (the multi-selection replaces it, and a lifted pack has none). */
	private void clearVanillaSelection() {
		for (int i = 0; i < 2; i++) {
			list(i).setSelected(null);
		}
	}

	private void moveSelection() {
		if (!multi.isEmpty() && multiList >= 0) {
			moveGroup(multiList, new ArrayList<>(multi));
		}
	}

	/**
	 * Sends the packs to the opposite list through vanilla's own enable()/disable(), keeping their relative order. The
	 * selection stays on the moved packs, so sending them again brings them back.
	 */
	private void moveGroup(int from, List<String> names) {
		int to = from == AVAILABLE ? SELECTED : AVAILABLE;

		List<PackSelectionModel.Entry> packs = new ArrayList<>();
		List<Pack> moved = new ArrayList<>();
		for (TransferableSelectionList.PackEntry entry : ListGeometry.entries(list(from))) {
			if (!names.contains(entry.getPackId())) {
				continue;
			}
			PackSelectionModel.Entry pack = ListGeometry.packOf(entry);
			Pack profile = findProfile(entry.getPackId());
			boolean movable = profile != null && !pack.isFixedPosition()
				&& (to == SELECTED ? pack.getCompatibility().isCompatible() : pack.canUnselect());
			if (movable) {
				packs.add(pack);
				moved.add(profile);
			}
		}
		if (packs.isEmpty()) {
			return;
		}
		for (PackSelectionModel.Entry pack : packs) {
			if (to == SELECTED) {
				pack.select();
			} else {
				pack.unselect();
			}
		}
		if (to == SELECTED) {
			keepRelativeOrder(profiles(SELECTED), moved);
		}
		organizer().dragit$refreshEnabledProfiles();
		ListGeometry.clampScroll(list(AVAILABLE));
		ListGeometry.clampScroll(list(SELECTED));
		followToList(moved, to);
	}

	/**
	 * After a group move the selection is exactly the packs that moved. Those that could not go (required, incompatible) stay
	 * where they are, unselected: the selection never spans both lists.
	 */
	private void followToList(List<Pack> moved, int to) {
		Set<String> ids = new HashSet<>();
		for (Pack pack : moved) {
			ids.add(pack.getId());
		}
		multi.retainAll(ids);
		if (multi.isEmpty()) {
			clearMulti();
		} else {
			multiList = to;
		}
	}

	/** Vanilla inserts every enabled pack by its own default position; put the moved ones back in the order they had on screen. */
	private void keepRelativeOrder(List<Pack> target, List<Pack> inOrder) {
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < target.size(); i++) {
			if (inOrder.contains(target.get(i))) {
				slots.add(i);
			}
		}
		if (slots.size() != inOrder.size()) {
			return;
		}
		boolean changed = false;
		for (int k = 0; k < slots.size(); k++) {
			if (target.get(slots.get(k)) != inOrder.get(k)) {
				target.set(slots.get(k), inOrder.get(k));
				changed = true;
			}
		}
		if (changed) {
			refreshLists();
		}
	}

	/** Forgets multi-selected packs that no longer exist (folder refresh) and follows the rest to the list they are in. */
	private void pruneMulti() {
		if (multiAnchor != null && ListGeometry.indexOf(list(AVAILABLE), multiAnchor) < 0 && ListGeometry.indexOf(list(SELECTED), multiAnchor) < 0) {
			multiAnchor = null;
		}
		if (multi.isEmpty()) {
			return;
		}
		multi.removeIf(name -> ListGeometry.indexOf(list(AVAILABLE), name) < 0 && ListGeometry.indexOf(list(SELECTED), name) < 0);
		if (multi.isEmpty()) {
			clearMulti();
			return;
		}
		int list = ListGeometry.indexOf(list(SELECTED), multi.iterator().next()) >= 0 ? SELECTED : AVAILABLE;
		multi.removeIf(name -> ListGeometry.indexOf(list(list), name) < 0); // the selection never spans both lists
		multiList = list;
	}

	// ---------------------------------------------------------------- per-frame update

	/** Start of {@code Screen.render}: advance the state machine and the animations by the real elapsed time. */
	public void beginFrame() {
		long now = System.nanoTime();
		dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1.0e9);
		lastNanos = now;
		if (!ready()) {
			return;
		}
		try {
			readPointer();
			pruneMulti();
			updateHighlightAlpha();
			if ((phase == Phase.PENDING || phase == Phase.DRAGGING) && !GuiCompat.leftButtonHeld()) {
				onRelease(pointerX, pointerY, GuiCompat.MOUSE_LEFT); // release event got lost (focus change etc.)
			}
			switch (phase) {
				case PENDING -> tickPending();
				case DRAGGING -> tickDragging();
				case SETTLING -> tickSettling();
				default -> {
				}
			}
			updateModel();
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	/** End of {@code Screen.render}: draw the placeholder and the lifted entry on top of everything. */
	public void endFrame(GuiGraphics context, float delta) {
		if (!ready()) {
			return;
		}
		try {
			DragRenderer.render(this, context, delta);
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	private void readPointer() {
		Minecraft client = Minecraft.getInstance();
		Window window = client.getWindow();
		pointerX = client.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
		pointerY = client.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
	}

	private double thresholdGui() {
		return DragItSettings.DRAG_THRESHOLD_PX / Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
	}

	private void tickPending() {
		if (Math.hypot(pointerX - pressX, pointerY - pressY) >= thresholdGui()) {
			startDrag();
		}
	}

	private void startDrag() {
		TransferableSelectionList source = list(srcList);
		int index = ListGeometry.indexOf(source, packId);
		if (index < 0) {
			reset();
			return;
		}
		// Dragging a selected pack lifts the whole selection (Explorer); any other pack goes alone.
		boolean group = isMultiMember(srcList, packId);
		dragIds.clear();
		dragProfiles.clear();
		for (TransferableSelectionList.PackEntry entry : ListGeometry.entries(source)) {
			String name = entry.getPackId();
			Pack profile = name.equals(packId) || (group && multi.contains(name)) ? findProfile(name) : null;
			if (profile != null) {
				dragIds.add(name);
				dragProfiles.add(profile);
			}
		}
		if (!dragIds.contains(packId)) {
			reset();
			return;
		}
		double entryX = ListGeometry.contentLeft(source);
		double entryY = ListGeometry.rowTop(source, index) + animator.offset(srcList, packId, index, ListGeometry.itemHeight(source));
		grabDX = pressX - entryX;
		grabDY = pressY - entryY;
		ghostX = ghostTX = entryX;
		ghostY = ghostTY = entryY;
		lift = 0;
		Arrays.fill(blockedMix, 0);
		phase = Phase.DRAGGING;
		clearVanillaSelection();
		tickDragging();
	}

	private void tickDragging() {
		Pack profile = findProfile(packId);
		boolean intact = profile != null;
		for (String id : dragIds) {
			intact &= ListGeometry.indexOf(list(srcList), id) >= 0;
		}
		if (!intact) {
			reset(); // a pack vanished or changed lists under us (folder refresh, keyboard)
			return;
		}
		ghostTX = pointerX - grabDX;
		ghostTY = pointerY - grabDY;
		double follow = 1.0 - Math.exp(-dt * DragItSettings.GHOST_FOLLOW_RATE);
		ghostX += (ghostTX - ghostX) * follow;
		ghostY += (ghostTY - ghostY) * follow;
		lift += (1.0 - lift) * (1.0 - Math.exp(-dt * DragItSettings.LIFT_RATE));
		drop = computeDrop(profile, pointerX, pointerY);
		autoScroll();
	}

	private DropTarget.Result computeDrop(Pack profile, double x, double y) {
		@SuppressWarnings("unchecked")
		List<Pack>[] lists = new List[]{profiles(AVAILABLE), profiles(SELECTED)};
		TransferableSelectionList[] widgets = {list(AVAILABLE), list(SELECTED)};
		double cardCenterY = y - grabDY + ListGeometry.entryHeight(list(srcList)) / 2.0;
		DropTarget.Block[] refusal = {refusal(AVAILABLE), refusal(SELECTED)};
		return DropTarget.compute(widgets, lists, profile, dragProfiles, srcList, x, y, cardCenterY, refusal);
	}

	/** Why none of the dragged packs can go to {@code target}; {@link DropTarget.Block#NONE} if at least one can. */
	private DropTarget.Block refusal(int target) {
		if (!moversTo(target).isEmpty()) {
			return DropTarget.Block.NONE;
		}
		return target == AVAILABLE ? DropTarget.Block.REQUIRED : DropTarget.Block.INCOMPATIBLE;
	}

	/**
	 * The dragged packs that would actually end up in {@code target}: all of them inside their own list; across lists a
	 * group skips what cannot go there (incompatible packs into "selected", required ones into "available"). A lone
	 * incompatible pack stays droppable because it gets vanilla's confirmation dialog.
	 */
	private List<Pack> moversTo(int target) {
		if (target == srcList || dragProfiles.size() == 1 && target == SELECTED) {
			return dragProfiles;
		}
		List<Pack> movers = new ArrayList<>();
		for (Pack profile : dragProfiles) {
			if (target == SELECTED ? profile.getCompatibility().isCompatible() : !profile.isRequired()) {
				movers.add(profile);
			}
		}
		return movers;
	}

	/**
	 * Whether a card of the lifted stack is shown refused at the current cursor position. A refused spot (pinned zone, a list
	 * that takes none of the packs) reddens every card; over a list that takes only some of them, just the cards of the packs
	 * that would stay behind. Card 0 is the grabbed pack, cards 1.. stand for the others in list order; the last card of a
	 * stack of more than three stands for all the rest and is red if any of them cannot go.
	 */
	private boolean cardRefused(int card) {
		if (drop.blocked()) {
			return true;
		}
		if (drop.list() == DropTarget.NO_LIST || drop.list() == srcList) {
			return false;
		}
		List<Pack> movers = moversTo(drop.list());
		List<Pack> represented = new ArrayList<>();
		for (Pack profile : dragProfiles) {
			boolean grabbed = profile.getId().equals(packId);
			if (grabbed == (card == 0)) {
				represented.add(profile);
			}
		}
		if (card > 0) {
			int from = card - 1;
			int to = card == blockedMix.length - 1 ? represented.size() : card;
			represented = represented.subList(Math.min(from, represented.size()), Math.min(to, represented.size()));
		}
		return !movers.containsAll(represented);
	}

	private void autoScroll() {
		int edge = DragItSettings.AUTOSCROLL_EDGE;
		int slack = DragItSettings.AUTOSCROLL_SLACK;
		for (int i = 0; i < 2; i++) {
			TransferableSelectionList list = list(i);
			int pad = DragItSettings.COLUMN_PADDING;
			if (pointerX < ListGeometry.left(list) - pad || pointerX > ListGeometry.right(list) + pad) {
				continue;
			}
			double speed = 0;
			if (pointerY < ListGeometry.top(list) + edge && pointerY > ListGeometry.top(list) - slack) {
				speed = -Math.min(1.5, (ListGeometry.top(list) + edge - pointerY) / edge);
			} else if (pointerY > ListGeometry.bottom(list) - edge && pointerY < ListGeometry.bottom(list) + slack) {
				speed = Math.min(1.5, (pointerY - (ListGeometry.bottom(list) - edge)) / edge);
			}
			if (speed != 0) {
				list.setScrollAmount(ListGeometry.scrollAmount(list) + speed * DragItSettings.AUTOSCROLL_MAX_SPEED * dt);
			}
		}
	}

	/** A card of the dropped stack other than the one the cursor held; {@code card} is its layer in the stack (1 or 2). */
	static final class Follower {
		final String name;
		final int card;
		double x;
		double y;
		/** 0..1: how red the card is; eases back to 0 on the way (a pack that stayed behind starts red). */
		double mix;
		/** List its row is in once the drop is applied. */
		int list = -1;

		Follower(String name, int card, double x, double y) {
			this.name = name;
			this.card = card;
			this.x = x;
			this.y = y;
		}
	}

	/** Starts the glide of the lifted card, and of every other card of the stack, into the rows they now have. */
	private void beginSettle(int list) {
		followers.clear();
		int k = 0;
		for (String id : dragIds) {
			if (id.equals(packId)) {
				continue;
			}
			int card = Math.min(++k, 2);
			int offset = (int) Math.round(card * 4 * lift); // where DragRenderer drew this layer of the stack
			Follower f = new Follower(id, card, ghostX + offset, ghostY + offset);
			f.list = ListGeometry.indexOf(list(list), id) >= 0 ? list : 1 - list; // a pack that could not go stays where it was
			f.mix = list == srcList || f.list != list ? blockedMix[card] : 0;
			followers.add(f);
		}
		settleList = list;
		phase = Phase.SETTLING;
	}

	private void tickSettling() {
		TransferableSelectionList list = list(settleList);
		int index = ListGeometry.indexOf(list, packId);
		if (index < 0) {
			finishSettle();
			return;
		}
		double glide = 1.0 - Math.exp(-dt * DragItSettings.SETTLE_RATE);
		followers.removeIf(f -> !glideFollower(f, glide));
		ghostTX = ListGeometry.contentLeft(list);
		ghostTY = ListGeometry.rowTop(list, index);
		ghostX += (ghostTX - ghostX) * glide;
		ghostY += (ghostTY - ghostY) * glide;
		lift -= lift * (1.0 - Math.exp(-dt * DragItSettings.LIFT_RATE));
		if (Math.abs(ghostTX - ghostX) < 0.3 && Math.abs(ghostTY - ghostY) < 0.3 && lift < 0.03 && followers.isEmpty()) {
			finishSettle();
		}
	}

	/** Moves a follower towards its row; false once it has arrived (or its pack is gone), when its row is shown again. */
	private boolean glideFollower(Follower f, double glide) {
		TransferableSelectionList list = list(f.list);
		int index = ListGeometry.indexOf(list, f.name);
		if (index < 0) {
			return false;
		}
		double tx = ListGeometry.contentLeft(list);
		double ty = ListGeometry.rowTop(list, index);
		f.x += (tx - f.x) * glide;
		f.y += (ty - f.y) * glide;
		f.mix -= f.mix * (1.0 - Math.exp(-dt * DragItSettings.LIFT_RATE));
		if (Math.abs(tx - f.x) < 0.3 && Math.abs(ty - f.y) < 0.3 && lift < 0.03) {
			animator.snap(f.list, f.name, index, ListGeometry.itemHeight(list));
			return false;
		}
		return true;
	}

	private void finishSettle() {
		if (packId != null && settleList >= 0 && ready()) {
			TransferableSelectionList list = list(settleList);
			int index = ListGeometry.indexOf(list, packId);
			if (index >= 0) {
				animator.snap(settleList, packId, index, ListGeometry.itemHeight(list));
			}
		}
		for (Follower f : followers) {
			if (f.list >= 0 && ready()) {
				int index = ListGeometry.indexOf(list(f.list), f.name);
				if (index >= 0) {
					animator.snap(f.list, f.name, index, ListGeometry.itemHeight(list(f.list)));
				}
			}
		}
		followers.clear();
		phase = Phase.IDLE;
		packId = null;
		lift = 0;
	}

	/** Computes where every entry of both lists wants to be and eases it there; also drives the placeholder. */
	private void updateModel() {
		double indicatorBlend = 1.0 - Math.exp(-dt * DragItSettings.INDICATOR_RATE);
		for (int card = 0; card < blockedMix.length; card++) {
			blockedMix[card] += ((phase == Phase.DRAGGING && cardRefused(card) ? 1.0 : 0.0) - blockedMix[card]) * indicatorBlend;
		}
		for (int i = 0; i < 2; i++) {
			TransferableSelectionList list = list(i);
			List<TransferableSelectionList.PackEntry> children = ListGeometry.entries(list);
			int n = children.size();
			int itemHeight = ListGeometry.itemHeight(list);
			boolean dragging = phase == Phase.DRAGGING;
			int gap = dragging && drop.valid() && drop.list() == i ? drop.gap() : -1;
			int gapSize = gap >= 0 ? moversTo(i).size() : 0; // rows the placeholder opens up

			List<String> names = ListGeometry.names(list);
			int[] slots = new int[n];
			int lifted = 0; // dragged rows above this one
			for (int j = 0; j < n; j++) {
				if (dragging && i == srcList && dragIds.contains(names.get(j))) {
					slots[j] = j;
					lifted++;
					continue;
				}
				int rank = j - lifted; // position once the dragged packs are lifted out
				slots[j] = gap >= 0 && rank >= gap ? rank + gapSize : rank;
			}
			animator.update(i, names, slots, itemHeight, dt);

			Indicator indicator = indicators[i];
			boolean active = dragging && drop.list() == i && (drop.valid() || drop.blocked());
			if (active) {
				double target = (double) drop.gap() * itemHeight;
				if (indicator.alpha < 0.02) {
					indicator.y = target;
				} else {
					indicator.y += (target - indicator.y) * indicatorBlend;
				}
				indicator.block = drop.block();
			}
			indicator.alpha += ((active ? 1.0 : 0.0) - indicator.alpha) * indicatorBlend;
			if (!active && indicator.alpha < 0.01) {
				indicator.alpha = 0;
			}
		}
	}

	// ---------------------------------------------------------------- dropping

	private void cancelDrag() {
		beginSettle(srcList);
		drop = DropTarget.Result.NONE;
		swallowRelease = buttonDown;
	}

	private enum Outcome {
		APPLIED, UNCHANGED, REJECTED, DEFERRED
	}

	private void drop(double x, double y) {
		Pack profile = findProfile(packId);
		if (profile == null) {
			reset();
			return;
		}
		pointerX = x;
		pointerY = y;
		DropTarget.Result result = computeDrop(profile, x, y);
		drop = DropTarget.Result.NONE;
		Outcome outcome = apply(profile, result);
		beginSettle(outcome == Outcome.APPLIED ? result.list() : srcList);
		if (outcome == Outcome.APPLIED) {
			ListGeometry.clampScroll(list(AVAILABLE));
			ListGeometry.clampScroll(list(SELECTED));
		}
	}

	private Outcome apply(Pack profile, DropTarget.Result result) {
		if (!result.valid()) {
			return Outcome.REJECTED;
		}
		if (result.list() == srcList) {
			// Re-order inside one list: edit the organizer's list directly, one rebuild; the group keeps its inner order.
			List<Pack> profiles = profiles(srcList);
			List<Pack> before = new ArrayList<>(profiles);
			if (!profiles.containsAll(dragProfiles)) {
				return Outcome.REJECTED;
			}
			profiles.removeAll(dragProfiles);
			profiles.addAll(Math.min(result.modelGap(), profiles.size()), dragProfiles);
			if (profiles.equals(before)) {
				return Outcome.UNCHANGED;
			}
			refreshLists();
			return Outcome.APPLIED;
		}

		List<Pack> movers = moversTo(result.list());
		List<PackSelectionModel.Entry> packs = new ArrayList<>();
		for (Pack mover : movers) {
			TransferableSelectionList.PackEntry entry = ListGeometry.find(list(srcList), mover.getId());
			if (entry == null) {
				return Outcome.REJECTED;
			}
			PackSelectionModel.Entry pack = ListGeometry.packOf(entry);
			if (result.list() == AVAILABLE && !pack.canUnselect()) {
				continue;
			}
			packs.add(pack);
		}
		if (packs.isEmpty()) {
			return Outcome.REJECTED;
		}
		if (result.list() == SELECTED && packs.size() == 1 && !packs.get(0).getCompatibility().isCompatible()) {
			confirmIncompatible(packs.get(0), movers.get(0), result.modelGap());
			return Outcome.DEFERRED;
		}
		crossList(packs, movers, result.list(), result.modelGap());
		return Outcome.APPLIED;
	}

	/**
	 * Moves packs to the other list through vanilla's own enable()/disable() (which also handles the high-contrast
	 * option and refreshes the manager), then slides them as one block to the exact gap (an index into the model's list).
	 */
	private void crossList(List<PackSelectionModel.Entry> packs, List<Pack> moved, int targetList, int gap) {
		for (PackSelectionModel.Entry pack : packs) {
			if (targetList == SELECTED) {
				pack.select();
			} else {
				pack.unselect();
			}
		}
		List<Pack> profiles = profiles(targetList);
		List<Pack> before = new ArrayList<>(profiles);
		profiles.removeAll(moved);
		profiles.addAll(Math.min(gap, profiles.size()), moved);
		if (!profiles.equals(before)) {
			refreshLists();
		}
		organizer().dragit$refreshEnabledProfiles();
		if (!multi.isEmpty()) {
			followToList(moved, targetList);
		}
	}

	/** Same standard confirmation dialog vanilla shows when enabling an incompatible pack. */
	private void confirmIncompatible(PackSelectionModel.Entry pack, Pack profile, int gap) {
		Minecraft client = Minecraft.getInstance();
		Component message = pack.getCompatibility().getConfirmation();
		GuiCompat.setScreen(client, new ConfirmScreen(confirmed -> {
			GuiCompat.setScreen(client, screen);
			if (confirmed) {
				crossList(List.of(pack), List.of(profile), SELECTED, gap);
				ListGeometry.clampScroll(list(AVAILABLE));
				ListGeometry.clampScroll(list(SELECTED));
			}
		}, Component.translatable("pack.incompatible.confirm.title"), message));
	}
}
