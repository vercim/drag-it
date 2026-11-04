package dev.vercim.dragit.mixin;

//? if >=26.3 {
import dev.vercim.dragit.drag.FileDropHover;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * SDL (26.3+) is the first to report a file drag while it still hovers over the window: these hooks feed
 * {@code FileDropHover}, which draws the window and drop-zone frames. The target class exists only from 26.3 on, so
 * {@link DragItMixinPlugin} skips this mixin on every older version (the class itself stays in the JAR for
 * {@code verifyJar}, with an empty body).
 */
@Mixin(targets = "com.mojang.blaze3d.platform.SDLEventHandler")
public abstract class SDLEventHandlerMixin {
	//? if >=26.3 {
	@Inject(method = "handleDropBeginEvent", at = @At("HEAD"))
	private void dragit$onDropBegin(CallbackInfo ci) {
		FileDropHover.begin();
	}

	@Inject(method = "handleDropCompleteEvent", at = @At("HEAD"))
	private void dragit$onDropComplete(org.lwjgl.sdl.SDL_Event event, CallbackInfo ci) {
		FileDropHover.end();
	}
	//?}
}
