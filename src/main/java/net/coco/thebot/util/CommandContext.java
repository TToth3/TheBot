package net.coco.thebot.util;

/**
 * Holds global context for the current bot command, such as who sent it.
 * This lets other classes (QuotaMiner, ReturnManager, etc.) access the sender name.
 */
public final class CommandContext {
    private static String lastSenderName = null;
    private static String lastCommand = null;

    private CommandContext() {}

    /** Record the most recent sender (e.g., in ClientChatRouter.onClientChat). */
    public static void setLastSender(String name) {
        lastSenderName = name;
    }

    /** Get the last recorded sender. */
    public static String getLastSender() {
        return lastSenderName;
    }

    /** Optionally track the last command string. */
    public static void setLastCommand(String cmd) {
        lastCommand = cmd;
    }

    public static String getLastCommand() {
        return lastCommand;
    }

    /** Clear everything (e.g., between actions). */
    public static void clear() {
        lastSenderName = null;
        lastCommand = null;
    }
}
