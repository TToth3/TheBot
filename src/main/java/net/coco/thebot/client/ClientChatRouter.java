package net.coco.thebot.client;

import baritone.api.BaritoneAPI;
import net.coco.thebot.baritone.BaritoneFacade;
import net.coco.thebot.config.BotConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.coco.thebot.util.CommandContext;


@Mod.EventBusSubscriber(modid = "thebot", value = Dist.CLIENT)

public class ClientChatRouter {

    // Simple cooldown to avoid spam/loops
    private static long lastExecMs = 0;
    private static final long COOLDOWN_MS = 400; // adjust as you like

    private static String LAST_SENDER_NAME = null;
    private static String AWAITING_POS_FOR = null;

    private static void requestPlayerPosFromServer(String targetName) {
        AWAITING_POS_FOR = targetName; // remember who we’re asking for
        net.coco.thebot.util.ServerCommand.send("data get entity " + targetName + " Pos");
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        // Raw rendered text (server formatting may vary across modpacks)
        final String raw = event.getMessage().getString();
        // Typical vanilla chat looks like: "<Sender> message"

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:Pos\\s*:\\s*\\[|\\[)\\s*([-+]?[0-9]*\\.?[0-9]+)d?\\s*,\\s*([-+]?[0-9]*\\.?[0-9]+)d?\\s*,\\s*([-+]?[0-9]*\\.?[0-9]+)d?\\s*\\]");
        java.util.regex.Matcher m = p.matcher(raw);
        if (m.find()) {
            try {
                int ix = (int)Math.round(Double.parseDouble(m.group(1)));
                int iy = (int)Math.round(Double.parseDouble(m.group(2)));
                int iz = (int)Math.round(Double.parseDouble(m.group(3)));
                // We don't actually need to know WHO the server line was for, because we request
                // only for a specific player name in ReturnManager (requested name is tracked there).
                // But if you want stronger association, you can parse the player name out of the line too.
                // For many servers, the line includes the player name; otherwise we’ll rely on ReturnManager.
                if (AWAITING_POS_FOR != null) {
                    net.coco.thebot.util.PlayerPosCache.put(AWAITING_POS_FOR, new net.minecraft.core.BlockPos(ix, iy, iz));
                    System.out.println("[Router] Cached Pos for " + AWAITING_POS_FOR + ": " + ix + "," + iy + "," + iz);
                    AWAITING_POS_FOR = null;
                } else {
                    // Fallback: you can skip this or keep a generic cache if you parse name from raw
                    System.out.println("[Router] Found Pos triple but AWAITING_POS_FOR is null; not caching.");
                }
            } catch (NumberFormatException ignored) {}
        }


        // We’ll parse conservatively:
        String sender = extractSender(raw);
        String message = extractMessage(raw);
        LAST_SENDER_NAME = sender;
        CommandContext.setLastSender(LAST_SENDER_NAME);

        if (sender == null || message == null) return;

        // Ignore self
        final var self = Minecraft.getInstance().player;
        if (self != null && sender.equalsIgnoreCase(self.getGameProfile().getName())) return;

        // Security: only whitelisted users
        if (!BotConfig.ALLOWED_SENDERS.contains(sender.toLowerCase())) return;

        // Optional: require a prefix to avoid accidental triggers
        if (BotConfig.REQUIRE_PREFIX) {
            if (!message.startsWith(BotConfig.CMD_PREFIX)) return;
            message = message.substring(BotConfig.CMD_PREFIX.length()).trim();
        }


        // Throttle
        long now = System.currentTimeMillis();
        if (now - lastExecMs < COOLDOWN_MS) return;
        lastExecMs = now;

        // Dispatch the command
        dispatch(message);
    }

    private static void dispatch(String msg) {
        // Examples:
        // "!bot mine diamond_ore"
        // "!bot goto 123 64 -200"
        // "!bot follow MyMain"
        // "!bot stop"

        String[] parts = msg.split("\\s+");
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        switch (cmd) {
            case "mine":
                if (parts.length >= 2) {
                    String targetId = parts[1];
                    int amount = 0;
                    if (parts.length >= 3) {
                        try { amount = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                    }
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player == null) break;

                    if (amount > 0) {
                        if (LAST_SENDER_NAME != null && !LAST_SENDER_NAME.isBlank()) {
                            net.coco.thebot.tasks.ReturnManager.setReturnTo(LAST_SENDER_NAME);
                            requestPlayerPosFromServer(LAST_SENDER_NAME); // primes the cache
                        }
                        boolean ok = net.coco.thebot.tasks.QuotaMiner.start(targetId, amount, mc.player);
                        if (!ok) {
                            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                                    .getCommandManager().execute("mine " + targetId);
                        }
                    } else {
                        // Endless mine (no return)
                        baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                                .getCommandManager().execute("mine " + targetId);
                    }
                }
                break;
            case "goto":
                if (parts.length >= 4) {
                    try {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);
                        BaritoneFacade.gotoXYZ(x, y, z);
                    } catch (NumberFormatException ignored) {}
                }
                break;
            case "follow":
                if (parts.length >= 2) BaritoneFacade.follow(parts[1]);
                break;
            case "stop":
                BaritoneFacade.stop();
                break;
            default:
                // If you want: forward unknown commands to Baritone directly
                // BaritoneFacade.run(String.join(" ", parts));
                break;
        }
    }

    private static String joinFrom(String[] arr, int idx) {
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < arr.length; i++) {
            if (i > idx) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    // Very simple parser that handles "<Sender> message"
    private static String extractSender(String raw) {
        // Expecting "<Name> ..." — works on vanilla; some servers format differently.
        if (raw.startsWith("<")) {
            int close = raw.indexOf('>');
            if (close > 1) return raw.substring(1, close).trim();
        }
        // Fallback: you can add regex rules per server format here.
        return null;
    }

    private static String extractMessage(String raw) {
        if (raw.startsWith("<")) {
            int close = raw.indexOf('>');
            if (close > 0 && close + 1 < raw.length()) {
                return raw.substring(close + 1).trim();
            }
        }
        return null;
    }
}
