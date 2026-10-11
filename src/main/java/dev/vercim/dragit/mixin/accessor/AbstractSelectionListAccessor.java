package dev.vercim.dragit.mixin.accessor;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSelectionList.class)
public interface AbstractSelectionListAccessor {
	@Accessor("itemHeight")
	int dragit$getItemHeight();

	@Accessor("headerHeight")
	int dragit$getHeaderHeight();

	@Invoker("scrollbarVisible")
	boolean dragit$isScrollbarVisible();
}
