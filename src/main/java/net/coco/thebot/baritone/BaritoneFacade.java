package net.coco.thebot.baritone;

import baritone.api.BaritoneAPI;

public final class BaritoneFacade {
    private BaritoneFacade() {}

    public static void run(String baritoneCmd) {
        BaritoneAPI.getProvider()
                .getPrimaryBaritone()
                .getCommandManager()
                .execute(baritoneCmd);
    }

    // Common actions you’ll probably want:
    public static void mine(String target) { run("mine " + target); }
    public static void gotoXYZ(int x, int y, int z) { run("goto " + x + " " + y + " " + z); }
    public static void follow(String player) {
        if (player.equalsIgnoreCase("me")) {
            run("follow player cocopuffminer3");
        }
        else {
            run("follow player " + player);
        }
    }
    public static void stop() { run("stop"); }
}
