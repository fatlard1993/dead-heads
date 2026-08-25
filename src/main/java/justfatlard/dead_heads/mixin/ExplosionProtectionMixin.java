package justfatlard.dead_heads.mixin;

import java.util.ArrayList;
import java.util.List;
import justfatlard.dead_heads.DeadHeadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A locked head does not blow up.
 *
 * <p>Taken out of the blast's list rather than given a high resistance, because resistance is
 * consulted while the list is drawn up and the head does not exist yet at that point: the
 * explosion decides what it will clear, kills the player, and dead-heads puts a head down inside
 * the area already condemned. By the time anything could object, the position is spoken for.
 * Filtering the list on the way into the clearing pass is the one place that sees both the
 * decision and the head.
 *
 * <p>Only while locked, which is what the lock is for. Once a head has opened to everybody it
 * takes its chances like any other block.
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionProtectionMixin {

	@Shadow @Final private ServerLevel level;

	@ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
	private List<BlockPos> deadHeads$spareLockedHeads(List<BlockPos> positions) {
		List<BlockPos> spared = null;

		for (BlockPos pos : positions) {
			if (!DeadHeadManager.isProtected(this.level, pos)) continue;
			// Only copied once something actually needs sparing, which is almost never: every
			// other explosion on the server keeps the list it arrived with.
			if (spared == null) spared = new ArrayList<>(positions);
			spared.remove(pos);
		}
		return spared == null ? positions : spared;
	}
}
