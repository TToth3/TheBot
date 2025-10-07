package net.coco.thebot.util;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerPosCache {
    private static final Map<String, BlockPos> POS = new ConcurrentHashMap<>();
    private static final Map<String, Long>  TS  = new ConcurrentHashMap<>();
    private static final long STALE_MS = 60_000; // 1 minute

    private PlayerPosCache() {}

    public static void put(String playerName, BlockPos pos) {
        if (playerName == null || pos == null) return;
        POS.put(playerName.toLowerCase(), pos.immutable());
        TS.put(playerName.toLowerCase(), System.currentTimeMillis());
    }

    public static Optional<BlockPos> getFresh(String playerName) {
        if (playerName == null) return Optional.empty();
        String k = playerName.toLowerCase();
        Long t = TS.get(k);
        if (t == null || (System.currentTimeMillis() - t) > STALE_MS) return Optional.empty();
        BlockPos p = POS.get(k);
        return Optional.ofNullable(p);
    }
}
