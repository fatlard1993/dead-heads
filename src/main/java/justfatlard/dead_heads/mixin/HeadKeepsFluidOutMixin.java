package justfatlard.dead_heads.mixin;

import justfatlard.dead_heads.DeadHeadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Water and lava flow around a player's head instead of into it.
 *
 * <p>A skull has no waterlogged state, and adding one would renumber the block states under every
 * vanilla client, so a head on the sea floor is a dry block that fluid is refused entry to. Every
 * spread, down or sideways, asks this one question of the block it would replace.
 */
@Mixin(FlowingFluid.class)
public abstract class HeadKeepsFluidOutMixin {

	@Inject(method = "canHoldSpecificFluid", at = @At("HEAD"), cancellable = true)
	private static void deadHeads$keepFluidOut(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid,
			CallbackInfoReturnable<Boolean> cir) {
		if (state.getBlock() instanceof SkullBlock && level instanceof Level world
				&& DeadHeadManager.keepsFluidOut(world, pos)) {
			cir.setReturnValue(false);
		}
	}
}
