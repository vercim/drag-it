package com.dragit.drag;

import com.dragit.DragIt;
import com.dragit.DragItSettings;
import com.dragit.mixin.accessor.PackScreenAccessor;
import com.dragit.mixin.accessor.ResourcePackOrganizerAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Drag & drop state machine of one {@link PackScreen}.
 *
 * <pre>
 * IDLE --press on a movable entry--> PENDING --cursor travels past the threshold--> DRAGGING
 * PENDING --release--> IDLE (the swallowed click is replayed to vanilla)
 * DRAGGING --release / Esc--> SETTLING (the lifted entry glides into its slot) --arrived--> IDLE
 * </pre>
 *
 * Everything is driven by real time ({@code System.nanoTime()} per frame), never by ticks. All order/enable changes go
 * through the vanilla {@link ResourcePackOrganizer}; this class stores no pack state of its own.
 */
public final class DragController {
	public static final int AVAILABLE = 0;
	public static final int SELECTED = 1;

	public enum Phase {
		IDLE, PENDING, DRAGGING, SETTLING
	}

	/** Cursor position in GUI coordinates; replaceable so the dev self-test can drive the screen without a real mouse. */
	public interface Pointer {
		double x();

		double y();
	}

	/** Number of unexpected exceptions swallowed (and logged) by the controller; the dev self-test asserts this stays 0. */
	public static volatile int faultCount;

	private static volatile Pointer pointerOverride;
	private static boolean pollRealButton = true;
	private static boolean drawingGhost;

	/** Dev/test hook: use a synthetic cursor and do not consult the real mouse button. */
	public static void setSyntheticPointer(Pointer pointer) {
		pointerOverride = pointer;
		pollRealButton = pointer == null;
	}

	/** True while the lifted entry is being drawn through the entry's own {@code render()} (so the render hook leaves it alone). */
	public static boolean isDrawingGhost() {
		return drawingGhost;
	}

	static void setDrawingGhost(boolean value) {
		drawingGhost = value;
	}

	public static DragController of(PackScreen screen) {
		return ((ControllerHolder) screen).dragit$getController();
	}

	static final class Indicator {
		double y;
		double alpha;
		boolean blocked;
	}

	private record Grab(int list, int index, String name) {
	}

	final PackScreen screen;
	final EntryAnimator animator = new EntryAnimator();
	final Indicator[] indicators = {new Indicator(), new Indicator()};

	Phase phase = Phase.IDLE;
	int srcList;
	String packId;
	/** Every pack lifted by the current drag (list order); more than one when a multi-selection is dragged. */
	final List<String> dragIds = new ArrayList<>();
	final List<ResourcePackProfile> dragProfiles = new ArrayList<>();
	int settleList;
	DropTarget.Result drop = DropTarget.Result.NONE;
	double ghostX;
	double ghostY;
	double lift;
	double pointerX;
	double pointerY;

	private final Set<String> multi = new LinkedHashSet<>();
	private final Map<String, Double> multiAlpha = new HashMap<>();
	private final Map<String, Double> selectionAlpha = new HashMap<>();
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

	public DragController(PackScreen screen) {
		this.screen = screen;
	}

	// ---------------------------------------------------------------- queries used by the render hooks

	public Phase phase() {
		return phase;
	}

	// Read-only debug view used by the dev self-test.
	public boolean debugDropBlocked() {
		return drop.blocked();
	}

	public boolean debugDropValid() {
		return drop.valid();
	}

	public int debugDropGap() {
		return drop.gap();
	}

	public int debugDropList() {
		return drop.list();
	}

	/** The dragged entry is drawn as the lifted card instead of in its row. */
	public boolean isHidden(String name) {
		if (phase == Phase.DRAGGING) {
			return dragIds.contains(name); // the whole group leaves its rows
		}
		return phase == Phase.SETTLING && name.equals(packId);
	}

	/** Vanilla "hovered" decorations (arrows) are meaningless while entries slide around. */
	public boolean suppressHover() {
		return phase == Phase.DRAGGING || phase == Phase.SETTLING;
	}

	/** Extra vertical offset (GUI px) to draw this entry at. */
	public float offsetFor(PackListWidget widget, String name, int index) {
		int list = listOf(widget);
		return list < 0 ? 0 : animator.offset(list, name, index, ListGeometry.itemHeight(widget));
	}

	// ---------------------------------------------------------------- screen access

	private boolean ready() {
		PackScreenAccessor access = (PackScreenAccessor) (Object) screen;
		return access.dragit$getAvailableList() != null
			&& access.dragit$getSelectedList() != null
			&& access.dragit$getOrganizer() != null;
	}

	PackListWidget list(int index) {
		PackScreenAccessor access = (PackScreenAccessor) (Object) screen;
		return index == AVAILABLE ? access.dragit$getAvailableList() : access.dragit$getSelectedList();
	}

	private int listOf(PackListWidget widget) {
		PackScreenAccessor access = (PackScreenAccessor) (Object) screen;
		if (widget == access.dragit$getAvailableList()) {
			return AVAILABLE;
		}
		return widget == access.dragit$getSelectedList() ? SELECTED : -1;
	}

	private ResourcePackOrganizerAccessor organizer() {
		return (ResourcePackOrganizerAccessor) (Object) ((PackScreenAccessor) (Object) screen).dragit$getOrganizer();
	}

	private List<ResourcePackProfile> profiles(int list) {
		return list == AVAILABLE ? organizer().dragit$getDisabledPacks() : organizer().dragit$getEnabledPacks();
	}

	private ResourcePackProfile findProfile(String id) {
		for (int i = 0; i < 2; i++) {
			for (ResourcePackProfile profile : profiles(i)) {
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
	public boolean onMouseButton(double x, double y, int button, boolean pressed) {
		return onMouseButton(x, y, button, pressed, 0);
	}

	/** @param mods GLFW modifier bits of the event (Shift selects several packs, Ctrl sends them to the other list) */
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
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (phase == Phase.SETTLING) {
			finishSettle();
		}
		swallowRelease = false;
		boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
		boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
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
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
				if (onEnableDisableArrow(srcList, clicked, pressX)) {
					moveSelection(); // the arrow of a selected pack acts on the whole selection
					return true;
				}
				clearMulti(); // Explorer: a click on a selected item collapses the selection to that item
				multiAnchor = clicked;
			}
			screen.mouseClicked(pressX, pressY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
			screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
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
		drop = DropTarget.Result.NONE;
		lift = 0;
		swallowRelease = gesture && buttonDown;
	}

	private void fail(RuntimeException e) {
		faultCount++;
		if (!faulted) {
			faulted = true;
			DragIt.LOGGER.error("Drag & drop failed, cancelling the gesture", e);
		}
		reset();
	}

	private Grab findGrabbable(double x, double y) {
		for (int i = 0; i < 2; i++) {
			PackListWidget list = list(i);
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
			PackListWidget.ResourcePackEntry entry = list.children().get(index);
			ResourcePackOrganizer.Pack pack = ListGeometry.packOf(entry);
			if (pack.isPinned()) {
				return null; // pinned packs are never lifted
			}
			return new Grab(i, index, entry.getName());
		}
		return null;
	}

	// ---------------------------------------------------------------- multi-selection (Shift) and quick move (Ctrl)

	/** Right edge (exclusive) of highlights and cards of this list; stops short of the scrollbar. */
	public static int highlightRight(PackListWidget list) {
		return ListGeometry.cardRight(list);
	}

	/** How visible the multi-selection highlight of this pack is, 0..1; it fades in on select and out on deselect. */
	public float multiAlpha(String name) {
		Double alpha = multiAlpha.get(name);
		return alpha == null ? 0F : alpha.floatValue();
	}

	/** Same for vanilla's single selection (the white frame), which the mod draws itself so that it can fade. */
	public float selectionAlpha(PackListWidget widget, String name) {
		Double alpha = selectionAlpha.get(listOf(widget) + name);
		return alpha == null ? 0F : alpha.floatValue();
	}

	private void updateHighlightAlpha() {
		double blend = 1.0 - Math.exp(-dt * DragItSettings.SELECTION_FADE_RATE);
		fade(multiAlpha, multi, blend);
		Set<String> selected = new HashSet<>();
		for (int i = 0; i < 2; i++) {
			PackListWidget.ResourcePackEntry entry = list(i).getSelectedOrNull();
			if (entry != null) {
				selected.add(i + entry.getName());
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

	private static boolean isFixed(PackListWidget.ResourcePackEntry entry) {
		return ListGeometry.packOf(entry).isPinned();
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
		PackListWidget list = list(id);
		String name = grab.name();
		PackListWidget.ResourcePackEntry vanilla = list.getSelectedOrNull();
		boolean seed = vanilla != null && !isFixed(vanilla);
		if (multiList != id) {
			multi.clear();
			multiList = id;
		}
		if (multi.isEmpty() && seed) {
			multi.add(vanilla.getName());
		}

		if (shift) {
			String anchor = multiAnchor;
			if (anchor == null || ListGeometry.indexOf(list, anchor) < 0) {
				anchor = seed ? vanilla.getName() : name;
			}
			if (!ctrl) {
				multi.clear();
			}
			int a = ListGeometry.indexOf(list, anchor);
			int b = ListGeometry.indexOf(list, name);
			for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
				PackListWidget.ResourcePackEntry entry = list.children().get(i);
				if (!isFixed(entry)) {
					multi.add(entry.getName());
				}
			}
			multiAnchor = anchor;
		} else {
			if (!multi.remove(name)) {
				multi.add(name);
			}
			multiAnchor = name;
		}
		if (multi.isEmpty()) {
			multiList = -1;
		}
		screen.clearSelection(); // the multi-selection replaces vanilla's single highlight
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
		for (PackListWidget.ResourcePackEntry entry : list(id).children()) {
			if (!isFixed(entry)) {
				multi.add(entry.getName());
			}
		}
		if (multi.isEmpty()) {
			multiList = -1;
		}
		screen.clearSelection();
		return true;
	}

	/**
	 * Keyboard. Enter / Space send the selection to the other list (vanilla's Enter toggles the single selected pack,
	 * so this is the same key for a whole selection); Ctrl+A selects all.
	 *
	 * @return true if the key was used
	 */
	public boolean onKey(int keyCode, int modifiers) {
		if (!ready() || phase != Phase.IDLE || screen.getFocused() instanceof TextFieldWidget) {
			return false;
		}
		try {
			if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
				return selectAll();
			}
			boolean toggle = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE;
			if (toggle && !multi.isEmpty()) {
				moveSelection();
				return true;
			}
		} catch (RuntimeException e) {
			fail(e);
		}
		return false;
	}

	/** True when a plain click at {@code x} would hit the enable/disable arrow of this pack (vanilla's own hit zones). */
	private boolean onEnableDisableArrow(int listId, String name, double x) {
		PackListWidget list = list(listId);
		PackListWidget.ResourcePackEntry entry = ListGeometry.find(list, name);
		if (entry == null) {
			return false;
		}
		double d = x - list.getRowLeft();
		ResourcePackOrganizer.Pack pack = ListGeometry.packOf(entry);
		return d <= 32.0 && (pack.canBeEnabled() || (d < 16.0 && pack.canBeDisabled()));
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

		List<ResourcePackOrganizer.Pack> packs = new ArrayList<>();
		List<ResourcePackProfile> moved = new ArrayList<>();
		for (PackListWidget.ResourcePackEntry entry : list(from).children()) {
			if (!names.contains(entry.getName())) {
				continue;
			}
			ResourcePackOrganizer.Pack pack = ListGeometry.packOf(entry);
			ResourcePackProfile profile = findProfile(entry.getName());
			boolean movable = profile != null && !pack.isPinned()
				&& (to == SELECTED ? pack.getCompatibility().isCompatible() : pack.canBeDisabled());
			if (movable) {
				packs.add(pack);
				moved.add(profile);
			}
		}
		if (packs.isEmpty()) {
			return;
		}
		for (ResourcePackOrganizer.Pack pack : packs) {
			if (to == SELECTED) {
				pack.enable();
			} else {
				pack.disable();
			}
		}
		if (to == SELECTED) {
			keepRelativeOrder(profiles(SELECTED), moved);
		}
		organizer().dragit$refreshEnabledProfiles();
		list(AVAILABLE).refreshScroll();
		list(SELECTED).refreshScroll();
		multiList = to;
	}

	/** Vanilla inserts every enabled pack by its own default position; put the moved ones back in the order they had on screen. */
	private void keepRelativeOrder(List<ResourcePackProfile> target, List<ResourcePackProfile> inOrder) {
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
			organizer().dragit$getUpdateCallback().run();
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
		multiList = ListGeometry.indexOf(list(SELECTED), multi.iterator().next()) >= 0 ? SELECTED : AVAILABLE;
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
			if ((phase == Phase.PENDING || phase == Phase.DRAGGING) && pollRealButton && !leftButtonHeld()) {
				onRelease(pointerX, pointerY, GLFW.GLFW_MOUSE_BUTTON_LEFT); // release event got lost (focus change etc.)
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
	public void endFrame(DrawContext context, float delta) {
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
		Pointer override = pointerOverride;
		if (override != null) {
			pointerX = override.x();
			pointerY = override.y();
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Window window = client.getWindow();
		pointerX = client.mouse.getX() * window.getScaledWidth() / window.getWidth();
		pointerY = client.mouse.getY() * window.getScaledHeight() / window.getHeight();
	}

	private static boolean leftButtonHeld() {
		return GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
	}

	private double thresholdGui() {
		if (pointerOverride != null) {
			return DragItSettings.DRAG_THRESHOLD_PX;
		}
		return DragItSettings.DRAG_THRESHOLD_PX / Math.max(1.0, MinecraftClient.getInstance().getWindow().getScaleFactor());
	}

	private void tickPending() {
		if (Math.hypot(pointerX - pressX, pointerY - pressY) >= thresholdGui()) {
			startDrag();
		}
	}

	private void startDrag() {
		PackListWidget source = list(srcList);
		int index = ListGeometry.indexOf(source, packId);
		if (index < 0) {
			reset();
			return;
		}
		// Dragging a selected pack lifts the whole selection (Explorer); any other pack goes alone.
		boolean group = isMultiMember(srcList, packId);
		dragIds.clear();
		dragProfiles.clear();
		for (PackListWidget.ResourcePackEntry entry : source.children()) {
			String name = entry.getName();
			ResourcePackProfile profile = name.equals(packId) || (group && multi.contains(name)) ? findProfile(name) : null;
			if (profile != null) {
				dragIds.add(name);
				dragProfiles.add(profile);
			}
		}
		if (!dragIds.contains(packId)) {
			reset();
			return;
		}
		double entryX = source.getRowLeft();
		double entryY = ListGeometry.rowTop(source, index) + animator.offset(srcList, packId, index, ListGeometry.itemHeight(source));
		grabDX = pressX - entryX;
		grabDY = pressY - entryY;
		ghostX = ghostTX = entryX;
		ghostY = ghostTY = entryY;
		lift = 0;
		phase = Phase.DRAGGING;
		screen.clearSelection();
		tickDragging();
	}

	private void tickDragging() {
		ResourcePackProfile profile = findProfile(packId);
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

	private DropTarget.Result computeDrop(ResourcePackProfile profile, double x, double y) {
		@SuppressWarnings("unchecked")
		List<ResourcePackProfile>[] lists = new List[]{profiles(AVAILABLE), profiles(SELECTED)};
		PackListWidget[] widgets = {list(AVAILABLE), list(SELECTED)};
		double cardCenterY = y - grabDY + ListGeometry.entryHeight(list(srcList)) / 2.0;
		boolean[] allowed = {!moversTo(AVAILABLE).isEmpty(), !moversTo(SELECTED).isEmpty()};
		return DropTarget.compute(widgets, lists, profile, dragProfiles, srcList, x, y, cardCenterY, allowed);
	}

	/**
	 * The dragged packs that would actually end up in {@code target}: all of them inside their own list; across lists a
	 * group skips what cannot go there (incompatible packs into "selected", required ones into "available"). A lone
	 * incompatible pack stays droppable because it gets vanilla's confirmation dialog.
	 */
	private List<ResourcePackProfile> moversTo(int target) {
		if (target == srcList || dragProfiles.size() == 1 && target == SELECTED) {
			return dragProfiles;
		}
		List<ResourcePackProfile> movers = new ArrayList<>();
		for (ResourcePackProfile profile : dragProfiles) {
			if (target == SELECTED ? profile.getCompatibility().isCompatible() : !profile.isRequired()) {
				movers.add(profile);
			}
		}
		return movers;
	}

	private void autoScroll() {
		int edge = DragItSettings.AUTOSCROLL_EDGE;
		int slack = DragItSettings.AUTOSCROLL_SLACK;
		for (int i = 0; i < 2; i++) {
			PackListWidget list = list(i);
			int pad = DragItSettings.COLUMN_PADDING;
			if (pointerX < list.getX() - pad || pointerX > list.getRight() + pad) {
				continue;
			}
			double speed = 0;
			if (pointerY < list.getY() + edge && pointerY > list.getY() - slack) {
				speed = -Math.min(1.5, (list.getY() + edge - pointerY) / edge);
			} else if (pointerY > list.getBottom() - edge && pointerY < list.getBottom() + slack) {
				speed = Math.min(1.5, (pointerY - (list.getBottom() - edge)) / edge);
			}
			if (speed != 0) {
				list.setScrollAmount(list.getScrollAmount() + speed * DragItSettings.AUTOSCROLL_MAX_SPEED * dt);
			}
		}
	}

	private void tickSettling() {
		PackListWidget list = list(settleList);
		int index = ListGeometry.indexOf(list, packId);
		if (index < 0) {
			finishSettle();
			return;
		}
		ghostTX = list.getRowLeft();
		ghostTY = ListGeometry.rowTop(list, index);
		double glide = 1.0 - Math.exp(-dt * DragItSettings.SETTLE_RATE);
		ghostX += (ghostTX - ghostX) * glide;
		ghostY += (ghostTY - ghostY) * glide;
		lift -= lift * (1.0 - Math.exp(-dt * DragItSettings.LIFT_RATE));
		if (Math.abs(ghostTX - ghostX) < 0.3 && Math.abs(ghostTY - ghostY) < 0.3 && lift < 0.03) {
			finishSettle();
		}
	}

	private void finishSettle() {
		if (packId != null && settleList >= 0 && ready()) {
			PackListWidget list = list(settleList);
			int index = ListGeometry.indexOf(list, packId);
			if (index >= 0) {
				animator.snap(settleList, packId, index, ListGeometry.itemHeight(list));
			}
		}
		phase = Phase.IDLE;
		packId = null;
		lift = 0;
	}

	/** Computes where every entry of both lists wants to be and eases it there; also drives the placeholder. */
	private void updateModel() {
		double indicatorBlend = 1.0 - Math.exp(-dt * DragItSettings.INDICATOR_RATE);
		for (int i = 0; i < 2; i++) {
			PackListWidget list = list(i);
			List<PackListWidget.ResourcePackEntry> children = list.children();
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
				indicator.blocked = drop.blocked();
			}
			indicator.alpha += ((active ? 1.0 : 0.0) - indicator.alpha) * indicatorBlend;
			if (!active && indicator.alpha < 0.01) {
				indicator.alpha = 0;
			}
		}
	}

	// ---------------------------------------------------------------- dropping

	private void cancelDrag() {
		settleList = srcList;
		phase = Phase.SETTLING;
		drop = DropTarget.Result.NONE;
		swallowRelease = buttonDown;
	}

	private enum Outcome {
		APPLIED, UNCHANGED, REJECTED, DEFERRED
	}

	private void drop(double x, double y) {
		ResourcePackProfile profile = findProfile(packId);
		if (profile == null) {
			reset();
			return;
		}
		pointerX = x;
		pointerY = y;
		DropTarget.Result result = computeDrop(profile, x, y);
		drop = DropTarget.Result.NONE;
		Outcome outcome = apply(profile, result);
		settleList = outcome == Outcome.APPLIED ? result.list() : srcList;
		phase = Phase.SETTLING;
		if (outcome == Outcome.APPLIED) {
			list(AVAILABLE).refreshScroll();
			list(SELECTED).refreshScroll();
		}
	}

	private Outcome apply(ResourcePackProfile profile, DropTarget.Result result) {
		if (!result.valid()) {
			return Outcome.REJECTED;
		}
		if (result.list() == srcList) {
			// Re-order inside one list: edit the organizer's list directly, one rebuild.
			List<ResourcePackProfile> profiles = profiles(srcList);
			int from = profiles.indexOf(profile);
			if (from < 0) {
				return Outcome.REJECTED;
			}
			if (result.gap() == from) {
				return Outcome.UNCHANGED;
			}
			profiles.remove(from);
			profiles.add(Math.min(result.gap(), profiles.size()), profile);
			organizer().dragit$getUpdateCallback().run();
			return Outcome.APPLIED;
		}

		PackListWidget.ResourcePackEntry entry = ListGeometry.find(list(srcList), packId);
		if (entry == null) {
			return Outcome.REJECTED;
		}
		ResourcePackOrganizer.Pack pack = ListGeometry.packOf(entry);
		if (result.list() == SELECTED) {
			if (!pack.getCompatibility().isCompatible()) {
				confirmIncompatible(pack, profile, result.gap());
				return Outcome.DEFERRED;
			}
		} else if (!pack.canBeDisabled()) {
			return Outcome.REJECTED;
		}
		crossList(pack, profile, result.list(), result.gap());
		return Outcome.APPLIED;
	}

	/**
	 * Moves a pack to the other list through vanilla's own enable()/disable() (which also handles the high-contrast
	 * option and refreshes the manager), then slides it to the exact gap.
	 */
	private void crossList(ResourcePackOrganizer.Pack pack, ResourcePackProfile profile, int targetList, int gap) {
		if (targetList == SELECTED) {
			pack.enable();
		} else {
			pack.disable();
		}
		List<ResourcePackProfile> profiles = profiles(targetList);
		int index = profiles.indexOf(profile);
		if (index >= 0 && index != gap) {
			profiles.remove(index);
			profiles.add(Math.min(gap, profiles.size()), profile);
			organizer().dragit$getUpdateCallback().run();
		}
		organizer().dragit$refreshEnabledProfiles();
	}

	/** Same standard confirmation dialog vanilla shows when enabling an incompatible pack. */
	private void confirmIncompatible(ResourcePackOrganizer.Pack pack, ResourcePackProfile profile, int gap) {
		MinecraftClient client = MinecraftClient.getInstance();
		Text message = pack.getCompatibility().getConfirmMessage();
		client.setScreen(new ConfirmScreen(confirmed -> {
			client.setScreen(screen);
			if (confirmed) {
				crossList(pack, profile, SELECTED, gap);
				list(AVAILABLE).refreshScroll();
				list(SELECTED).refreshScroll();
			}
		}, Text.translatable("pack.incompatible.confirm.title"), message));
	}
}
