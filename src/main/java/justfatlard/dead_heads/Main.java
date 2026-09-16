package justfatlard.dead_heads;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
	public static final String MOD_ID = "dead-heads";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Fabric carries the dead player's attachments across from a listener of its own in the
	 * default phase. A compass put in its slot before that was overwritten by the dead player's
	 * emptied one.
	 */
	private static final Identifier RESPAWN_PHASE = Identifier.fromNamespaceAndPath(MOD_ID, "respawn");

	@Override
	public void onInitialize() {
		DeadHeadsConfig.load();
		DeadHeadsGameRules.register();
		ReckoningLoot.register();

		// Guarded class load: pandorical is compileOnly here, so naming its types has to happen
		// somewhere a server without it never reaches.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("pandorical")) {
			DeadHeadsConfig.menu();
			justfatlard.dead_heads.integration.HeadInteraction.register();
		}

		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
			(dispatcher, registry, environment) -> DeadHeadCommands.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(DeadHeadManager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(DeadHeadManager::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(DeadHeadManager::tick);
		UseBlockCallback.EVENT.register(DeadHeadManager::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(DeadHeadManager::onBlockBreakBefore);
		PlayerBlockBreakEvents.AFTER.register(DeadHeadManager::onBlockBreakAfter);
		ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Event.DEFAULT_PHASE, RESPAWN_PHASE);
		ExtraSlots.orderAfterRespawnCopy(RESPAWN_PHASE);
		ServerPlayerEvents.AFTER_RESPAWN.register(RESPAWN_PHASE, (oldPlayer, newPlayer, alive) -> {
			if (alive) return;
			Soulbound.restore(oldPlayer, newPlayer);
			DeadHeadManager.onRespawn(newPlayer);
		});

		LOGGER.info("Dead Heads loaded");
	}
}
