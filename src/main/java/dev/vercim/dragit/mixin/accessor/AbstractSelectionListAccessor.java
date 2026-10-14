package dev.vercim.dragit.mixin.accessor;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSelectionList.class)
public interface AbstractSelectionListAccessor {
	//? if >=1.21.10 {
	/*/^* Pitch of a row; from 1.21.10 each entry carries its own height and the list only keeps the default. ^/
	@Accessor("defaultEntryHeight")
	int dragit$getItemHeight();
	*///?} else {
	@Accessor("itemHeight")
	int dragit$getItemHeight();

	@Accessor("headerHeight")
	int dragit$getHeaderHeight();
	//?}
}
