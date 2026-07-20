package com.avery.shop.gui;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ChatPrompt {
    public static void start(Plugin plugin, Player player, String promptText, Consumer<String> onInput, Runnable onCancel) {
        ConversationFactory factory = new ConversationFactory(plugin)
                .withModality(true)
                .withLocalEcho(false)
                .withTimeout(60)
                .withFirstPrompt(new StringPrompt() {
                    @NotNull
                    @Override
                    public String getPromptText(@NotNull ConversationContext context) {
                        return promptText;
                    }

                    @Nullable
                    @Override
                    public Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
                        if (input == null) return END_OF_CONVERSATION;
                        String trimmed = input.trim();
                        if (trimmed.equalsIgnoreCase("cancel") || trimmed.equalsIgnoreCase("取消")) {
                            if (onCancel != null) {
                                plugin.getServer().getScheduler().runTask(plugin, onCancel);
                            }
                            return END_OF_CONVERSATION;
                        }
                        
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            onInput.accept(trimmed);
                        });
                        return END_OF_CONVERSATION;
                    }
                });
        factory.buildConversation(player).begin();
    }
}
