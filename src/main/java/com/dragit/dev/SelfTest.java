package com.dragit.dev;

import com.dragit.DragIt;
import com.dragit.drag.DragController;
import com.dragit.mixin.accessor.EntryListWidgetAccessor;
import com.dragit.mixin.accessor.PackScreenAccessor;
import com.dragit.mixin.accessor.ResourcePackOrganizerAccessor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only end-to-end check, active only with {@code -Ddragit.selftest=true} (see {@code ./gradlew runClient -Pselftest}).
 * It opens the real pack screen, drives it with a synthetic cursor through the same entry points the mouse mixin uses,
 * asserts the resulting organizer state and writes screenshots to {@code run/screenshots}. Inert in normal play.
 */
public final class SelfTest {
	public static final boolean ENABLED = Boolean.getBoolean("dragit.selftest");

	private static final String PREFIX = "ditest_";
	private static final int A = DragController.AVAILABLE;
	private static final int S = DragController.SELECTED;

	private interface Step {
		boolean run();
	}

	private static final List<Step> steps = new ArrayList<>();
	private static final List<String> stepNames = new ArrayList<>();
	private static final List<String> failures = new ArrayList<>();
	private static final Random random = new Random(1234);
	private static int passed;
	private static int index;
	private static long stepStartNs = -1;
	private static int stepFrames;
	private static boolean built;
	private static boolean finished;
	private static PackScreen pack;
	private static String pendingShot;
	private static double px = 200;
	private static double py = 120;
	private static final double[] hold = new double[2];

	private static final DragController.Pointer POINTER = new DragController.Pointer() {
		@Override
		public double x() {
			return px;
		}

		@Override
		public double y() {
			return py;
		}
	};

	private SelfTest() {
	}

	// ------------------------------------------------------------------ hooks

	public static void onPackScreenFrame(PackScreen screen) {
		tick();
	}

	public static void onOtherScreenFrame(Screen screen) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!built && screen instanceof TitleScreen && client.getOverlay() == null) {
			stepFrames++;
			if (stepFrames < 20) {
				return;
			}
			stepFrames = 0;
			built = true;
			prepare(client);
			build();
			client.setScreen(pack);
			return;
		}
		if (built) {
			tick();
		}
	}

	public static void afterPackScreenFrame(PackScreen screen) {
		if (pendingShot != null) {
			String name = pendingShot;
			pendingShot = null;
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenshotRecorder.saveScreenshot(client.runDirectory, "dragit_" + name + ".png", client.getFramebuffer(), message -> {
			});
		}
	}

	// ------------------------------------------------------------------ setup

	private static void prepare(MinecraftClient client) {
		try {
			Path dir = client.getResourcePackDir();
			Files.createDirectories(dir);
			for (String suffix : new String[]{"a", "b", "c", "d", "e", "f", "old"}) {
				Path packDir = dir.resolve(PREFIX + suffix);
				Files.createDirectories(packDir);
				int format = suffix.equals("old") ? 1 : 34;
				Files.writeString(packDir.resolve("pack.mcmeta"),
					"{\"pack\":{\"pack_format\":" + format + ",\"description\":\"Drag It! test pack " + suffix + "\"}}", StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		client.getResourcePackManager().scanPacks();
		client.getResourcePackManager().setEnabledProfiles(List.of());
		DragController.setSyntheticPointer(POINTER);
		pack = new PackScreen(client.getResourcePackManager(), manager -> {
			client.options.refreshResourcePacks(manager);
			client.setScreen(null);
		}, client.getResourcePackDir(), Text.translatable("resourcePack.title"));
	}

	private static void cleanup() {
		MinecraftClient client = MinecraftClient.getInstance();
		try {
			client.options.resourcePacks.clear();
			client.options.incompatibleResourcePacks.clear();
			client.options.write();
			Path dir = client.getResourcePackDir();
			try (Stream<Path> stream = Files.list(dir)) {
				for (Path path : stream.filter(p -> p.getFileName().toString().startsWith(PREFIX)).toList()) {
					try (Stream<Path> walk = Files.walk(path)) {
						for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
							Files.deleteIfExists(file);
						}
					}
				}
			}
		} catch (IOException e) {
			DragIt.LOGGER.warn("[SelfTest] cleanup failed", e);
		}
	}

	// ------------------------------------------------------------------ step runner

	private static void tick() {
		if (!built || finished || index >= steps.size()) {
			return;
		}
		if (stepStartNs < 0) {
			stepStartNs = System.nanoTime();
			stepFrames = 0;
			DragIt.LOGGER.info("[SelfTest] >> {}", stepNames.get(index));
		}
		stepFrames++;
		boolean done;
		try {
			done = steps.get(index).run();
		} catch (Throwable t) {
			fail(stepNames.get(index), "exception " + t);
			DragIt.LOGGER.error("[SelfTest] step threw", t);
			done = true;
		}
		if (done) {
			index++;
			stepStartNs = -1;
			if (index >= steps.size()) {
				finish();
			}
		}
	}

	private static long elapsedMs() {
		return (System.nanoTime() - stepStartNs) / 1_000_000L;
	}

	private static void add(String name, Step step) {
		stepNames.add(name);
		steps.add(step);
	}

	private static void act(String name, Runnable action) {
		add(name, () -> {
			action.run();
			return true;
		});
	}

	private static void waitMs(String name, long ms) {
		add(name, () -> elapsedMs() >= ms);
	}

	private static void check(String name, BooleanSupplier condition, Supplier<String> detail) {
		act("check: " + name, () -> {
			if (condition.getAsBoolean()) {
				passed++;
				DragIt.LOGGER.info("[SelfTest] PASS {}", name);
			} else {
				fail(name, detail.get());
			}
		});
	}

	private static void fail(String name, String detail) {
		String line = name + " :: " + detail;
		failures.add(line);
		DragIt.LOGGER.error("[SelfTest] FAIL {}", line);
	}

	private static void shot(String name) {
		act("screenshot " + name, () -> pendingShot = name);
		waitFrames("shot flush", 3);
	}

	private static void waitFrames(String name, int frames) {
		add(name, () -> stepFrames >= frames);
	}

	/** Moves the synthetic cursor smoothly to the point returned by {@code target} (evaluated when the move starts). */
	private static void move(String name, Supplier<double[]> target, long ms) {
		double[] from = new double[2];
		double[] to = new double[2];
		add(name, () -> {
			if (stepFrames == 1) {
				from[0] = px;
				from[1] = py;
				double[] t = target.get();
				to[0] = t[0];
				to[1] = t[1];
			}
			double t = ms <= 0 ? 1 : Math.min(1.0, elapsedMs() / (double) ms);
			double eased = t * t * (3 - 2 * t);
			px = from[0] + (to[0] - from[0]) * eased;
			py = from[1] + (to[1] - from[1]) * eased;
			return t >= 1.0;
		});
	}

	private static void press() {
		act("press", () -> ctl().onMouseButton(px, py, 0, true));
	}

	private static void release() {
		act("release", () -> ctl().onMouseButton(px, py, 0, false));
	}

	// ------------------------------------------------------------------ model helpers

	private static DragController ctl() {
		return DragController.of(pack);
	}

	private static PackListWidget list(int i) {
		PackScreenAccessor a = (PackScreenAccessor) (Object) pack;
		return i == A ? a.dragit$getAvailableList() : a.dragit$getSelectedList();
	}

	private static List<ResourcePackProfile> profiles(int i) {
		ResourcePackOrganizerAccessor o = (ResourcePackOrganizerAccessor) (Object) ((PackScreenAccessor) (Object) pack).dragit$getOrganizer();
		return i == A ? o.dragit$getDisabledPacks() : o.dragit$getEnabledPacks();
	}

	private static List<String> ids(int i) {
		List<String> ids = new ArrayList<>();
		for (ResourcePackProfile profile : profiles(i)) {
			ids.add(profile.getId());
		}
		return ids;
	}

	/** What the widgets actually show (must always equal the organizer's lists). */
	private static List<String> shown(int i) {
		List<String> names = new ArrayList<>();
		for (PackListWidget.ResourcePackEntry entry : list(i).children()) {
			names.add(entry.getName());
		}
		return names;
	}

	/** Pinned packs: the mod never lifts them and nothing may cross them. */
	private static boolean pinned(String id) {
		for (int i = 0; i < 2; i++) {
			for (ResourcePackProfile profile : profiles(i)) {
				if (profile.getId().equals(id)) {
					return profile.isPinned();
				}
			}
		}
		return false;
	}

	/** Packs that can never be disabled: pinned ones and required ones such as the built-in "Default" pack. */
	private static boolean locked(String id) {
		for (int i = 0; i < 2; i++) {
			for (ResourcePackProfile profile : profiles(i)) {
				if (profile.getId().equals(id)) {
					return profile.isPinned() || profile.isRequired();
				}
			}
		}
		return false;
	}

	private static String requiredId() {
		for (ResourcePackProfile profile : profiles(S)) {
			if (profile.isRequired() && !profile.isPinned()) {
				return profile.getId();
			}
		}
		throw new IllegalStateException("no required pack in the enabled list");
	}

	private static boolean fixedPosition(String id) {
		for (int i = 0; i < 2; i++) {
			for (ResourcePackProfile profile : profiles(i)) {
				if (profile.getId().equals(id)) {
					return profile.isPinned();
				}
			}
		}
		return false;
	}

	private static String id(String suffix) {
		return "file/" + PREFIX + suffix;
	}

	private static int itemHeight(PackListWidget l) {
		return ((EntryListWidgetAccessor) (Object) l).dragit$getItemHeight();
	}

	private static int origin(PackListWidget l) {
		return l.getY() + 4 - (int) l.getScrollAmount() + ((EntryListWidgetAccessor) (Object) l).dragit$getHeaderHeight();
	}

	private static void scrollTo(PackListWidget l, int row) {
		double wanted = row * itemHeight(l) + 4 + 13 - l.getHeight() / 2.0;
		l.setScrollAmount(Math.max(0, wanted));
	}

	/** Centre of an entry's text area (not the arrow icons), scrolling the list so it is visible. */
	private static double[] entryPoint(int listIndex, String id) {
		PackListWidget l = list(listIndex);
		int row = shown(listIndex).indexOf(id);
		if (row < 0) {
			throw new IllegalStateException("not in list " + listIndex + ": " + id + " (" + shown(listIndex) + ")");
		}
		scrollTo(l, row);
		return new double[]{l.getRowLeft() + 120, origin(l) + row * itemHeight(l) + 16};
	}

	private static double[] iconPoint(int listIndex, String id) {
		double[] p = entryPoint(listIndex, id);
		return new double[]{list(listIndex).getRowLeft() + 8, p[1] - 8};
	}

	/** Cursor position that makes the lifted card (grabbed at its centre) insert at {@code gap}. */
	private static double[] gapPoint(int listIndex, int gap) {
		PackListWidget l = list(listIndex);
		scrollTo(l, gap);
		double y = origin(l) + (double) gap * itemHeight(l);
		y = Math.max(l.getY() + 2, Math.min(l.getBottom() - 2, y));
		return new double[]{l.getRowLeft() + 120, y};
	}

	private static int maxGap(int listIndex, String id) {
		List<String> base = new ArrayList<>(ids(listIndex));
		base.remove(id);
		if (listIndex == A) {
			return base.size();
		}
		for (int i = 0; i < base.size(); i++) {
			if (pinned(base.get(i))) {
				return i;
			}
		}
		return base.size();
	}

	// ------------------------------------------------------------------ composite drag test

	private static int totalPacks;
	private static String dragId;
	private static List<String> expectEnabled;
	private static List<String> expectDisabled;

	/**
	 * Full gesture: grab {@code idSupplier}'s entry, drag it to {@code gap} of {@code toList}, release, wait, and compare the
	 * organizer (and the widgets) with the expected result.
	 */
	private static void dragTest(String label, Supplier<String> idSupplier, int toList, Supplier<Integer> gapSupplier) {
		act(label + ": plan", () -> {
			dragId = idSupplier.get();
			int from = ids(A).contains(dragId) ? A : S;
			int gap = Math.min(gapSupplier.get(), maxGap(toList, dragId));
			expectEnabled = new ArrayList<>(ids(S));
			expectDisabled = new ArrayList<>(ids(A));
			expectEnabled.remove(dragId);
			expectDisabled.remove(dragId);
			(toList == S ? expectEnabled : expectDisabled).add(gap, dragId);
			hold[0] = gap;
			hold[1] = from;
			DragIt.LOGGER.info("[SelfTest] {}: {} from {} -> list {} gap {}", label, dragId, from == A ? "available" : "selected", toList, gap);
		});
		move(label + ": to entry", () -> entryPoint(ids(A).contains(dragId) ? A : S, dragId), 250);
		press();
		move(label + ": nudge", () -> new double[]{px + 10, py + 2}, 90);
		move(label + ": to gap", () -> gapPoint(toList, (int) hold[0]), 700);
		waitMs(label + ": hover", 250);
		check(label + ": placeholder valid while hovering", () -> ctl().debugDropValid() && ctl().debugDropList() == toList && ctl().debugDropGap() == (int) hold[0],
			() -> "valid=" + ctl().debugDropValid() + " list=" + ctl().debugDropList() + " gap=" + ctl().debugDropGap() + " wanted " + (int) hold[0]);
		release();
		waitMs(label + ": settle", 1100);
		check(label + ": enabled order", () -> ids(S).equals(expectEnabled), () -> ids(S) + " != expected " + expectEnabled);
		check(label + ": available order", () -> ids(A).equals(expectDisabled), () -> ids(A) + " != expected " + expectDisabled);
		check(label + ": widgets mirror organizer", () -> shown(S).equals(ids(S)) && shown(A).equals(ids(A)), () -> shown(S) + " / " + shown(A));
		check(label + ": back to idle", () -> ctl().phase() == DragController.Phase.IDLE, () -> String.valueOf(ctl().phase()));
	}

	// ------------------------------------------------------------------ the script

	private static void build() {
		waitFrames("let the screen settle", 30);
		act("initial state", () -> DragIt.LOGGER.info("[SelfTest] available={} selected={}", ids(A), ids(S)));
		check("required vanilla pack (Default) is in the enabled list", () -> ids(S).stream().anyMatch(SelfTest::locked), () -> ids(S).toString());

		// 1. Plain click on the arrow area: must behave exactly like vanilla (pack gets enabled), without any drag.
		move("click: to arrow", () -> iconPoint(A, id("a")), 200);
		press();
		waitMs("click: hold", 80);
		release();
		waitMs("click: wait", 300);
		check("click on the enable arrow enables the pack (vanilla behaviour)", () -> ids(S).contains(id("a")) && !ids(A).contains(id("a")), () -> ids(S) + " / " + ids(A));
		check("a click never starts a drag", () -> ctl().phase() == DragController.Phase.IDLE, () -> String.valueOf(ctl().phase()));

		// 2. Plain click on the text area selects the entry.
		move("select: to entry", () -> entryPoint(A, id("b")), 200);
		press();
		release();
		waitMs("select: wait", 200);
		check("click on an entry selects it (vanilla behaviour)", () -> list(A).getSelectedOrNull() != null && list(A).getSelectedOrNull().getName().equals(id("b")),
			() -> String.valueOf(list(A).getSelectedOrNull()));

		// Fill the selected list so it scrolls.
		for (String suffix : new String[]{"c", "d", "e", "f"}) {
			move("enable " + suffix, () -> iconPoint(A, id(suffix)), 120);
			press();
			release();
			waitMs("enable " + suffix + " wait", 150);
		}
		act("state after enabling", () -> DragIt.LOGGER.info("[SelfTest] available={} selected={}", ids(A), ids(S)));

		// 3. Drag between lists, into the middle.
		dragTest("available->selected middle", () -> id("b"), S, () -> 2);
		shot("after_drop");
		// 4. Re-order inside the selected list.
		dragTest("selected reorder to top", () -> ids(S).get(3), S, () -> 0);
		dragTest("selected reorder to last allowed", () -> ids(S).get(0), S, () -> 99);
		dragTest("selected reorder to middle", () -> ids(S).get(1), S, () -> 3);
		// 5. Selected -> available, at an exact place.
		dragTest("selected->available", () -> ids(S).get(2), A, () -> 1);
		// 6. Back with exact index at the very top.
		dragTest("available->selected top", () -> id("a"), S, () -> 0);

		// 7. Visual check mid-drag with two lists.
		build_visualChecks();
		build_escape();
		build_blocked();
		build_outside();
		build_incompatible();
		build_autoscroll();
		build_resize();
		build_chaos();
		build_emptySpace();
		build_scale3();
		build_done();
	}

	private static void build_visualChecks() {
		move("visual: to entry", () -> entryPoint(S, ids(S).get(1)), 250);
		press();
		move("visual: lift", () -> new double[]{px + 6, py + 6}, 80);
		move("visual: across to available", () -> gapPoint(A, 1), 600);
		waitMs("visual: hover", 400);
		shot("drag_cross_list");
		check("a lifted entry is hidden from its row and shown as a card", () -> ctl().phase() == DragController.Phase.DRAGGING, () -> String.valueOf(ctl().phase()));
		release();
		waitMs("visual: settle start", 80);
		shot("settle_start");
		waitMs("visual: settle", 1000);
	}

	private static void build_escape() {
		act("esc: remember", () -> {
			expectEnabled = ids(S);
			expectDisabled = ids(A);
		});
		move("esc: to entry", () -> entryPoint(S, ids(S).get(0)), 250);
		press();
		move("esc: lift", () -> new double[]{px + 10, py + 4}, 80);
		move("esc: away", () -> gapPoint(A, 2), 500);
		act("esc: press Esc", () -> {
			boolean handled = pack.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
			hold[0] = handled ? 1 : 0;
		});
		check("Esc is consumed while dragging", () -> hold[0] == 1, () -> "keyPressed returned false");
		check("Esc does not close the screen", () -> MinecraftClient.getInstance().currentScreen == pack, () -> String.valueOf(MinecraftClient.getInstance().currentScreen));
		release();
		waitMs("esc: glide back", 1100);
		check("Esc restores the original order", () -> ids(S).equals(expectEnabled) && ids(A).equals(expectDisabled), () -> ids(S) + " / " + ids(A));
		check("Esc leaves the drag idle", () -> ctl().phase() == DragController.Phase.IDLE, () -> String.valueOf(ctl().phase()));
		act("esc: second Esc is not swallowed", () -> hold[0] = ctl().onEscape() ? 1 : 0);
		check("Esc outside a drag is not consumed (screen may close)", () -> hold[0] == 0, () -> "consumed");
	}

	/**
	 * The required "Default" pack is not pinned: it re-orders like any other pack but can never be disabled. (The dev
	 * environment has no truly pinned pack, so the pinned-pack barrier is not exercised here.)
	 */
	private static void build_blocked() {
		dragTest("required: Default to the top", SelfTest::requiredId, S, () -> 0);
		act("required: remember", () -> {
			expectEnabled = ids(S);
			expectDisabled = ids(A);
		});
		move("required: to entry", () -> entryPoint(S, requiredId()), 250);
		press();
		move("required: lift", () -> new double[]{px + 10, py + 4}, 80);
		move("required: onto available", () -> gapPoint(A, 0), 600);
		waitMs("required: hover", 300);
		check("a required pack is not a valid drop in the available list", () -> !ctl().debugDropValid(), () -> "valid=" + ctl().debugDropValid());
		shot("drag_blocked");
		release();
		waitMs("required: glide back", 1100);
		check("a required pack cannot be disabled by dragging", () -> ids(S).equals(expectEnabled) && ids(A).equals(expectDisabled), () -> ids(S) + " / " + ids(A));
		dragTest("required: Default back to the bottom", SelfTest::requiredId, S, () -> 99);
	}

	private static void build_outside() {
		act("outside: remember", () -> {
			expectEnabled = ids(S);
			expectDisabled = ids(A);
		});
		move("outside: to entry", () -> entryPoint(S, ids(S).get(1)), 250);
		press();
		move("outside: lift", () -> new double[]{px + 10, py + 4}, 80);
		move("outside: header area", () -> new double[]{210, 8}, 500);
		check("outside both lists there is no drop target", () -> !ctl().debugDropValid() && ctl().debugDropList() == -1, () -> "list=" + ctl().debugDropList());
		release();
		waitMs("outside: glide back", 1100);
		check("release outside the lists puts the pack back", () -> ids(S).equals(expectEnabled) && ids(A).equals(expectDisabled), () -> ids(S) + " / " + ids(A));
	}

	private static void build_incompatible() {
		act("incompat: remember", () -> {
			expectEnabled = ids(S);
			expectDisabled = ids(A);
		});
		move("incompat: to entry", () -> entryPoint(A, id("old")), 250);
		press();
		move("incompat: lift", () -> new double[]{px + 10, py + 4}, 80);
		move("incompat: to selected", () -> gapPoint(S, 1), 600);
		release();
		waitMs("incompat: dialog", 400);
		check("an incompatible pack opens the standard confirm dialog", () -> MinecraftClient.getInstance().currentScreen instanceof ConfirmScreen,
			() -> String.valueOf(MinecraftClient.getInstance().currentScreen));
		act("incompat: answer No", () -> pressDialogButton(1));
		waitMs("incompat: back", 300);
		check("answering No leaves everything as it was", () -> MinecraftClient.getInstance().currentScreen == pack && ids(S).equals(expectEnabled) && ids(A).equals(expectDisabled),
			() -> ids(S) + " / " + ids(A));

		move("incompat2: to entry", () -> entryPoint(A, id("old")), 250);
		press();
		move("incompat2: lift", () -> new double[]{px + 10, py + 4}, 80);
		move("incompat2: to selected", () -> gapPoint(S, 1), 600);
		release();
		waitMs("incompat2: dialog", 400);
		act("incompat2: answer Yes", () -> pressDialogButton(0));
		waitMs("incompat2: back", 500);
		check("answering Yes enables the pack at the dropped position", () -> ids(S).indexOf(id("old")) == 1, () -> ids(S).toString());
		check("widgets mirror the organizer after the dialog", () -> shown(S).equals(ids(S)) && shown(A).equals(ids(A)), () -> shown(S) + " / " + shown(A));
	}

	private static void pressDialogButton(int which) {
		Screen screen = MinecraftClient.getInstance().currentScreen;
		List<ButtonWidget> buttons = new ArrayList<>();
		for (var child : screen.children()) {
			if (child instanceof ButtonWidget button) {
				buttons.add(button);
			}
		}
		buttons.get(which).onPress();
	}

	private static void build_autoscroll() {
		for (int i = 0; i < 8; i++) {
			add("autoscroll: fill " + i, new Step() {
				@Override
				public boolean run() {
					if (stepFrames == 1) {
						for (String id : ids(A)) {
							if (!id.endsWith("_old")) {
								double[] p = iconPoint(A, id);
								px = p[0];
								py = p[1];
								ctl().onMouseButton(px, py, 0, true);
								ctl().onMouseButton(px, py, 0, false);
								return false;
							}
						}
						return true;
					}
					return stepFrames >= 6;
				}
			});
		}
		act("autoscroll: reset", () -> list(S).setScrollAmount(0));
		move("autoscroll: to entry", () -> entryPoint(S, ids(S).get(0)), 250);
		press();
		move("autoscroll: lift", () -> new double[]{px + 10, py + 4}, 80);
		move("autoscroll: bottom edge", () -> new double[]{px, list(S).getBottom() - 6}, 400);
		act("autoscroll: note", () -> hold[0] = list(S).getScrollAmount());
		waitMs("autoscroll: hold bottom", 900);
		check("holding the cursor at the bottom edge scrolls the list down", () -> list(S).getScrollAmount() > hold[0] + 20, () -> hold[0] + " -> " + list(S).getScrollAmount());
		shot("autoscroll");
		move("autoscroll: top edge", () -> new double[]{px, list(S).getY() + 6}, 400);
		act("autoscroll: note", () -> hold[0] = list(S).getScrollAmount());
		waitMs("autoscroll: hold top", 900);
		check("holding the cursor at the top edge scrolls the list up", () -> list(S).getScrollAmount() < hold[0] - 20, () -> hold[0] + " -> " + list(S).getScrollAmount());
		act("autoscroll: Esc", () -> ctl().onEscape());
		release();
		waitMs("autoscroll: done", 1000);
	}

	private static void build_resize() {
		act("resize: remember", () -> {
			expectEnabled = ids(S);
			expectDisabled = ids(A);
		});
		move("resize: to entry", () -> entryPoint(S, ids(S).get(0)), 250);
		press();
		move("resize: lift", () -> new double[]{px + 10, py + 4}, 80);
		waitMs("resize: hover", 150);
		act("resize: window resized mid-drag", () -> {
			MinecraftClient client = MinecraftClient.getInstance();
			pack.resize(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
		});
		waitMs("resize: wait", 300);
		check("a re-layout cancels the gesture without side effects", () -> ctl().phase() == DragController.Phase.IDLE && ids(S).equals(expectEnabled), () -> ctl().phase() + " " + ids(S));
		release();
		waitMs("resize: wait", 200);
		check("the leftover release is swallowed", () -> ids(S).equals(expectEnabled) && ids(A).equals(expectDisabled), () -> ids(S) + " / " + ids(A));
	}

	private static void build_chaos() {
		act("chaos: start", () -> {
			DragController.faultCount = 0;
			totalPacks = ids(A).size() + ids(S).size();
		});
		add("chaos: 900 frames of random mouse", () -> {
			PackListWidget a = list(A);
			PackListWidget s = list(S);
			int w = MinecraftClient.getInstance().getWindow().getScaledWidth();
			int h = MinecraftClient.getInstance().getWindow().getScaledHeight();
			switch (random.nextInt(14)) {
				case 0, 1 -> {
					px = random.nextInt(w + 40) - 20;
					py = random.nextInt(h + 40) - 20;
				}
				case 2, 3, 4 -> {
					px += random.nextGaussian() * 25;
					py += random.nextGaussian() * 25;
				}
				case 5 -> ctl().onMouseButton(px, py, 0, true);
				case 6 -> ctl().onMouseButton(px, py, 0, false);
				case 7 -> {
					px = (random.nextBoolean() ? a : s).getRowLeft() + random.nextInt(180);
					py = random.nextInt(h);
				}
				case 8 -> {
					// Esc is only sent mid-gesture (outside one it would legitimately close the screen).
					if (ctl().phase() == DragController.Phase.DRAGGING || ctl().phase() == DragController.Phase.PENDING) {
						pack.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
					}
				}
				case 9 -> (random.nextBoolean() ? a : s).setScrollAmount(random.nextInt(300));
				case 10 -> ctl().onMouseButton(px, py, 1, random.nextBoolean());
				case 11 -> pack.mouseScrolled(px, py, 0, random.nextInt(5) - 2);
				default -> {
					px += random.nextInt(9) - 4;
					py += random.nextInt(9) - 4;
				}
			}
			return stepFrames >= 900;
		});
		act("chaos: release", () -> ctl().onMouseButton(px, py, 0, false));
		waitMs("chaos: drain", 1500);
		check("no exceptions escaped the controller during chaos", () -> DragController.faultCount == 0, () -> DragController.faultCount + " faults");
		check("chaos left the controller idle", () -> ctl().phase() == DragController.Phase.IDLE, () -> String.valueOf(ctl().phase()));
		check("chaos kept every pack exactly once", () -> {
			List<String> all = new ArrayList<>(ids(A));
			all.addAll(ids(S));
			return all.stream().distinct().count() == all.size() && all.size() == totalPacks;
		}, () -> ids(A) + " / " + ids(S) + " total was " + totalPacks);
		check("widgets mirror the organizer after chaos", () -> shown(S).equals(ids(S)) && shown(A).equals(ids(A)), () -> shown(S) + " / " + shown(A));
	}

	private static void build_emptySpace() {
		// Strip the selected list down to its pinned packs so there is empty room below the last entry.
		for (int i = 0; i < 12; i++) {
			add("empty: click disable " + i, new Step() {
				@Override
				public boolean run() {
					if (stepFrames == 1) {
						for (String id : ids(S)) {
							if (!locked(id)) {
								double[] p = iconPoint(S, id);
								px = p[0];
								py = p[1];
								ctl().onMouseButton(px, py, 0, true);
								ctl().onMouseButton(px, py, 0, false);
								return false;
							}
						}
						return true;
					}
					return stepFrames >= 8;
				}
			});
		}
		act("empty: state", () -> DragIt.LOGGER.info("[SelfTest] available={} selected={}", ids(A), ids(S)));
		act("empty: remember", () -> {
			expectEnabled = new ArrayList<>(ids(S));
			expectEnabled.add(0, id("c"));
			hold[0] = 0;
		});
		move("empty: to entry", () -> entryPoint(A, id("c")), 250);
		press();
		move("empty: lift", () -> new double[]{px + 10, py + 4}, 80);
		move("empty: to empty area", () -> {
			PackListWidget l = list(S);
			return new double[]{l.getRowLeft() + 120, l.getBottom() - 8};
		}, 600);
		waitMs("empty: hover", 250);
		check("empty space in the list is a valid drop target", () -> ctl().debugDropValid() && ctl().debugDropList() == S, () -> "valid=" + ctl().debugDropValid());
		shot("drag_empty_space");
		release();
		waitMs("empty: settle", 1000);
		check("dropping on empty space inserts last (below the required Default pack)", () -> ids(S).indexOf(id("c")) == ids(S).size() - 1,
			() -> ids(S).toString());
	}

	private static void build_scale3() {
		act("scale3: switch GUI scale", () -> {
			MinecraftClient client = MinecraftClient.getInstance();
			client.options.getGuiScale().setValue(3);
			client.onResolutionChanged();
		});
		waitMs("scale3: relayout", 500);
		act("scale3: remember", () -> {
			expectEnabled = new ArrayList<>(ids(S));
			expectDisabled = new ArrayList<>(ids(A));
		});
		dragTest("scale3 available->selected", () -> id("d"), S, () -> 0);
		shot("scale3_after_drop");
		act("scale3: back to auto", () -> {
			MinecraftClient client = MinecraftClient.getInstance();
			client.options.getGuiScale().setValue(0);
			client.onResolutionChanged();
		});
		waitMs("scale3: relayout", 500);
	}

	private static void build_done() {
		act("done: close", () -> {
			hold[0] = 0;
			expectEnabled = new ArrayList<>(ids(S));
			pack.close();
		});
		waitMs("done: apply", 1500);
		check("Done applies the shown order to the game options (non-pinned, bottom to top)", () -> {
			List<String> want = new ArrayList<>();
			for (int i = expectEnabled.size() - 1; i >= 0; i--) {
				if (!fixedPosition(expectEnabled.get(i))) {
					want.add(expectEnabled.get(i));
				}
			}
			return MinecraftClient.getInstance().options.resourcePacks.equals(want);
		}, () -> MinecraftClient.getInstance().options.resourcePacks + " vs shown " + expectEnabled);
		check("the order is written to options.txt (survives a restart)", () -> {
			try {
				File file = new File(MinecraftClient.getInstance().runDirectory, "options.txt");
				String text = Files.readString(file.toPath());
				for (String line : text.split("\\R")) {
					if (line.startsWith("resourcePacks:")) {
						List<String> want = new ArrayList<>();
						for (int i = expectEnabled.size() - 1; i >= 0; i--) {
							if (!fixedPosition(expectEnabled.get(i))) {
								want.add("\"" + expectEnabled.get(i) + "\"");
							}
						}
						return line.equals("resourcePacks:[" + String.join(",", want) + "]");
					}
				}
			} catch (IOException e) {
				return false;
			}
			return false;
		}, () -> "options.txt line differs");
	}

	private static void finish() {
		finished = true;
		cleanup();
		DragIt.LOGGER.info("[SelfTest] ================================================");
		DragIt.LOGGER.info("[SelfTest] RESULT: {} passed, {} failed", passed, failures.size());
		for (String failure : failures) {
			DragIt.LOGGER.info("[SelfTest]   FAILED: {}", failure);
		}
		DragIt.LOGGER.info("[SelfTest] ================================================");
		MinecraftClient.getInstance().scheduleStop();
	}
}
