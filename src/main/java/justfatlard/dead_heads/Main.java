package justfatlard.dead_heads;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
	public static final String MOD_ID = "dead-heads";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DeadHeadsConfig.load();
		DeadHeadsGameRules.register();

		// Guarded class load: pandorical is compileOnly here, so naming its types has to happen
		// somewhere a server without it never reaches.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("pandorical")) {
			justfatlard.dead_heads.integration.HeadInteraction.register();
		}

		ServerLifecycleEvents.SERVER_STARTED.register(DeadHeadManager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(DeadHeadManager::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(DeadHeadManager::tick);
		UseBlockCallback.EVENT.register(DeadHeadManager::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(DeadHeadManager::onBlockBreakBefore);
		PlayerBlockBreakEvents.AFTER.register(DeadHeadManager::onBlockBreakAfter);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) DeadHeadManager.onRespawn(newPlayer);
		});

		LOGGER.info("Dead Heads loaded");
	}
}
