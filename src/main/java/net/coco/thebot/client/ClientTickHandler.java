package net.coco.thebot.client;

import net.coco.thebot.tasks.QuotaMiner;   // <-- make sure this class exists per our plan
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Runs lightweight per-tick updates for the bot on the CLIENT.
 * Keep this lean: no blocking IO, no heavy loops.
 */
@Mod.EventBusSubscriber(modid = "thebot", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientTickHandler {

    private ClientTickHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent event) {
        // Only run logic once per tick at END to ensure game state is settled
        if (event.phase != TickEvent.Phase.END) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        try {
            net.coco.thebot.tasks.QuotaMiner.tick(mc.player);
        } catch (Throwable ignored) {
        }

        try {
            // If we *just* finished mining, immediately try to return (this also issues/reissues coord requests)
            if (net.coco.thebot.tasks.QuotaMiner.justCompletedConsume()
                    && net.coco.thebot.tasks.ReturnManager.hasTarget()) {
                System.out.println("[Tick] Mining complete; initiating return.");
                // Trigger a request now (will also re-query every 2s)
                net.coco.thebot.tasks.ReturnManager.tryReturnNow();
            } else {
                // Otherwise, keep trying in the background (handles late server replies)
                net.coco.thebot.tasks.ReturnManager.tryReturnNow();
            }
        } catch (Throwable ignored) {
        }
    }
}