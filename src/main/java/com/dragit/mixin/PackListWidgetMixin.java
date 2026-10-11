package com.dragit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.dragit.drag.ControllerHolder;
import com.dragit.mixin.accessor.PackListWidgetAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PackListWidget.class)
public abstract class PackListWidgetMixin {
	/**
	 * Vanilla draws the selection frame instantly at the row's layout position. The mod draws it itself from the entry's
	 * render hook (same geometry and colours, but fading in/out and following the sliding entry), so vanilla's is skipped.
	 */
	@WrapMethod(method = "drawSelectionHighlight")
	private void dragit$skipVanillaHighlight(
		DrawContext context, int y, int entryWidth, int entryHeight, int borderColor, int fillColor, Operation<Void> original
	) {
		if (((PackListWidgetAccessor) (Object) this).dragit$getScreen() instanceof ControllerHolder) {
			return;
		}
		original.call(context, y, entryWidth, entryHeight, borderColor, fillColor);
	}
}
