package dev.vercim.dragit.drag;

/** Implemented (via mixin) by {@code PackScreen}: every screen instance owns one {@link DragController}. */
public interface ControllerHolder {
	DragController dragit$getController();
}
