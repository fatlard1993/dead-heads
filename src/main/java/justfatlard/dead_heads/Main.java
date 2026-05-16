package justfatlard.dead_heads;

import net.fabricmc.api.ModInitializer;
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

		ServerLifecycleEvents.SERVER_STARTED.register(DeadHeadManager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(DeadHeadManager::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(DeadHeadManager::tick);
		UseBlockCallback.EVENT.register(DeadHeadManager::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(DeadHeadManager::onBlockBreakBefore);
		PlayerBlockBreakEvents.AFTER.register(DeadHeadManager::onBlockBreakAfter);

		LOGGER.info("Dead Heads loaded");
	}
}
