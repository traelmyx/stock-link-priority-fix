package io.github.traelmyx.stocklinkpriorityfix.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import io.github.traelmyx.stocklinkpriorityfix.StockLinkPriorityFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Mixin(value = LogisticsManager.class, remap = false)
public abstract class LogisticsManagerMixin {
	/** Lower redstone signal = higher priority, the order getAllPresent(freq, true) sorts in. */
	@Unique
	private static final Comparator<Object> stock_link_priority_fix$byPriority =
		Comparator.comparingInt(link -> ((LogisticallyLinkedBehaviour) link).redstonePower);

	// Inside one inventory's group of links, pick among the highest-priority ones instead of any of them.
	// require = 0: if a Create update changes this method, the fix stops working instead of crashing the game
	@WrapOperation(method = "findPackagersForRequest", require = 1,
		at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0))
	private static Object stock_link_priority_fix$pickInGroup(List<Object> group, int index, Operation<Object> original) {
		if (!StockLinkPriorityFix.enabled || group.size() < 2)
			return original.call(group, index);
		int best = Integer.MAX_VALUE;
		for (Object o : group) best = Math.min(best, ((LogisticallyLinkedBehaviour) o).redstonePower);
		List<Object> top = new ArrayList<>();
		for (Object o : group) if (((LogisticallyLinkedBehaviour) o).redstonePower == best) top.add(o);
		return top.get(ThreadLocalRandom.current().nextInt(top.size()));
	}

	// The picked links are about to be asked one by one (the loop over them is the method's second List.iterator() call);
	// put them back in priority order first. The sort is stable, so links with equal priority keep their order.
	@WrapOperation(method = "findPackagersForRequest", require = 1,
		at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 1))
	private static Iterator<Object> stock_link_priority_fix$sortByPriority(List<Object> availableLinks, Operation<Iterator<Object>> original) {
		if (StockLinkPriorityFix.enabled)
			availableLinks.sort(stock_link_priority_fix$byPriority);
		return original.call(availableLinks);
	}
}
