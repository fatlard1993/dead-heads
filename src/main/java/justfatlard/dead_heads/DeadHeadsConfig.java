package justfatlard.dead_heads;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeadHeadsConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("DeadHeads");
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("dead-heads.properties");

	private static int lockDurationMinutes = 5;

	private static final String DEFAULT_CONFIG = """
			# Dead Heads Configuration
			# Delete this file to regenerate with defaults.

			# Minutes before a death head unlocks for other players to loot.
			# Set to 0 to disable locking entirely.
			lock_duration_minutes=5
			""";

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			createDefaultConfig();
			LOGGER.info("[{}] Created default config at {}", Main.MOD_ID, CONFIG_PATH);
			return;
		}

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
			props.load(in);
		} catch (IOException e) {
			LOGGER.error("[{}] Failed to read config, using defaults: {}", Main.MOD_ID, e.getMessage());
			return;
		}

		lockDurationMinutes = getInt(props, "lock_duration_minutes", lockDurationMinutes);
		LOGGER.info("[{}] Config loaded from {}", Main.MOD_ID, CONFIG_PATH);
	}

	public static long getLockDurationMs() {
		return lockDurationMinutes * 60L * 1000L;
	}

	private static int getInt(Properties props, String key, int defaultValue) {
		String value = props.getProperty(key);
		if (value == null || value.isBlank()) return defaultValue;
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			LOGGER.warn("[{}] Invalid value for '{}': '{}', using default {}", Main.MOD_ID, key, value, defaultValue);
			return defaultValue;
		}
	}

	private static void createDefaultConfig() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, DEFAULT_CONFIG);
		} catch (IOException e) {
			LOGGER.error("[{}] Failed to create default config: {}", Main.MOD_ID, e.getMessage());
		}
	}
}
