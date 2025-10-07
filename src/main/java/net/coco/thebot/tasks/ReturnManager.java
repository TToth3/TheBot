package net.coco.thebot.tasks;

import net.coco.thebot.util.PlayerPosCache;
import net.coco.thebot.util.ServerCommand;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public final class ReturnManager {
    private static String returnToName = null;

    private static long lastRequestMs = 0L;
    private static final long REQUERY_MS = 2000L;     // re-ask every 2s until we get coords
    private static final long GIVE_UP_MS = 60_000L;   // give up after 60s
    private static long firstRequestMs = 0L;

    private ReturnManager() {}

    public static void setReturnTo(String name) {
        returnToName = (name == null || name.isBlank()) ? null : name;
        lastRequestMs = 0L;
        firstRequestMs = 0L;
        System.out.println("[ReturnManager] Will return to: " + returnToName);
    }

    public static boolean hasTarget() { return returnToName != null; }

    /** Call this every tick. When coords are known, issues Baritone goto and clears target. */
    public static void tryReturnNow() {
        if (returnToName == null) return;

        // 1) Do we already have fresh coords cached?
        Optional<BlockPos> posOpt = PlayerPosCache.getFresh(returnToName);
        if (posOpt.isPresent()) {
            BlockPos p = posOpt.get();
            System.out.println("[ReturnManager] Returning to " + returnToName + " at " + p);
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                    .getCommandManager().execute("goto " + p.getX() + " " + p.getY() + " " + p.getZ());
            clear();
            return;
        }

        // 2) No coords yet → periodically request via server command
        long now = System.currentTimeMillis();
        if (firstRequestMs == 0L) firstRequestMs = now;

        if (now - firstRequestMs > GIVE_UP_MS) {
            System.out.println("[ReturnManager] Gave up requesting position for " + returnToName);
            clear();
            return;
        }

        if (now - lastRequestMs >= REQUERY_MS) {
            System.out.println("[ReturnManager] Requesting position for " + returnToName);
            ServerCommand.send("data get entity " + returnToName + " Pos");
            lastRequestMs = now;
        }
    }

    public static void clear() {
        returnToName = null;
        lastRequestMs = 0L;
        firstRequestMs = 0L;
    }
}
