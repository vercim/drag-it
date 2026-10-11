package dev.vercim.dragit.drag;

/** Implemented (via mixin) by {@code PackSelectionScreen}: every screen instance owns one {@link DragController}. */
public interface ControllerHolder {
	DragController dragit$getController();
}
