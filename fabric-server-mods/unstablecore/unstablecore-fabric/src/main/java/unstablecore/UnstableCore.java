package unstablecore;

import java.nio.file.Path;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnstableCore implements ModInitializer {
	public static final String MOD_ID = "unstablecore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		ImmortalityConfig config = ImmortalityConfig.load(dir.resolve("immortality.json"));
		ImmortalityManager manager = new ImmortalityManager(dir.resolve("immortal-players.json"));
		manager.load();

		ImmortalityListener.register(manager, config);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				ImmortalCommand.register(dispatcher, manager));
	}

	/** Same look as the plugin: the whole message in dark purple, starting with [UnstableCore]. */
	public static Text msg(String text) {
		return Text.literal("[UnstableCore] " + text).formatted(Formatting.DARK_PURPLE);
	}
}
