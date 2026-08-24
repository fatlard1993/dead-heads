package justfatlard.dead_heads.integration;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.PandoricalApi;

/**
 * Tells the client that a right-click on a player head is the server's to answer.
 *
 * <p>Without it the client does what it does with any block it has no special handling for:
 * predicts that a right-click places whatever is in hand. The server opens the head instead and
 * places nothing, so the block appears, vanishes a tick later, and leaves the stack count wrong
 * until something forces a resync.
 *
 * <p>A vanilla block rather than one of ours, which the content sync would normally skip - it
 * carries the blocks this server invented, and a skull is Mojang's. Naming it outright is the
 * only way to say that its behaviour here is not the behaviour the client assumes.
 *
 * <p>Every player head, not only the ones holding somebody's belongings, because the claim is
 * made once at startup and a head does not become a grave until somebody dies on it. The cost is
 * that a decorative skull is placed against a tick later than it used to be; the alternative is
 * that every grave in the world flickers when opened.
 */
public final class HeadInteraction {
	private HeadInteraction() {}

	public static void register() {
		PandoricalApi.content().registerBlock("minecraft:player_head",
			new BlockRegistration().interactive());
	}
}
