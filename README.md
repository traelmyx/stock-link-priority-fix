# Create: Stock Link Priority Fix

Fixes [Create issue #9689](https://github.com/Creators-of-Create/Create/issues/9689): stock links ignore their redstone priority, so orders come out of whatever inventory Create happens to list first.

A stock link with a weaker redstone signal (from an analog lever, for example) is supposed to be used before one with a stronger signal, and a signal of 15 switches it off. Since Create 6.0.7 an order first picks one link per inventory at random and then asks them in hash order, so the signal only works as an on/off switch. This mod puts the picked links back in priority order and, when one inventory has several links, picks among its highest-priority ones.

- Server side (or singleplayer). Requires Create 6.0 for Minecraft 1.21.1 on NeoForge.
- `config/stock_link_priority_fix.properties`: `enabled`.
- If a Create update changes the code this hooks into, the fix switches itself off and logs a warning; it never stops the game from loading.

Tests: two chests with 64 cobblestone each sit on one network, each with a packager and a stock link. One link gets signal 8 and the other 0, then the other way round, 12 times each, and every order must come from the chest with the weaker signal. The same run with the fix off must get some of them wrong, so the test can't pass by accident. A third test puts two links (signal 0 and 12) on one chest against one at 6 on the other, and a fourth sends a real order and checks which chest the cobblestone leaves. A client smoke test starts the game with Create, builds a packager and stock link and takes a screenshot.

Not affiliated with the Create team. MIT licensed.
