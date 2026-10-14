package dev.vercim.dragit.mixin.accessor;

import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TransferableSelectionList.class)
public interface TransferableSelectionListAccessor {
	@Accessor("screen")
	PackSelectionScreen dragit$getScreen();

	//? if >=1.21.4 {
	/*@Invoker("scrollBarX")
	*///?} else {
	@Invoker("getScrollbarPosition")
	//?}
	int dragit$getScrollbarX();
}
