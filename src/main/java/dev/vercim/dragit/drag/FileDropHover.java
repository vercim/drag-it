package dev.vercim.dragit.drag;

import net.minecraft.client.Minecraft;

/**
 * State of the operating system's file drag while it hovers over the game window. Only SDL (Minecraft 26.3+) reports
 * such events (drop begin / position / complete); on GLFW versions nothing ever calls this and the hover stays off.
 * Fed by the SDLEventHandler / Window mixins, drawn by {@link DragRenderer} on the pack selection screen.
 */
public final class FileDropHover {
	private static boolean active;
	/** Cursor position in window pixels; SDL drop events carry their own because the OS owns the cursor during a file drag. */
	private static double x;
	private static double y;

	private FileDropHover() {
	}

	/** SDL_EVENT_DROP_BEGIN: a file drag entered the window. It carries no position, so the last known cursor spot is used. */
	public static void begin() {
		active = true;
		Minecraft client = Minecraft.getInstance();
		x = client.mouseHandler.xpos();
		y = client.mouseHandler.ypos();
	}

	/** SDL_EVENT_DROP_POSITION: the drag moved over the window (window pixels). */
	public static void move(double newX, double newY) {
		x = newX;
		y = newY;
	}

	/** SDL_EVENT_DROP_COMPLETE: the files were dropped, or the drag left the window without dropping. */
	public static void end() {
		active = false;
	}

	public static boolean active() {
		return active;
	}

	public static double x() {
		return x;
	}

	public static double y() {
		return y;
	}
}
