package com.dragit.mixin.accessor;

import java.util.List;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The organizer's own lists are the single source of truth for what "Done" applies, so the mod edits them directly.
 * Vanilla has no "insert at index" operation; these accessors are what makes one possible.
 */
@Mixin(ResourcePackOrganizer.class)
public interface ResourcePackOrganizerAccessor {
	@Accessor("enabledPacks")
	List<ResourcePackProfile> dragit$getEnabledPacks();

	@Accessor("disabledPacks")
	List<ResourcePackProfile> dragit$getDisabledPacks();

	/** Rebuilds both widget lists (vanilla's own "something changed" callback). */
	@Accessor("updateCallback")
	Runnable dragit$getUpdateCallback();

	@Invoker("refreshEnabledProfiles")
	void dragit$refreshEnabledProfiles();
}
