package net.coco.thebot.config;

import java.util.Set;

public final class BotConfig {
    // Keep lowercase to simplify matches
    public static final Set<String> ALLOWED_SENDERS = Set.of(
            "Cocopuffminer3".toLowerCase()
            // add more here
    );

    public static final String CMD_PREFIX = "!bot";  // e.g. "!bot mine diamond_ore"
    public static final boolean REQUIRE_PREFIX = true; // flip to false if you prefer DMs only

    private BotConfig() {}
}
