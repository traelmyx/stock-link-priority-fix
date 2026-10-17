package io.github.traelmyx.stocklinkpriorityfix.test;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(StockLinkTests.MOD_ID)
public class StockLinkTestMod {
	public StockLinkTestMod(Dist dist) {
		if (dist.isClient() && ClientSmoke.start()) {
			NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientSmoke.tick(Minecraft.getInstance()));
		}
	}
}
