package justfatlard.dead_heads.integration;

import justfatlard.dead_heads.DeadHeadManager;

/** A locked head is spared from explosions through Pandorical's one hook, where it is installed. */
public final class BlastGuard {
	private BlastGuard() {}

	public static void register() {
		justfatlard.pandorical.api.PandoricalApi.blasts().protect(DeadHeadManager::isProtected);
	}
}
