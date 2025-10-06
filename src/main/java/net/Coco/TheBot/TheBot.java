package com.Coco.TheBot;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@Mod("TheBot")
@EventBusSubscriber
public class TheBotMod {
    public BotMod() {
        System.out.println("Baritone Bot Mod loaded.");
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        if (message.startsWith("!mine")) {
            // Placeholder: Hook into Baritone mining logic
            System.out.println("Command received: " + message);
        }
    }
}
