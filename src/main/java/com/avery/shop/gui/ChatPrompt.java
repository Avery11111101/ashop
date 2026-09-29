package com.avery.shop.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 聊天輸入監聽器 - 原生支援 Paper 26.3 AsyncChatEvent 與 Adventure Component，
 * 徹底替代已在 Paper 26.3 被移除/廢棄的舊版 Bukkit Conversation API。
 */
public final class ChatPrompt {

    private ChatPrompt() {}

    public static void start(Plugin plugin, Player player, String promptText, Consumer<String> onInput, Runnable onCancel) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (promptText != null && !promptText.isBlank()) {
            player.sendMessage(promptText);
        }

        final AtomicBoolean completed = new AtomicBoolean(false);

        class PromptListener implements Listener {
            BukkitTask timeoutTask;

            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    return;
                }
                if (!completed.compareAndSet(false, true)) {
                    return;
                }

                event.setCancelled(true);
                cleanup();

                String rawInput = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
                handleInput(rawInput);
            }

            void handleInput(String input) {
                if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("取消")) {
                    if (onCancel != null) {
                        Bukkit.getScheduler().runTask(plugin, onCancel);
                    }
                    return;
                }
                if (onInput != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> onInput.accept(input));
                }
            }

            void cleanup() {
                HandlerList.unregisterAll(this);
                if (timeoutTask != null) {
                    timeoutTask.cancel();
                }
            }
        }

        PromptListener listener = new PromptListener();
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // 60 秒超時自動清理
        listener.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (completed.compareAndSet(false, true)) {
                listener.cleanup();
                if (onCancel != null) {
                    onCancel.run();
                }
            }
        }, 20L * 60L);
    }
}
