package dev.vercim.dragit.drag;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
//? if >=1.21.10 {
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}
//? if >=26.3 {
import org.lwjgl.sdl.SDLMouse;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}

/**
 * Drawing and window calls whose names or shapes differ between Minecraft versions, so the rest of the mod stays version-free.
 * Up to 1.21.1 the GUI matrix stack is a 3D {@code PoseStack} and entries are drawn by {@code render}; from 1.21.10 it is a
 * 2D {@code Matrix3x2fStack}, depth follows draw order, and entries are drawn by {@code extractContent} at their own position.
 */
public final class GuiCompat {
	private GuiCompat() {
	}

	// ---------------------------------------------------------------- input constants

	/** Left mouse button (a button number, as in the button events). */
	public static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
	public static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;
	public static final int KEY_ENTER = InputConstants.KEY_RETURN;
	public static final int KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;
	public static final int KEY_SPACE = InputConstants.KEY_SPACE;
	public static final int KEY_A = InputConstants.KEY_A;

	/** The button or key action of an event is a press (not a release or a repeat). */
	public static boolean isPress(int action) {
		return action == InputConstants.PRESS;
	}

	public static boolean hasControl(int modifiers) {
		return (modifiers & InputConstants.MOD_CONTROL) != 0;
	}

	public static boolean hasShift(int modifiers) {
		//? if >=26.3 {
		return (modifiers & InputConstants.MOD_SHIFT) != 0;
		//?} else {
		/*return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		*///?}
	}

	/** Asks the platform whether the left mouse button is down right now (vanilla stops tracking it once the mod swallows a press). */
	public static boolean leftButtonHeld() {
		//? if >=26.3 {
		return (SDLMouse.SDL_GetMouseState(null, null) & SDLMouse.SDL_BUTTON_LMASK) != 0;
		//?} else {
		/*return GLFW.glfwGetMouseButton(handle(Minecraft.getInstance().getWindow()), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		*///?}
	}

	// ---------------------------------------------------------------- screen access

	/** The screen that is open, or {@code null} ({@code Minecraft.screen} up to 26.1, {@code gui.screen()} from 26.2). */
	public static Screen screen(Minecraft minecraft) {
		//? if >=26.2 {
		return minecraft.gui.screen();
		//?} else {
		/*return minecraft.screen;
		*///?}
	}

	/** A loading overlay covers the screen. */
	public static boolean overlayShown(Minecraft minecraft) {
		//? if >=26.2 {
		return minecraft.gui.overlay() != null;
		//?} else {
		/*return minecraft.getOverlay() != null;
		*///?}
	}

	public static void setScreen(Minecraft minecraft, Screen screen) {
		//? if >=26.2 {
		minecraft.gui.setScreen(screen);
		//?} else {
		/*minecraft.setScreen(screen);
		*///?}
	}

	/** GLFW handle of the window. */
	public static long handle(Window window) {
		//? if >=1.21.10 {
		return window.handle();
		//?} else {
		/*return window.getWindow();
		*///?}
	}

	/** Replays a left-button press that the mod held back, so that vanilla handles it as an ordinary click. */
	public static void click(Screen screen, double x, double y) {
		//? if >=1.21.10 {
		screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(MOUSE_LEFT, 0)), false);
		//?} else {
		/*screen.mouseClicked(x, y, MOUSE_LEFT);
		*///?}
	}

	/** Left-button release matching {@link #click}. */
	public static void release(Screen screen, double x, double y) {
		//? if >=1.21.10 {
		screen.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(MOUSE_LEFT, 0)));
		//?} else {
		/*screen.mouseReleased(x, y, MOUSE_LEFT);
		*///?}
	}

	public static void push(GuiGraphicsExtractor context) {
		//? if >=1.21.10 {
		context.pose().pushMatrix();
		//?} else {
		/*context.pose().pushPose();
		*///?}
	}

	public static void pop(GuiGraphicsExtractor context) {
		//? if >=1.21.10 {
		context.pose().popMatrix();
		//?} else {
		/*context.pose().popPose();
		*///?}
	}

	public static void translate(GuiGraphicsExtractor context, float x, float y) {
		//? if >=1.21.10 {
		context.pose().translate(x, y);
		//?} else {
		/*context.pose().translate(x, y, 0);
		*///?}
	}

	public static void scale(GuiGraphicsExtractor context, float x, float y) {
		//? if >=1.21.10 {
		context.pose().scale(x, y);
		//?} else {
		/*context.pose().scale(x, y, 1);
		*///?}
	}

	/** Puts everything drawn from now on above the rest of the screen (older versions order by depth, newer ones by draw order). */
	public static void raise(GuiGraphicsExtractor context) {
		//? if <1.21.10 {
		/*context.pose().translate(0, 0, 200);
		*///?}
	}

	/**
	 * One-pixel outline of the box {@code x, y, width, height}: the four fills vanilla's {@code renderOutline} makes. Drawn here
	 * because 1.21.10's {@code submitOutline} defers the outline to the end of the frame, above everything drawn after it, which
	 * breaks the stacking of the lifted cards (1.21.11 draws it at once again, other versions have no such method).
	 */
	public static void outline(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y + 1, x + 1, y + height - 1, color);
		context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	/** Draws {@code text} without a shadow at {@code x, y} ({@code drawString} before 26.1, {@code text} from 26.1). */
	public static void text(GuiGraphicsExtractor context, Font font, String text, int x, int y, int color) {
		//? if >=26.1 {
		context.text(font, text, x, y, color, false);
		//?} else {
		/*context.drawString(font, text, x, y, color, false);
		*///?}
	}

	/**
	 * Draws an entry's content as if its content box started at {@code x, y} ({@code index} is the entry's row; the width and
	 * height are those vanilla gives it) with no hover state. Used for the lifted card.
	 */
	public static void drawEntry(
		GuiGraphicsExtractor context, TransferableSelectionList.PackEntry entry, int index, int x, int y, int width, int height, float delta
	) {
		//? if >=1.21.10 {
		push(context);
		translate(context, x - entry.getContentX(), y - entry.getContentY());
		entry.extractContent(context, -1000, -1000, false, delta);
		pop(context);
		//?} else {
		/*entry.render(context, index, y, x, width, height, -1000, -1000, false, delta);
		*///?}
	}
}
