package dev.vercim.dragit.drag;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
//? if >=1.21.10 {
/*import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
*///?}
import org.lwjgl.glfw.GLFW;

/**
 * Drawing and window calls whose names or shapes differ between Minecraft versions, so the rest of the mod stays version-free.
 * Up to 1.21.1 the GUI matrix stack is a 3D {@code PoseStack} and entries are drawn by {@code render}; from 1.21.10 it is a
 * 2D {@code Matrix3x2fStack}, depth follows draw order, and entries are drawn by {@code renderContent} at their own position.
 */
public final class GuiCompat {
	private GuiCompat() {
	}

	/** GLFW handle of the window. */
	public static long handle(Window window) {
		//? if >=1.21.10 {
		/*return window.handle();
		*///?} else {
		return window.getWindow();
		//?}
	}

	/** Replays a left-button press that the mod held back, so that vanilla handles it as an ordinary click. */
	public static void click(Screen screen, double x, double y) {
		//? if >=1.21.10 {
		/*screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
		*///?} else {
		screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
		//?}
	}

	/** Left-button release matching {@link #click}. */
	public static void release(Screen screen, double x, double y) {
		//? if >=1.21.10 {
		/*screen.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)));
		*///?} else {
		screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
		//?}
	}

	public static void push(GuiGraphics context) {
		//? if >=1.21.10 {
		/*context.pose().pushMatrix();
		*///?} else {
		context.pose().pushPose();
		//?}
	}

	public static void pop(GuiGraphics context) {
		//? if >=1.21.10 {
		/*context.pose().popMatrix();
		*///?} else {
		context.pose().popPose();
		//?}
	}

	public static void translate(GuiGraphics context, float x, float y) {
		//? if >=1.21.10 {
		/*context.pose().translate(x, y);
		*///?} else {
		context.pose().translate(x, y, 0);
		//?}
	}

	public static void scale(GuiGraphics context, float x, float y) {
		//? if >=1.21.10 {
		/*context.pose().scale(x, y);
		*///?} else {
		context.pose().scale(x, y, 1);
		//?}
	}

	/** Puts everything drawn from now on above the rest of the screen (older versions order by depth, newer ones by draw order). */
	public static void raise(GuiGraphics context) {
		//? if <1.21.10 {
		context.pose().translate(0, 0, 200);
		//?}
	}

	/**
	 * One-pixel outline of the box {@code x, y, width, height}: the four fills vanilla's {@code renderOutline} makes. Drawn here
	 * because 1.21.10's {@code submitOutline} defers the outline to the end of the frame, above everything drawn after it, which
	 * breaks the stacking of the lifted cards (1.21.11 draws it at once again, other versions have no such method).
	 */
	public static void outline(GuiGraphics context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y + 1, x + 1, y + height - 1, color);
		context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	/**
	 * Draws an entry's content as if its content box started at {@code x, y} ({@code index} is the entry's row; the width and
	 * height are those vanilla gives it) with no hover state. Used for the lifted card.
	 */
	public static void drawEntry(
		GuiGraphics context, TransferableSelectionList.PackEntry entry, int index, int x, int y, int width, int height, float delta
	) {
		//? if >=1.21.10 {
		/*push(context);
		translate(context, x - entry.getContentX(), y - entry.getContentY());
		entry.renderContent(context, -1000, -1000, false, delta);
		pop(context);
		*///?} else {
		entry.render(context, index, y, x, width, height, -1000, -1000, false, delta);
		//?}
	}
}
