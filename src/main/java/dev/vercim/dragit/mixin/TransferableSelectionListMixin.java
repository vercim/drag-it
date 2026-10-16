package dev.vercim.dragit.mixin;

//? if >=1.21.1 && <1.21.10 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
*///?}
import dev.vercim.dragit.drag.ControllerHolder;
import dev.vercim.dragit.mixin.accessor.TransferableSelectionListAccessor;
//? if >=1.21.1 && <1.21.10 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
//? if >=1.21.10 || <1.21.1 {
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * Vanilla draws the selection frame instantly at the row's layout position. The mod draws it itself from the entry's
 * render hook (same geometry and colours, but fading in/out and following the sliding entry), so vanilla's is skipped.
 *
 * <p>In 1.21.1 and 1.21.4 {@code extractSelection} is declared by {@code TransferableSelectionList}, which is wrapped. In 1.20.1
 * and from 1.21.10 only the superclass {@code AbstractSelectionList} declares it, so the mixin targets that and checks for the
 * pack lists itself. From 1.21.10 its entry parameter is a protected class the mixin cannot name (and a handler must match the
 * erased type exactly), so the handler takes no arguments at all; 1.20.1 takes the same shape, because Forge 1.20.1 ships
 * without MixinExtras and a plain {@code @Inject} is all it has.
 */
//? if >=1.21.10 || <1.21.1 {
@Mixin(AbstractSelectionList.class)
public abstract class TransferableSelectionListMixin {
	@Inject(method = "extractSelection", at = @At("HEAD"), cancellable = true)
	private void dragit$skipVanillaHighlight(CallbackInfo ci) {
		if ((Object) this instanceof TransferableSelectionList list
			&& ((TransferableSelectionListAccessor) (Object) list).dragit$getScreen() instanceof ControllerHolder) {
			ci.cancel();
		}
	}
}
//?} else {
/*@Mixin(TransferableSelectionList.class)
public abstract class TransferableSelectionListMixin {
	@WrapMethod(method = "extractSelection")
	private void dragit$skipVanillaHighlight(
		GuiGraphicsExtractor context, int y, int entryWidth, int entryHeight, int borderColor, int fillColor, Operation<Void> original
	) {
		if (((TransferableSelectionListAccessor) (Object) this).dragit$getScreen() instanceof ControllerHolder) {
			return;
		}
		original.call(context, y, entryWidth, entryHeight, borderColor, fillColor);
	}
}
*///?}
