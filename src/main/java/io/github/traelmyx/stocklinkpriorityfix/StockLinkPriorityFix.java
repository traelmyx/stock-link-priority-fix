package io.github.traelmyx.stocklinkpriorityfix;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Create issue #9689. Since Create 6.0.7 an order picks one stock link per inventory, in random order, and lines them up in
 * hash order; the redstone priority of the links (lower signal = used first) no longer decides which inventory pays.
 * The picked links are now put back in priority order, and inside one inventory the highest-priority link is picked.
 */
@Mod(StockLinkPriorityFix.MOD_ID)
public class StockLinkPriorityFix {
	public static final String MOD_ID = "stock_link_priority_fix";
	public static final Logger LOGGER = LoggerFactory.getLogger("Stock Link Priority Fix");

	public static boolean enabled = true;

	public StockLinkPriorityFix() {
		loadConfig(FMLPaths.CONFIGDIR.get());
		checkTarget();
	}

	/** The mixin is optional so a Create update can't crash a pack; say so when it no longer finds its target. */
	private static void checkTarget() {
		try {
			Class<?> manager = Class.forName("com.simibubi.create.content.logistics.packagerLink.LogisticsManager", false, StockLinkPriorityFix.class.getClassLoader());
			boolean found = false;
			for (var m : manager.getDeclaredMethods()) found |= m.getName().equals("findPackagersForRequest");
			if (!found) throw new NoSuchMethodException("findPackagersForRequest");
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("This Create version has no LogisticsManager.findPackagersForRequest; the fix is inactive ({})", e.toString());
		}
	}

	static void loadConfig(Path configDir) {
		Path file = configDir.resolve(MOD_ID + ".properties");
		Properties props = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				props.load(reader);
			} catch (IOException e) {
				LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
		props.setProperty("enabled", Boolean.toString(enabled));
		try {
			Files.createDirectories(configDir);
			try (Writer writer = Files.newBufferedWriter(file)) {
				props.store(writer, "Stock Link Priority Fix. enabled: use the stock links' redstone priority when picking which inventory an order comes from.");
			}
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", file, e);
		}
	}
}
