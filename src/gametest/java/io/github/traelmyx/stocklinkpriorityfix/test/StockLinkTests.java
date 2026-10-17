package io.github.traelmyx.stocklinkpriorityfix.test;

import com.google.common.collect.Multimap;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import io.github.traelmyx.stocklinkpriorityfix.StockLinkPriorityFix;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Two chests on one logistics network, each with a packager and a stock link, both holding 64 cobblestone. A link with a
 * lower redstone signal has priority, so an order must be taken from its chest (Create issue #9689). Inventory A can get a
 * second stock link on the side of its packager, to check that a group of links on one inventory counts with its best one.
 */
@GameTestHolder(StockLinkTests.MOD_ID)
@PrefixGameTestTemplate(false)
public class StockLinkTests {
	public static final String MOD_ID = "stock_link_priority_fix_gametest";

	private static final BlockPos CHEST_A = new BlockPos(0, 1, 0), PACKAGER_A = new BlockPos(1, 1, 0), LINK_A = new BlockPos(1, 2, 0), SIDE_LINK_A = new BlockPos(1, 1, 1);
	private static final BlockPos CHEST_B = new BlockPos(0, 1, 3), PACKAGER_B = new BlockPos(1, 1, 3), LINK_B = new BlockPos(1, 2, 3);
	private static final int TRIES = 12;

	private static UUID build(GameTestHelper helper, boolean sideLink) {
		UUID network = UUID.randomUUID();
		inventory(helper, network, CHEST_A, PACKAGER_A, LINK_A);
		inventory(helper, network, CHEST_B, PACKAGER_B, LINK_B);
		if (sideLink) {
			helper.setBlock(SIDE_LINK_A, AllBlocks.STOCK_LINK.getDefaultState()
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
			link(helper, SIDE_LINK_A).freqId = network;
		}
		return network;
	}

	private static void inventory(GameTestHelper helper, UUID network, BlockPos chestPos, BlockPos packager, BlockPos link) {
		helper.setBlock(chestPos, Blocks.CHEST);
		((Container) helper.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		helper.setBlock(packager, AllBlocks.PACKAGER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
		helper.setBlock(link, AllBlocks.STOCK_LINK.getDefaultState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		link(helper, link).freqId = network;
	}

	private static LogisticallyLinkedBehaviour link(GameTestHelper helper, BlockPos pos) {
		return ((SmartBlockEntity) helper.getBlockEntity(pos)).getBehaviour(LogisticallyLinkedBehaviour.TYPE);
	}

	private static PackageOrderWithCrafts order(int count) {
		return PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.COBBLESTONE), count)));
	}

	/** Which inventory pays for a 16-cobblestone order: "A", "B", "both" or "none". */
	private static String payer(GameTestHelper helper, UUID network) {
		Multimap<PackagerBlockEntity, PackagingRequest> requests = LogisticsManager.findPackagersForRequest(network, order(16), null, "test");
		boolean a = requests.containsKey(helper.getBlockEntity(PACKAGER_A)), b = requests.containsKey(helper.getBlockEntity(PACKAGER_B));
		return a && b ? "both" : a ? "A" : b ? "B" : "none";
	}

	private static int cobblestone(GameTestHelper helper, BlockPos chestPos) {
		Container chest = (Container) helper.getBlockEntity(chestPos);
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.COBBLESTONE)) n += chest.getItem(i).getCount();
		return n;
	}

	/** Orders with A deprioritised, then with B deprioritised; returns how many orders went to the wrong inventory. */
	private static int wrongPicks(GameTestHelper helper, UUID network) {
		int wrong = 0;
		for (int t = 0; t < TRIES; t++) {
			link(helper, LINK_A).redstonePowerChanged(8);
			link(helper, LINK_B).redstonePowerChanged(0);
			if (!payer(helper, network).equals("B")) wrong++;
			link(helper, LINK_A).redstonePowerChanged(0);
			link(helper, LINK_B).redstonePowerChanged(8);
			if (!payer(helper, network).equals("A")) wrong++;
		}
		return wrong;
	}

	@GameTest(template = "empty", timeoutTicks = 200, batch = "fixed")
	public static void orderComesFromPriorityLink(GameTestHelper helper) {
		StockLinkPriorityFix.enabled = true;
		UUID network = build(helper, false);
		helper.runAfterDelay(40, () -> {
			int wrong = wrongPicks(helper, network);
			System.out.printf("[stock_link_priority_fix] fix on: %d of %d orders from the lower-priority inventory%n", wrong, 2 * TRIES);
			helper.assertTrue(wrong == 0, wrong + " of " + 2 * TRIES + " orders came from the lower-priority inventory");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200, batch = "control")
	public static void controlReproducesBug(GameTestHelper helper) {
		StockLinkPriorityFix.enabled = false;
		UUID network = build(helper, false);
		helper.runAfterDelay(40, () -> {
			int wrong = wrongPicks(helper, network);
			StockLinkPriorityFix.enabled = true;
			System.out.printf("[stock_link_priority_fix] fix off: %d of %d orders from the lower-priority inventory%n", wrong, 2 * TRIES);
			// without the fix some orders must ignore the priority, or this test can't tell the fix works
			helper.assertTrue(wrong > 0, "every order followed the priority without the fix: the bug did not reproduce");
			helper.succeed();
		});
	}

	/** A has links at signal 0 and 12, B one at 6: A's best link wins, so A pays every time. */
	@GameTest(template = "empty", timeoutTicks = 200, batch = "group")
	public static void inventoryCountsWithItsBestLink(GameTestHelper helper) {
		StockLinkPriorityFix.enabled = true;
		UUID network = build(helper, true);
		helper.runAfterDelay(40, () -> {
			link(helper, LINK_A).redstonePowerChanged(0);
			link(helper, SIDE_LINK_A).redstonePowerChanged(12);
			link(helper, LINK_B).redstonePowerChanged(6);
			int wrong = 0;
			for (int t = 0; t < 2 * TRIES; t++) if (!payer(helper, network).equals("A")) wrong++;
			System.out.printf("[stock_link_priority_fix] two links on A: %d of %d orders from B%n", wrong, 2 * TRIES);
			helper.assertTrue(wrong == 0, wrong + " of " + 2 * TRIES + " orders skipped A although its best link has priority");
			helper.succeed();
		});
	}

	/** End to end: a real 16-cobblestone order with A preferred is taken from A's chest and leaves B's alone (Create would list B first). */
	@GameTest(template = "empty", timeoutTicks = 300, batch = "e2e")
	public static void realOrderTakesFromPriorityChest(GameTestHelper helper) {
		StockLinkPriorityFix.enabled = true;
		UUID network = build(helper, false);
		helper.runAfterDelay(40, () -> {
			link(helper, LINK_A).redstonePowerChanged(0);
			link(helper, LINK_B).redstonePowerChanged(8);
			helper.assertTrue(LogisticsManager.broadcastPackageRequest(network, RequestType.PLAYER, order(16), null, "test"), "order refused");
		});
		helper.runAfterDelay(100, () -> {
			int a = cobblestone(helper, CHEST_A), b = cobblestone(helper, CHEST_B);
			System.out.printf("[stock_link_priority_fix] after a 16 order: A %d, B %d%n", a, b);
			helper.assertTrue(a == 48 && b == 64, "expected A 48 / B 64, got A " + a + " / B " + b);
			helper.succeed();
		});
	}
}
