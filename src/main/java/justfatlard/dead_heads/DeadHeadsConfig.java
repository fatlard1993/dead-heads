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
	private static boolean mobHeadsDefault = false;
	private static int mobHeadDecaySeconds = 600;

	private static final String DEFAULT_CONFIG = """
			# Dead Heads Configuration
			# Delete this file to regenerate with defaults.

			# Minutes before a death head unlocks for other players to loot.
			# Set to 0 to disable locking entirely.
			lock_duration_minutes=5

			# Starting value of the dead-heads:mob_heads game rule for worlds that
			# have never set it. With the rule on, a mob killed by a player leaves
			# its drops inside a head block instead of scattering item entities.
			# Off by default so existing worlds are unchanged; any world can still
			# override this in-game with /gamerule dead-heads:mob_heads true
			mob_heads_default=false

			# Seconds a mob head survives before it rots. Vanilla items despawn
			# after 6000 ticks (300 seconds), so the default is twice that.
			# When a head rots its contents are consumed, not dropped, and the
			# ground around it gets a bonemeal growth instead.
			# Set to 0 to keep mob heads forever.
			mob_head_decay_seconds=600
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
		mobHeadsDefault = getBoolean(props, "mob_heads_default", mobHeadsDefault);
		mobHeadDecaySeconds = getInt(props, "mob_head_decay_seconds", mobHeadDecaySeconds);
		LOGGER.info("[{}] Config loaded from {}", Main.MOD_ID, CONFIG_PATH);
	}

	public static long getLockDurationMs() {
		return lockDurationMinutes * 60L * 1000L;
	}

	public static boolean mobHeadsDefault() {
		return mobHeadsDefault;
	}

	public static long getMobHeadDecayMs() {
		return mobHeadDecaySeconds * 1000L;
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

	private static boolean getBoolean(Properties props, String key, boolean defaultValue) {
		String value = props.getProperty(key);
		if (value == null || value.isBlank()) return defaultValue;
		String trimmed = value.trim();
		if (trimmed.equalsIgnoreCase("true")) return true;
		if (trimmed.equalsIgnoreCase("false")) return false;
		LOGGER.warn("[{}] Invalid value for '{}': '{}', using default {}", Main.MOD_ID, key, value, defaultValue);
		return defaultValue;
	}

	private static void createDefaultConfig() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, DEFAULT_CONFIG);
		} catch (IOException e) {
			LOGGER.error("[{}] Failed to create default config: {}", Main.MOD_ID, e.getMessage());
		}
	}

	/**
	 * Write one value back into the config file, keeping the file's comments and order: the
	 * line for the key is replaced where it stands, or added at the end when it is missing.
	 */
	private static void store(String key, String value) {
		try {
			java.nio.file.Path path = CONFIG_PATH;
			java.util.List<String> lines = java.nio.file.Files.exists(path)
				? new java.util.ArrayList<>(java.nio.file.Files.readAllLines(path))
				: new java.util.ArrayList<>();
			boolean found = false;
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).trim().startsWith(key + "=") || lines.get(i).trim().startsWith(key + " =")) {
					lines.set(i, key + "=" + value);
					found = true;
				}
			}
			if (!found) lines.add(key + "=" + value);
			java.nio.file.Files.createDirectories(path.getParent());
			java.nio.file.Files.write(path, lines);
		} catch (java.io.IOException e) {
			LOGGER.warn("Could not write dead-heads config", e);
		}
	}

	public static int lockDurationMinutes() { return lockDurationMinutes; }
	public static int mobHeadDecaySeconds() { return mobHeadDecaySeconds; }

	public static void setLockDurationMinutes(int minutes) { lockDurationMinutes = minutes; store("lock_duration_minutes", String.valueOf(minutes)); }
	public static void setMobHeadsDefault(boolean on) { mobHeadsDefault = on; store("mob_heads_default", String.valueOf(on)); }
	public static void setMobHeadDecaySeconds(int seconds) { mobHeadDecaySeconds = seconds; store("mob_head_decay_seconds", String.valueOf(seconds)); }

	/** The file's knobs in the mod menu, for ops. */
	public static void menu() {
		justfatlard.pandorical.api.PandoricalApi.settings().serverGroup(Main.MOD_ID, "Dead Heads")
			.number("lockDuration", "Head lock, minutes", 0, 60, 1, 5)
			.describe("How long a dropped head is the dead player's alone")
			.backedBy(player -> lockDurationMinutes(), (player, v) -> setLockDurationMinutes(v));
		justfatlard.pandorical.api.PandoricalApi.settings().serverGroup(Main.MOD_ID, "Dead Heads")
			.toggle("mobHeads", "Mob heads by default", false)
			.describe("Mobs drop heads without being asked")
			.backedBy(player -> mobHeadsDefault(), (player, v) -> setMobHeadsDefault(v));
		justfatlard.pandorical.api.PandoricalApi.settings().serverGroup(Main.MOD_ID, "Dead Heads")
			.number("mobHeadDecay", "Mob head decay, seconds", 0, 3600, 60, 600)
			.backedBy(player -> mobHeadDecaySeconds(), (player, v) -> setMobHeadDecaySeconds(v));
	}

}
