package justfatlard.dead_heads.integration;

import justfatlard.dead_heads.DeadHeadManager;

/**
 * A locked head is spared from explosions through Pandorical's one hook, where it is installed and new
 * enough to have one; otherwise this mod's own patch spares it.
 */
public final class BlastGuard {
	private BlastGuard() {}

	private static volatile boolean registered;

	public static void register() {
		try {
			justfatlard.pandorical.api.PandoricalApi.blasts().protect(DeadHeadManager::isProtected);
			registered = true;
		} catch (LinkageError older) {
			// Pandorical from before 15.37.0: the patch of this mod's own stays on
		}
	}

	public static boolean registered() {
		return registered;
	}
}
