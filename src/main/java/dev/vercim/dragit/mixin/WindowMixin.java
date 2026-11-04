package dev.vercim.dragit.mixin;

//? if >=26.3 {
import dev.vercim.dragit.drag.FileDropHover;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDLEvents;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * SDL's drop-position events (the cursor moving over the window during a file drag) fall through the switch of
 * {@code SDLEventHandler.pollEvents} into {@code Window.handleEvent}, whose own switch ignores them; this hook reads
 * the cursor position out of them for {@code FileDropHover}. The method exists only from 26.3 on, so
 * {@link DragItMixinPlugin} skips this mixin on every older version.
 */
@Mixin(targets = "com.mojang.blaze3d.platform.Window")
public abstract class WindowMixin {
	//? if >=26.3 {
	@Inject(method = "handleEvent", at = @At("HEAD"))
	private void dragit$onEvent(SDL_Event event, CallbackInfo ci) {
		if (event.type() == SDLEvents.SDL_EVENT_DROP_POSITION) {
			FileDropHover.move(event.drop().x(), event.drop().y());
		}
	}
	//?}
}
