package net.coco.thebot.util;

import net.minecraft.client.Minecraft;

public final class ServerCommand {
    private ServerCommand() {}

    /** Execute a server command from the client, without posting plain chat. */
    public static void send(String rawCommandWithoutSlash) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.connection == null) return;
        try {
            // Preferred (1.19+ mappings): use command channel
            mc.player.connection.sendCommand(rawCommandWithoutSlash);
        } catch (Throwable ignored) {
            // Fallback: prefix '/' so server treats it as a command
            mc.player.connection.sendChat("/" + rawCommandWithoutSlash);
        }
    }
}
