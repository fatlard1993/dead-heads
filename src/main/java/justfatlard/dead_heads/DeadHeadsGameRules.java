package justfatlard.dead_heads;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Game rules owned by this mod. On 26.3 game rules live in a real registry
 * (Registries.GAME_RULE), so a modded rule is namespaced and shows up in
 * /gamerule as "dead-heads:mob_heads".
 *
 * The default comes from dead-heads.properties, which means an operator can
 * decide the server-wide starting point while any single world can still
 * override it in-game. Worlds that have never touched the rule follow the
 * config; worlds that have set it keep their own value.
 */
public final class DeadHeadsGameRules {
	public static GameRule<Boolean> MOB_HEADS;

	private DeadHeadsGameRules() {}

	public static void register() {
		MOB_HEADS = GameRuleBuilder.forBoolean(DeadHeadsConfig.mobHeadsDefault())
			.category(GameRuleCategory.DROPS)
			.buildAndRegister(Identifier.fromNamespaceAndPath(Main.MOD_ID, "mob_heads"));
	}

	public static boolean mobHeadsEnabled(ServerLevel level) {
		return MOB_HEADS != null && level.getGameRules().get(MOB_HEADS);
	}
}
