package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.ai.ResponseEngine;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler {

    /*
     * Log format from HolyWorld:
     * [20:13:50] [Render thread/INFO]: [System] [CHAT] \u00a7d\u00a7l[CHECK] \u00a7fAAAlpine14288 \u00a75-> za chto
     *
     * In-game the Text.getString() strips some formatting, so we need multiple patterns.
     */

    // Pattern 1: with color codes in getString()
    private static final Pattern CHECK_PATTERN_COLORED = Pattern.compile(
        "\\u00a7d\\u00a7l\\[CHECK\\]\\s*\\u00a7f(\\S+)\\s*\\u00a75->\\s*(.*)"
    );

    // Pattern 2: stripped color codes
    private static final Pattern CHECK_PATTERN_CLEAN = Pattern.compile(
        "\\[CHECK\\]\\s+(\\S+)\\s+->\\s+(.*)"
    );

    // Pattern 3: partial color codes
    private static final Pattern CHECK_PATTERN_PARTIAL = Pattern.compile(
        "\\[CHECK\\]\\s*[^\\w]*(\\S+)\\s*[^\\w]*->\\s*(.*)"
    );

    private final ResponseEngine responseEngine;
    private final ScheduledExecutorService scheduler;

    // Cooldown per player to avoid spam
    private final ConcurrentHashMap<String, Long> lastReplyTime = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2500;

    public ChatHandler() {
        this.responseEngine = new ResponseEngine();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HW-AutoReply");
            t.setDaemon(true);
            return t;
        });
        registerListener();
    }

    public ResponseEngine getResponseEngine() {
        return responseEngine;
    }

    private void registerListener() {
        // For 1.20.1 Fabric API
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!HolyWorldAutoReply.isEnabled()) return;
            if (overlay) return;

            try {
                String rawText = message.getString();
                processMessage(rawText);
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[AutoReply] Error processing message", e);
            }
        });
    }

    private void processMessage(String rawMessage) {
        if (rawMessage == null || !rawMessage.contains("[CHECK]")) {
            return;
        }

        String playerName = null;
        String playerMessage = null;

        // Try all patterns
        Matcher m = CHECK_PATTERN_COLORED.matcher(rawMessage);
        if (m.find()) {
            playerName = m.group(1);
            playerMessage = m.group(2);
        }

        if (playerName == null) {
            m = CHECK_PATTERN_CLEAN.matcher(rawMessage);
            if (m.find()) {
                playerName = m.group(1);
                playerMessage = m.group(2);
            }
        }

        if (playerName == null) {
            m = CHECK_PATTERN_PARTIAL.matcher(rawMessage);
            if (m.find()) {
                playerName = m.group(1);
                playerMessage = m.group(2);
            }
        }

        // Fallback: manual parsing
        if (playerName == null) {
            int checkIdx = rawMessage.indexOf("[CHECK]");
            String afterCheck = rawMessage.substring(checkIdx + 7);
            // Remove all section sign color codes
            afterCheck = stripColorCodes(afterCheck).trim();

            int arrowIdx = afterCheck.indexOf("->");
            if (arrowIdx > 0) {
                playerName = afterCheck.substring(0, arrowIdx).trim();
                playerMessage = afterCheck.substring(arrowIdx + 2).trim();
            }
        }

        if (playerName == null || playerMessage == null || playerMessage.trim().isEmpty()) {
            return;
        }

        // Clean up
        playerName = stripColorCodes(playerName).trim();
        playerMessage = stripColorCodes(playerMessage).trim();

        if (playerName.isEmpty() || playerMessage.isEmpty()) return;

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastTime = lastReplyTime.get(playerName);
        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) {
            return;
        }
        lastReplyTime.put(playerName, now);

        // Get response
        String response = responseEngine.getResponse(playerMessage, playerName);

        if (response != null && !response.isEmpty()) {
            final String finalResponse = response;
            final String finalPlayerName = playerName;

            // Random delay 0.8-2.0 seconds
            long delay = 800 + (long) (Math.random() * 1200);

            scheduler.schedule(() -> {
                sendReply(finalPlayerName, finalResponse);
            }, delay, TimeUnit.MILLISECONDS);
        } else {
            // null response = ban signal, log it
            HolyWorldAutoReply.LOGGER.warn("[AutoReply] BAN SIGNAL for {}: {}", playerName, playerMessage);
        }
    }

    private void sendReply(String playerName, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        client.execute(() -> {
            if (client.player != null && client.getNetworkHandler() != null) {
                // Use /r to reply to the player who messaged
                String command = "r " + message;
                client.getNetworkHandler().sendChatCommand(command);
                HolyWorldAutoReply.LOGGER.info("[AutoReply] Sent to {}: {}", playerName, message);
            }
        });
    }

    /**
     * Remove Minecraft color codes (section sign + character)
     */
    private static String stripColorCodes(String input) {
        if (input == null) return "";
        // Handle both real section signs and escaped ones
        return input.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "")
                     .replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}
