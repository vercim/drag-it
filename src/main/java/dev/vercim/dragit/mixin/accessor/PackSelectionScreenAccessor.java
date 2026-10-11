package dev.vercim.dragit.mixin.accessor;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PackSelectionScreen.class)
public interface PackSelectionScreenAccessor {
	@Accessor("availablePackList")
	TransferableSelectionList dragit$getAvailableList();

	@Accessor("selectedPackList")
	TransferableSelectionList dragit$getSelectedList();

	@Accessor("model")
	PackSelectionModel dragit$getOrganizer();
}
