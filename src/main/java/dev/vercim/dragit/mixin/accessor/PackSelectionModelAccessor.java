package dev.vercim.dragit.mixin.accessor;

import java.util.List;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The organizer's own lists are the single source of truth for what "Done" applies, so the mod edits them directly.
 * Vanilla has no "insert at index" operation; these accessors are what makes one possible.
 */
@Mixin(PackSelectionModel.class)
public interface PackSelectionModelAccessor {
	@Accessor("selected")
	List<Pack> dragit$getEnabledPacks();

	@Accessor("unselected")
	List<Pack> dragit$getDisabledPacks();

	/** Rebuilds both widget lists (vanilla's own "something changed" callback). */
	@Accessor("onListChanged")
	Runnable dragit$getUpdateCallback();

	@Invoker("updateRepoSelectedList")
	void dragit$refreshEnabledProfiles();
}
