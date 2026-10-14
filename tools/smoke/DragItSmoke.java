package dev.vercim.dragit.drag;

import dev.vercim.dragit.mixin.accessor.PackSelectionModelAccessor;
import dev.vercim.dragit.mixin.accessor.PackSelectionScreenAccessor;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

/** Temporary smoke test: drives the real controller in the dev client. Not part of the mod. */
public final class DragItSmoke {
	public static volatile boolean HELD;
	public static volatile boolean ACTIVE;

	private static final java.util.concurrent.atomic.AtomicBoolean STARTED = new java.util.concurrent.atomic.AtomicBoolean();

	/** Called from {@code SmokeStartTestMixin} whenever the title screen is built; only the first call starts the test. */
	public static void startOnce() {
		if (STARTED.compareAndSet(false, true)) {
			start();
		}
	}

	public static void start() {
		ACTIVE = true;
		try {
			makePacks();
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
		Thread t = new Thread(DragItSmoke::run, "dragit-smoke");
		t.setDaemon(true);
		t.start();
	}

	static void makePacks() throws Exception {
		Path dir = new File("resourcepacks").toPath().toAbsolutePath();
		Files.createDirectories(dir);
		int major = Integer.parseInt(Files.readString(new File("smoke.format").toPath()).trim());
		String meta = major > 64
			? "{\"pack\":{\"description\":\"Test pack\",\"pack_format\":" + major + ",\"min_format\":" + major + ",\"max_format\":" + major + "}}"
			: "{\"pack\":{\"description\":\"Test pack\",\"pack_format\":" + major + "}}";
		for (String n : new String[]{"TestA", "TestB", "TestC", "TestD", "TestE"}) {
			Path p = dir.resolve(n);
			Files.createDirectories(p);
			Files.writeString(p.resolve("pack.mcmeta"), meta);
		}
		System.out.println("SMOKE packs made in " + dir);
	}

	static void log(String s) {
		System.out.println("SMOKE " + s);
		try {
			Files.writeString(new File("smoke.log").toPath(), s + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (Exception ignored) {
		}
	}

	static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
		}
	}

	static void waitFor(BooleanSupplier c, long timeout) {
		long end = System.currentTimeMillis() + timeout;
		while (!c.getAsBoolean() && System.currentTimeMillis() < end) {
			sleep(100);
		}
	}

	static <T> T onRender(Supplier<T> s) {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<T> out = new AtomicReference<>();
		AtomicReference<Throwable> err = new AtomicReference<>();
		Minecraft.getInstance().execute(() -> {
			try {
				out.set(s.get());
			} catch (Throwable t) {
				err.set(t);
			} finally {
				latch.countDown();
			}
		});
		try {
			latch.await();
		} catch (InterruptedException ignored) {
		}
		if (err.get() != null) {
			throw new RuntimeException(err.get());
		}
		return out.get();
	}

	static void onRender(Runnable r) {
		onRender(() -> {
			r.run();
			return null;
		});
	}

	static void setField(Object o, String name, double v) {
		try {
			Field f = o.getClass().getDeclaredField(name);
			f.setAccessible(true);
			f.setDouble(o, v);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static void pointer(double gx, double gy) {
		Minecraft mc = Minecraft.getInstance();
		var w = mc.getWindow();
		setField(mc.mouseHandler, "xpos", gx * w.getScreenWidth() / w.getGuiScaledWidth());
		setField(mc.mouseHandler, "ypos", gy * w.getScreenHeight() / w.getGuiScaledHeight());
	}

	static boolean ready(Minecraft mc) {
		try {
			return GuiCompat.screen(mc) instanceof TitleScreen && !GuiCompat.overlayShown(mc);
		} catch (NullPointerException e) {
			return false; // the game is still starting up
		}
	}

	static Object mainTarget(Minecraft mc) {
		try {
			return Minecraft.class.getMethod("getMainRenderTarget").invoke(mc);
		} catch (ReflectiveOperationException e) {
			try {
				Object renderer = Minecraft.class.getField("gameRenderer").get(mc);
				return renderer.getClass().getMethod("mainRenderTarget").invoke(renderer);
			} catch (ReflectiveOperationException e2) {
				throw new RuntimeException(e2);
			}
		}
	}

	static void shot(String name) {
		Minecraft mc = Minecraft.getInstance();
		try {
			for (var m : Screenshot.class.getDeclaredMethods()) {
				if (m.getName().equals("grab") && m.getParameterCount() >= 4 && m.getParameterTypes()[0] == File.class && m.getParameterTypes()[1] == String.class) {
					Object[] args = m.getParameterCount() == 5 ? new Object[]{mc.gameDirectory, name + ".png", mainTarget(mc), 1, (java.util.function.Consumer<Component>) c -> {}} : new Object[]{mc.gameDirectory, name + ".png", mainTarget(mc), (java.util.function.Consumer<Component>) c -> {}};
					onRender(() -> {
						try {
							m.invoke(null, args);
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					});
					break;
				}
			}
		} catch (RuntimeException e) {
			log("screenshot failed " + e);
		}
		sleep(800);
	}

	static PackSelectionScreen screen() {
		return (PackSelectionScreen) GuiCompat.screen(Minecraft.getInstance());
	}

	static DragController ctl() {
		return DragController.of(screen());
	}

	static String state() {
		return onRender(() -> {
			DragController c = ctl();
			PackSelectionModelAccessor m = (PackSelectionModelAccessor) (Object) ((PackSelectionScreenAccessor) (Object) screen()).dragit$getOrganizer();
			return "phase=" + c.phase + " drop=" + c.drop + " available=" + ids(m.dragit$getDisabledPacks()) + " selected=" + ids(m.dragit$getEnabledPacks())
				+ " widgetAvail=" + ListGeometry.names(c.list(0)) + " widgetSel=" + ListGeometry.names(c.list(1));
		});
	}

	static List<String> ids(List<Pack> packs) {
		List<String> out = new ArrayList<>();
		for (Pack p : packs) {
			out.add(p.getId());
		}
		return out;
	}

	/** Centre of the content of row {@code index} of list {@code id}: {x, y}. */
	static double[] row(int id, int index) {
		return onRender(() -> {
			TransferableSelectionList l = ctl().list(id);
			double y = ListGeometry.rowTop(l, index) + 16;
			if (y < ListGeometry.top(l) + 8 || y > ListGeometry.bottom(l) - 8) {
				l.setScrollAmount(ListGeometry.scrollAmount(l) + y - (ListGeometry.top(l) + 60));
				y = ListGeometry.rowTop(l, index) + 16;
			}
			return new double[]{ListGeometry.contentLeft(l) + 70, y};
		});
	}

	/** Pointer position that drops at gap {@code gap} of list {@code id}. */
	static double[] gap(int id, int gap) {
		return onRender(() -> {
			TransferableSelectionList l = ctl().list(id);
			return new double[]{ListGeometry.contentLeft(l) + 70, ListGeometry.originY(l) + gap * ListGeometry.itemHeight(l) + 1};
		});
	}

	static void press(double[] p, int mods) {
		pointer(p[0], p[1]);
		HELD = true;
		boolean eaten = onRender(() -> ctl().onMouseButton(p[0], p[1], GuiCompat.MOUSE_LEFT, true, mods));
		log("press consumed=" + eaten);
	}

	static void release(double[] p) {
		pointer(p[0], p[1]);
		boolean eaten = onRender(() -> ctl().onMouseButton(p[0], p[1], GuiCompat.MOUSE_LEFT, false, 0));
		HELD = false;
		log("release consumed=" + eaten);
	}

	static void moveTo(double[] from, double[] to, int steps) {
		for (int i = 1; i <= steps; i++) {
			pointer(from[0] + (to[0] - from[0]) * i / steps, from[1] + (to[1] - from[1]) * i / steps);
			sleep(40);
		}
	}

	static void drag(String label, double[] from, double[] to, boolean shotMid) {
		press(from, 0);
		sleep(150);
		moveTo(from, to, 12);
		sleep(900);
		log(label + " mid " + state());
		if (shotMid) {
			shot(label + "-mid");
		}
		release(to);
		sleep(1200);
		log(label + " after " + state());
	}

	static int idx(int list, String id) {
		return onRender(() -> ListGeometry.indexOf(ctl().list(list), id));
	}

	static void expect(String label, String what, Object actual, Object expected) {
		if (actual instanceof List<?> l && l.contains("mod_resources")) {
			// the NeoForge dev profile has an extra built-in pack that Fabric lacks; the expectations are written without it
			List<Object> filtered = new ArrayList<>(l);
			filtered.remove("mod_resources");
			actual = filtered;
		}
		log((actual.equals(expected) ? "PASS " : "FAIL ") + label + " " + what + ": " + actual + (actual.equals(expected) ? "" : " (expected " + expected + ")"));
	}

	static List<String> sel() {
		return onRender(() -> ListGeometry.names(ctl().list(1)));
	}

	/** Gives the search box the keyboard focus, as the screen does when it opens. */
	static boolean focusSearch() {
		return onRender(() -> {
			EditBox box = null;
			for (var child : screen().children()) {
				if (child instanceof EditBox e) {
					box = e;
				}
			}
			if (box != null) {
				screen().setFocused(box);
				box.setFocused(true);
			}
			return box != null;
		});
	}

	static boolean setSearch(String text) {
		return onRender(() -> {
			EditBox box = null;
			for (var child : screen().children()) {
				if (child instanceof EditBox e) {
					box = e;
				}
			}
			if (box != null) {
				box.setValue(text);
			}
			return box != null;
		});
	}

	static List<String> modelSel() {
		List<String> all = onRender(() -> ids(((PackSelectionModelAccessor) (Object) ((PackSelectionScreenAccessor) (Object) screen()).dragit$getOrganizer()).dragit$getEnabledPacks()));
		all.remove("mod_resources"); // the NeoForge dev profile's built-in pack
		return all;
	}

	static List<String> modelAvail() {
		return onRender(() -> ids(((PackSelectionModelAccessor) (Object) ((PackSelectionScreenAccessor) (Object) screen()).dragit$getOrganizer()).dragit$getDisabledPacks()));
	}

	public static volatile boolean SYNTH;
	public static volatile boolean REAL_HELD;

	/** Sends a button event through vanilla's own MouseHandler, so that the mod's mixin sees it like a real one. */
	static void realButton(int action, int mods) {
		onRender(() -> {
			Minecraft mc = Minecraft.getInstance();
			long handle = GuiCompat.handle(mc.getWindow());
			try {
				SYNTH = true;
				for (var m : net.minecraft.client.MouseHandler.class.getDeclaredMethods()) {
					Class<?>[] t = m.getParameterTypes();
					if (m.getName().equals("onButton") && t.length == 3 && t[0] == long.class) {
						m.setAccessible(true);
						Object info = Class.forName("net.minecraft.client.input.MouseButtonInfo").getConstructor(int.class, int.class).newInstance(GuiCompat.MOUSE_LEFT, mods);
						m.invoke(mc.mouseHandler, handle, info, action);
						return;
					}
					if (m.getName().equals("onPress") && t.length == 4) {
						m.setAccessible(true);
						m.invoke(mc.mouseHandler, handle, GuiCompat.MOUSE_LEFT, action, mods);
						return;
					}
				}
				throw new IllegalStateException("no button handler found");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			} finally {
				SYNTH = false;
			}
		});
	}

	/** Sends a key press through {@code Screen.keyPressed}, as vanilla's keyboard handler does. */
	static boolean realKey(int key, int mods) {
		return onRender(() -> {
			try {
				for (var m : net.minecraft.client.gui.screens.Screen.class.getDeclaredMethods()) {
					Class<?>[] t = m.getParameterTypes();
					if (m.getName().equals("keyPressed") && t.length == 1) {
						Object event = t[0].getConstructor(int.class, int.class, int.class).newInstance(key, 0, mods);
						return (Boolean) m.invoke(screen(), event);
					}
					if (m.getName().equals("keyPressed") && t.length == 3) {
						return (Boolean) m.invoke(screen(), key, 0, mods);
					}
				}
				throw new IllegalStateException("no keyPressed found");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
	}

	static void realDrag(String label, double[] from, double[] to) {
		pointer(from[0], from[1]);
		HELD = true;
		realButton(com.mojang.blaze3d.platform.InputConstants.PRESS, 0);
		sleep(150);
		moveTo(from, to, 12);
		sleep(900);
		log(label + " mid " + state());
		pointer(to[0], to[1]);
		realButton(0, 0);
		HELD = false;
		sleep(1200);
		log(label + " after " + state());
	}

	static void run() {
		try {
			waitFor(() -> Minecraft.getInstance() != null, 120_000);
			Minecraft mc = Minecraft.getInstance();
			waitFor(() -> ready(mc), 180_000);
			sleep(2000);
			onRender(() -> mc.getResourcePackRepository().reload()); // pick up the folder packs made by makePacks
			sleep(500);
			onRender(() -> GuiCompat.setScreen(mc, new PackSelectionScreen(mc.getResourcePackRepository(), r -> {}, mc.getResourcePackDirectory(), Component.literal("Resource Packs"))));
			sleep(3000);
			onRender(() -> screen().setFocused(null));
			log("initial " + state());
			shot("01-initial");

			// geometry: every visible row must hit-test to itself, at scroll 0 and scrolled
			for (double scroll : new double[]{0, 50}) {
				onRender(() -> ctl().list(0).setScrollAmount(scroll));
				sleep(300);
				int bad = onRender(() -> {
					TransferableSelectionList l = ctl().list(0);
					int mismatches = 0;
					for (int i = 0; i < ListGeometry.entries(l).size(); i++) {
						double y = ListGeometry.rowTop(l, i) + 16;
						if (y > ListGeometry.top(l) + 4 && y < ListGeometry.bottom(l) - 4 && ListGeometry.hit(l, ListGeometry.contentLeft(l) + 70, y) != i) {
							mismatches++;
						}
					}
					return mismatches;
				});
				expect("G", "hit mismatches at scroll " + scroll, bad, 0);
				shot("G-scroll" + (int) scroll);
			}
			onRender(() -> ctl().list(0).setScrollAmount(0));
			sleep(300);

			drag("S1", row(0, idx(0, "file/TestC")), gap(1, 0), true);
			expect("S1", "selected", sel(), List.of("file/TestC", "vanilla"));
			drag("S2", row(0, idx(0, "file/TestA")), gap(1, sel().size()), false);
			expect("S2", "selected", sel(), List.of("file/TestC", "vanilla", "file/TestA"));
			// rows are looked up by pack id: NeoForge's extra mod_resources pack shifts the indexes
			drag("S3", row(1, idx(1, "file/TestA")), gap(1, 0), true);
			expect("S3", "selected", sel(), List.of("file/TestA", "file/TestC", "vanilla"));
			drag("S3b", row(1, idx(1, "file/TestA")), gap(1, sel().size() - 1), false);
			expect("S3b", "selected", sel(), List.of("file/TestC", "vanilla", "file/TestA"));
			// outside drop
			double[] r0 = row(0, 0);
			String before = state();
			drag("S4", r0, new double[]{r0[0] - 140, r0[1] + 10}, false);
			expect("S4", "selected unchanged", sel(), List.of("file/TestC", "vanilla", "file/TestA"));
			// Esc
			press(row(0, 0), 0);
			sleep(150);
			moveTo(row(0, 0), gap(1, 0), 10);
			sleep(500);
			boolean esc = onRender(() -> ctl().onEscape());
			sleep(1200);
			release(gap(1, 0));
			expect("S5", "esc used", esc, true);
			expect("S5", "selected unchanged", sel(), List.of("file/TestC", "vanilla", "file/TestA"));
			// multi-select with Ctrl+click, send with Enter
			double[] a = row(0, idx(0, "file/TestB"));
			press(a, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
			release(a);
			double[] b = row(0, idx(0, "file/TestD"));
			press(b, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
			release(b);
			sleep(900);
			shot("S6-multi");
			// dragging a selected pack lifts the whole group
			double[] ga = row(0, idx(0, "file/TestB"));
			press(ga, 0);
			sleep(150);
			moveTo(ga, gap(1, 1), 12);
			sleep(900);
			log("S6 group mid " + state());
			shot("S6-group-mid");
			release(gap(1, 1));
			sleep(1200);
			log("S6 group after " + state());
			expect("S6", "group landed after TestC", sel().subList(0, 3), List.of("file/TestC", "file/TestB", "file/TestD"));
			shot("S6-after");
			// Enter sends the group back
			boolean key = onRender(() -> ctl().onKey(GuiCompat.KEY_ENTER, 0));
			sleep(1200);
			expect("S6", "enter used", key, true);
			log("S6 enter " + state());
			// search box: dragging works on the shown packs and lands at the matching spot of the model's list
			boolean searched = setSearch("Test");
			if (searched) {
				sleep(800);
				shot("S7-filtered");
				log("S7 state " + state());
				expect("S7", "shown enabled", sel(), List.of("file/TestC", "file/TestA"));
				expect("S7", "model enabled", modelSel(), List.of("file/TestC", "vanilla", "file/TestA"));
				// a plain click on another pack ends the group selection left over from S6
				double[] plain = row(0, idx(0, "file/TestE"));
				press(plain, 0);
				release(plain);
				sleep(500);
				// T1: from the other list, between two shown packs with a hidden one (Default) between them
				drag("T1", row(0, idx(0, "file/TestB")), gap(1, 1), true);
				expect("T1", "model enabled", modelSel(), List.of("file/TestC", "vanilla", "file/TestB", "file/TestA"));
				expect("T1", "model available", modelAvail(), List.of("file/TestD", "file/TestE", "high_contrast", "programmer_art"));
				// T2: inside the list, to the top
				drag("T2", row(1, idx(1, "file/TestA")), gap(1, 0), false);
				expect("T2", "model enabled", modelSel(), List.of("file/TestA", "file/TestC", "vanilla", "file/TestB"));
				// T3: into the other list, between two shown packs
				drag("T3", row(1, idx(1, "file/TestB")), gap(0, 1), false);
				expect("T3", "model available", modelAvail(), List.of("file/TestD", "file/TestB", "file/TestE", "high_contrast", "programmer_art"));
				expect("T3", "model enabled", modelSel(), List.of("file/TestA", "file/TestC", "vanilla"));
				// T4: to the end of the shown list: right after the last shown pack, above the hidden Default pack
				drag("T4", row(1, idx(1, "file/TestA")), gap(1, 1), false);
				expect("T4", "model enabled", modelSel(), List.of("file/TestC", "file/TestA", "vanilla"));
				// T5: a group from the available list, to the top
				double[] gd = row(0, idx(0, "file/TestD"));
				press(gd, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
				release(gd);
				double[] gbb = row(0, idx(0, "file/TestB"));
				press(gbb, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
				release(gbb);
				sleep(600);
				drag("T5", row(0, idx(0, "file/TestD")), gap(1, 0), true);
				expect("T5", "model enabled", modelSel(), List.of("file/TestD", "file/TestB", "file/TestC", "file/TestA", "vanilla"));
				expect("T5", "model available", modelAvail(), List.of("file/TestE", "high_contrast", "programmer_art"));
				setSearch("");
				sleep(800);
				expect("T6", "shown enabled after clearing", sel(), List.of("file/TestD", "file/TestB", "file/TestC", "file/TestA", "vanilla"));
			} else {
				log("S7 skipped (no search box in this version)");
			}
			// the real input path: events go through vanilla's MouseHandler and Screen.keyPressed, so the mixins and constants are used
			REAL_HELD = true;
			boolean held = onRender(() -> GuiCompat.leftButtonHeld());
			REAL_HELD = false;
			expect("R0", "platform says the left button is up", held, false);
			List<String> availBefore = modelAvail();
			String first = availBefore.get(0);
			realDrag("R1", row(0, 0), gap(1, 0));
			expect("R1", "dragged pack is first enabled", modelSel().get(0), first);
			// Ctrl+click two packs, then Enter sends both
			List<String> avail2 = modelAvail();
			String a1 = avail2.get(0);
			String a2 = avail2.get(1);
			pointer(row(0, 0)[0], row(0, 0)[1]);
			HELD = true;
			realButton(com.mojang.blaze3d.platform.InputConstants.PRESS, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
			realButton(0, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
			HELD = false;
			sleep(400);
			double[] second = row(0, 1);
			pointer(second[0], second[1]);
			HELD = true;
			realButton(com.mojang.blaze3d.platform.InputConstants.PRESS, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
			realButton(0, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
			HELD = false;
			sleep(400);
			boolean enter = realKey(GuiCompat.KEY_ENTER, 0);
			sleep(900);
			log("R2 " + state());
			expect("R2", "enter used", enter, true);
			expect("R2", "both packs enabled", modelSel().containsAll(List.of(a1, a2)), true);
			// Ctrl+A with the pointer over the available list, then Enter
			pointer(row(0, 0)[0], row(0, 0)[1]);
			boolean all = realKey(GuiCompat.KEY_A, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
			sleep(400);
			boolean moved = realKey(GuiCompat.KEY_ENTER, 0);
			sleep(900);
			log("R3 " + state());
			expect("R3", "ctrl+a used", all, true);
			expect("R3", "enter used", moved, true);
			expect("R3", "available list is empty", modelAvail().isEmpty(), true);
			// Esc during a drag cancels it
			List<String> selBefore = modelSel();
			double[] r = row(1, 0);
			pointer(r[0], r[1]);
			HELD = true;
			realButton(com.mojang.blaze3d.platform.InputConstants.PRESS, 0);
			sleep(150);
			moveTo(r, gap(1, 3), 10);
			sleep(500);
			boolean esc2 = realKey(GuiCompat.KEY_ESCAPE, 0);
			sleep(1200);
			pointer(gap(1, 3)[0], gap(1, 3)[1]);
			realButton(0, 0);
			HELD = false;
			sleep(600);
			expect("R4", "esc used", esc2, true);
			expect("R4", "order unchanged", modelSel(), selBefore);
			// Space must do what Enter does. R5: one pack chosen with a plain click (vanilla's own selection)
			String single = modelSel().stream().filter(n -> n.startsWith("file/")).findFirst().orElseThrow();
			double[] sp = row(1, idx(1, single));
			pointer(sp[0], sp[1]);
			HELD = true;
			realButton(com.mojang.blaze3d.platform.InputConstants.PRESS, 0);
			realButton(0, 0);
			HELD = false;
			sleep(400);
			log("R5 clicked " + single + ", focus=" + onRender(() -> String.valueOf(screen().getFocused())));
			// The click itself must select the pack (vanilla 1.20.1 does not; the mod makes it).
			String picked = onRender(() -> {
				var entry = ListGeometry.selected(ctl().list(1));
				return entry == null ? null : entry.getPackId();
			});
			expect("R5", "the click selected the pack", single.equals(picked), true);
			boolean space1 = realKey(GuiCompat.KEY_SPACE, 0);
			sleep(900);
			expect("R5", "space used", space1, true);
			expect("R5", "pack moved to the available list", modelAvail().contains(single), true);
			// R6 / R7: a Ctrl+click group while the search box has the focus (as when the screen opens, and after drags that never reach vanilla)
			for (int round = 0; round < 2; round++) {
				String label = round == 0 ? "R6" : "R7";
				int keyCode = round == 0 ? GuiCompat.KEY_SPACE : GuiCompat.KEY_ENTER;
				int from = 1;
				List<String> pool = modelSel().stream().filter(n -> n.startsWith("file/")).toList();
				String g1 = pool.get(0);
				String g2 = pool.get(1);
				boolean focused = focusSearch();
				if (!focused) {
					log(label + " skipped (no search box in this version)");
					continue;
				}
				for (String name : new String[]{g1, g2}) {
					double[] at = row(from, idx(from, name));
					pointer(at[0], at[1]);
					HELD = true;
					realButton(com.mojang.blaze3d.platform.InputConstants.PRESS, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
					realButton(0, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL);
					HELD = false;
					sleep(400);
				}
				log(label + " before key, focus=" + onRender(() -> String.valueOf(screen().getFocused())));
				boolean used = realKey(keyCode, 0);
				sleep(900);
				List<String> target = modelAvail();
				expect(label, "key used", used, true);
				expect(label, "both packs moved", target.containsAll(List.of(g1, g2)), true);
			}
			log("DONE");
		} catch (Throwable t) {
			log("FAILED " + t);
			t.printStackTrace(System.out);
		}
		sleep(1500);
		Runtime.getRuntime().halt(0);
	}
}
