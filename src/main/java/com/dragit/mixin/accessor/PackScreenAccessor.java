package com.dragit.mixin.accessor;

import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PackScreen.class)
public interface PackScreenAccessor {
	@Accessor("availablePackList")
	PackListWidget dragit$getAvailableList();

	@Accessor("selectedPackList")
	PackListWidget dragit$getSelectedList();

	@Accessor("organizer")
	ResourcePackOrganizer dragit$getOrganizer();
}
