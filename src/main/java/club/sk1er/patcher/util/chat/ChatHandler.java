/*
 * Copyright © 2020 by Sk1er LLC
 *
 * All rights reserved.
 *
 * Sk1er LLC
 * 444 S Fulton Ave
 * Mount Vernon, NY
 * sk1er.club
 */

package club.sk1er.patcher.util.chat;

import club.sk1er.mods.core.universal.ChatColor;
import club.sk1er.patcher.config.PatcherConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentStyle;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ChatHandler {

    private static final Map<Integer, ChatEntry> chatMessageMap = new HashMap<>();
    private static final Map<Integer, Set<ChatLine>> messagesForHash = new HashMap<>();
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static int currentMessageHash = -1;
    @SubscribeEvent
    public void renderChat(RenderGameOverlayEvent.Chat event) {
        if (event.type == RenderGameOverlayEvent.ElementType.CHAT && PatcherConfig.chatPosition) {
            event.posY -= 12;
        }
    }

    private int ticks;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (ticks++ >= 1200) {
            chatMessageMap.entrySet().removeIf(next -> {
                boolean oldEnough = next.getValue().lastSeenMessageMillis > 60000;
                if (oldEnough) messagesForHash.remove(next.getKey());
                return oldEnough;
            });
        }
    }

    @SubscribeEvent
    public void changeWorld(WorldEvent.Load event) {
        ticks = 0;
    }

    public static boolean setChatLineHead(IChatComponent chatComponent, boolean refresh) {
        if (Loader.isModLoaded("hychat")) {
            return true;
        }

        if (!refresh) {
            final String message = cleanColour(chatComponent.getFormattedText()).trim();
            if (message.isEmpty() && PatcherConfig.antiClearChat) {
                return false;
            }

            if (PatcherConfig.compactChat) {
                if (message.isEmpty() || message.startsWith("---------") || message.startsWith("=========") || message.startsWith("▬▬▬▬▬")) {
                    return true;
                }

                currentMessageHash = getChatComponentHash(chatComponent);
                final long currentTime = System.currentTimeMillis();

                if (!chatMessageMap.containsKey(currentMessageHash)) {
                    chatMessageMap.put(currentMessageHash, new ChatEntry(1, currentTime));
                } else {
                    final ChatEntry entry = chatMessageMap.get(currentMessageHash);
                    if (currentTime - entry.lastSeenMessageMillis > 60000) {
                        chatMessageMap.put(currentMessageHash, new ChatEntry(1, currentTime));
                    } else {
                        final boolean deleted = deleteMessageByHash(currentMessageHash);
                        if (!deleted) {
                            chatMessageMap.put(currentMessageHash, new ChatEntry(1, currentTime));
                        } else {
                            entry.messageCount++;
                            entry.lastSeenMessageMillis = currentTime;
                            chatComponent.appendText(ChatColor.GRAY + " (" + entry.messageCount + ")");
                        }
                    }
                }

                return true;
            }
        }

        return true;
    }

    public static void setChatLine_addToList(ChatLine line) {
        if (currentMessageHash != -1) {
            messagesForHash.computeIfAbsent(currentMessageHash, k -> new HashSet<>()).add(line);
        }
    }

    public static void setChatLineReturn() {
        currentMessageHash = -1;
    }

    private static boolean deleteMessageByHash(int hashCode) {
        if (!messagesForHash.containsKey(hashCode) || messagesForHash.get(hashCode).isEmpty()) {
            return false;
        }

        final int normalSearchLength = 100;
        final int wrappedSearchLength = 300;

        boolean removedMessage = false;
        final Set<ChatLine> toRemove = messagesForHash.get(hashCode);
        {
            final List<ChatLine> chatLines = mc.ingameGUI.getChatGUI().chatLines;
            int searched = 0;
            final Iterator<ChatLine> iterator = chatLines.iterator();
            while (iterator.hasNext() && searched < normalSearchLength) {
                final ChatLine next = iterator.next();
                if (toRemove.contains(next)) {
                    removedMessage = true;
                    iterator.remove();
                }

                searched++;
            }
        }

        final List<ChatLine> drawnChatLines = mc.ingameGUI.getChatGUI().drawnChatLines;
        int searched = 0;
        final Iterator<ChatLine> iterator = drawnChatLines.iterator();
        while (iterator.hasNext() && searched < wrappedSearchLength) {
            final ChatLine next = iterator.next();
            if (toRemove.contains(next)) {
                removedMessage = true;
                iterator.remove();
            }

            searched++;
        }

        messagesForHash.remove(hashCode);
        return removedMessage;
    }

    private static int getChatStyleHash(ChatStyle style) {
        final HoverEvent hoverEvent = style.getChatHoverEvent();
        HoverEvent.Action hoverAction = null;
        int hoverChatHash = 0;

        if (hoverEvent != null) {
            hoverAction = hoverEvent.getAction();
            hoverChatHash = getChatComponentHash(hoverEvent.getValue());
        }

        return Objects.hash(style.getColor(),
            style.getBold(),
            style.getItalic(),
            style.getUnderlined(),
            style.getStrikethrough(),
            style.getObfuscated(),
            hoverAction, hoverChatHash,
            style.getChatClickEvent(),
            style.getInsertion());
    }

    private static int getChatComponentHash(IChatComponent chatComponent) {
        final List<Integer> siblingHashes = new ArrayList<>();
        for (IChatComponent sibling : chatComponent.getSiblings()) {
            if (sibling instanceof ChatComponentStyle) {
                siblingHashes.add(getChatComponentHash(sibling));
            }
        }

        return Objects.hash(chatComponent.getUnformattedTextForChat(),
            siblingHashes,
            getChatStyleHash(chatComponent.getChatStyle()));
    }

    public static String cleanColour(String in) {
        return in.replaceAll("(?i)\\u00A7.", "");
    }

    static class ChatEntry {
        int messageCount;
        long lastSeenMessageMillis;

        ChatEntry(int messageCount, long lastSeenMessageMillis) {
            this.messageCount = messageCount;
            this.lastSeenMessageMillis = lastSeenMessageMillis;
        }
    }
}
