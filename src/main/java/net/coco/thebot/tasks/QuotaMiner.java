package net.coco.thebot.tasks;

import baritone.api.BaritoneAPI;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mine-until-N logic wrapper around Baritone.
 * Start with start(targetId, amount, player) and call tick(player) each client tick.
 */
public final class QuotaMiner {

    private QuotaMiner() {}

    // ---- Runtime state ----
    private static boolean ACTIVE = false;
    private static Item ITEM_TO_COUNT = null;
    private static String DISPLAY_TARGET = "";
    private static int GOAL = 0;
    private static int BASELINE = 0;
    private static long STARTED_MS = 0L;
    private static boolean JUST_COMPLETED = false;

    // Optional guardrails
    private static final long DEFAULT_TIMEOUT_MS = 8 * 60 * 1000L; // 8 minutes
    private static final long DEFAULT_STILL_COOLDOWN_MS = 1500L;
    private static long TIMEOUT_MS = DEFAULT_TIMEOUT_MS;

    // A few “smart drop” mappings for common ores when not silk-touching
    private static final Map<ResourceLocation, Item> ORE_DROP_MAP = new HashMap<>();
    static {
        // vanilla overworld
        ORE_DROP_MAP.put(rl("minecraft:coal_ore"), Items.COAL);
        ORE_DROP_MAP.put(rl("minecraft:deepslate_coal_ore"), Items.COAL);

        ORE_DROP_MAP.put(rl("minecraft:iron_ore"), Items.RAW_IRON);
        ORE_DROP_MAP.put(rl("minecraft:deepslate_iron_ore"), Items.RAW_IRON);

        ORE_DROP_MAP.put(rl("minecraft:copper_ore"), Items.RAW_COPPER);
        ORE_DROP_MAP.put(rl("minecraft:deepslate_copper_ore"), Items.RAW_COPPER);

        ORE_DROP_MAP.put(rl("minecraft:gold_ore"), Items.RAW_GOLD);
        ORE_DROP_MAP.put(rl("minecraft:deepslate_gold_ore"), Items.RAW_GOLD);

        ORE_DROP_MAP.put(rl("minecraft:redstone_ore"), Items.REDSTONE);
        ORE_DROP_MAP.put(rl("minecraft:deepslate_redstone_ore"), Items.REDSTONE);

        ORE_DROP_MAP.put(rl("minecraft:lapis_ore"), Items.LAPIS_LAZULI);
        ORE_DROP_MAP.put(rl("minecraft:deepslate_lapis_ore"), Items.LAPIS_LAZULI);

        ORE_DROP_MAP.put(rl("minecraft:diamond_ore"), Items.DIAMOND);
        ORE_DROP_MAP.put(rl("minecraft:deepslate_diamond_ore"), Items.DIAMOND);

        ORE_DROP_MAP.put(rl("minecraft:emerald_ore"), Items.EMERALD);
        ORE_DROP_MAP.put(rl("minecraft:deepslate_emerald_ore"), Items.EMERALD);

        // nether
        ORE_DROP_MAP.put(rl("minecraft:nether_quartz_ore"), Items.QUARTZ);
        ORE_DROP_MAP.put(rl("minecraft:nether_gold_ore"), Items.GOLD_NUGGET);

        // ancient debris -> scrap
        ORE_DROP_MAP.put(rl("minecraft:ancient_debris"), Items.NETHERITE_SCRAP);
    }

    /** Begin a quota mine. targetId can be a block id or item id (with or without namespace). */
    public static boolean start(String targetId, int amount, LocalPlayer player) {
        if (player == null || amount <= 0 || targetId == null || targetId.isBlank()) return false;

        // resolve what to count in inventory
        final Item countItem = resolveItemToCount(targetId);
        if (countItem == null) {
            System.out.println("[QuotaMiner] Could not resolve item to count for: " + targetId);
            return false;
        }

        // stop any previous run
        stopBaritone();

        ITEM_TO_COUNT = countItem;
        DISPLAY_TARGET = targetId;
        GOAL = amount;
        BASELINE = countInInventory(player, ITEM_TO_COUNT);
        STARTED_MS = System.currentTimeMillis();
        ACTIVE = true;

        // Kick off Baritone mining task (mine the *block* id the user gave)
        String normalizedTarget = normalizeId(targetId);
        runBaritone("mine " + normalizedTarget);

        System.out.println("[QuotaMiner] Started: target=" + normalizedTarget +
                " (counting " + ForgeRegistries.ITEMS.getKey(ITEM_TO_COUNT) + "), goal=" + GOAL +
                ", baseline=" + BASELINE);
        return true;
    }

    /** Call this from a client tick (END). Stops itself when the quota is met or on timeout. */
    public static void tick(LocalPlayer player) {
        if (!ACTIVE) return;
        if (player == null) { cancel("[QuotaMiner] Player null; cancel."); return; }

        // Timeout guard
        if (System.currentTimeMillis() - STARTED_MS > TIMEOUT_MS) {
            cancel("[QuotaMiner] Timeout exceeded; cancel.");
            return;
        }

        final int now = countInInventory(player, ITEM_TO_COUNT);
        final int gained = now - BASELINE;

        if (gained >= GOAL) {
            stopBaritone();
            ACTIVE = false;
            JUST_COMPLETED = true; // <-- add this
            System.out.println("[QuotaMiner] Complete: gained=" + gained + " of " + DISPLAY_TARGET);

        }
    }

    /** Is a quota run currently active? */
    public static boolean isActive() {
        return ACTIVE;
    }

    /** Force-cancel current run (does not clear baseline/history). */
    public static void cancel(String reason) {
        stopBaritone();
        ACTIVE = false;
        System.out.println(reason);
    }

    // ---- helpers ----

    private static void runBaritone(String cmd) {
        BaritoneAPI.getProvider()
                .getPrimaryBaritone()
                .getCommandManager()
                .execute(cmd);
    }

    private static void stopBaritone() {
        // primary cancel
        runBaritone("stop");
        // optional hard cancel:
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {}
    }

    /** Decide what to count in the inventory for a given target id (block or item). */
    private static Item resolveItemToCount(String targetId) {
        // If the user already gave an item id, just count that
        ResourceLocation asItemId = rl(normalizeId(targetId));
        Item directItem = ForgeRegistries.ITEMS.getValue(asItemId);
        if (directItem != null && directItem != Items.AIR) {
            return directItem;
        }

        // Otherwise treat as a block id, pick best “drop” to count
        ResourceLocation blockId = rl(normalizeId(targetId));
        // Special-case ore -> drop map
        Item mapped = ORE_DROP_MAP.get(blockId);
        if (mapped != null) return mapped;

        // Fallback: count the block's item form (e.g., logs, stone, etc.)
        Block block = ForgeRegistries.BLOCKS.getValue(blockId);
        if (block != null) {
            Item asBlockItem = block.asItem();
            if (asBlockItem != Items.AIR) return asBlockItem;
        }

        // No good mapping found
        return null;
    }

    private static int countInInventory(LocalPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack != null && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static String normalizeId(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        if (!s.contains(":")) s = "minecraft:" + s;
        return s;
    }

    private static ResourceLocation rl(String id) {
        return ResourceLocation.tryParse(id);
    }

    public static boolean justCompletedConsume() {
        if (JUST_COMPLETED) {
            JUST_COMPLETED = false;
            return true;
        }
        return false;
    }
}