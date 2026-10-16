package dev.vercim.dragit.mixin.accessor;

//? if >=1.21.4 {
/*import net.minecraft.client.gui.components.AbstractScrollArea;
*///?} else {
import net.minecraft.client.gui.components.AbstractSelectionList;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The scrollbar logic. Up to 1.21.1 it is declared by {@code AbstractSelectionList}; from 1.21.4 by its new superclass
 * {@code AbstractScrollArea}. An invoker only finds methods the target class declares itself, so the target follows the version.
 * From 26.1 the method is called {@code scrollable}. 1.20.1 has no such method (the scrollbar shows when {@code getMaxScroll()}
 * is above zero, which is public), so there the interface stays empty and {@code ListGeometry.scrollbarVisible} asks the list directly.
 */
//? if >=1.21.4 {
/*@Mixin(AbstractScrollArea.class)
*///?} else {
@Mixin(AbstractSelectionList.class)
//?}
public interface AbstractScrollAreaAccessor {
	//? if >=26.1 {
	/*@Invoker("scrollable")
	boolean dragit$isScrollbarVisible();
	*///?} else if >=1.21.1 {
	@Invoker("scrollbarVisible")
	boolean dragit$isScrollbarVisible();
	//?}
}
