package dev.vercim.dragit.mixin.accessor;

import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PackListWidget.class)
public interface PackListWidgetAccessor {
	@Accessor("screen")
	PackScreen dragit$getScreen();

	@Invoker("getScrollbarX")
	int dragit$getScrollbarX();
}
