package dev.vercim.dragit.mixin.accessor;

import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntryListWidget.class)
public interface EntryListWidgetAccessor {
	@Accessor("itemHeight")
	int dragit$getItemHeight();

	@Accessor("headerHeight")
	int dragit$getHeaderHeight();

	@Invoker("isScrollbarVisible")
	boolean dragit$isScrollbarVisible();
}
