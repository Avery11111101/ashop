package com.avery.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 基岩版 (Bedrock / Geyser / Floodgate) 玩家識別工具類別
 */
public final class BedrockUtil {

    private BedrockUtil() {}

    /**
     * 檢測玩家是否為基岩版 (Bedrock) 玩家
     *
     * @param player Bukkit 玩家
     * @return 若為基岩版玩家傳回 true，否則傳回 false
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        // 1. 嘗試由 Floodgate API 判斷
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Method getInstanceMethod = floodgateApiClass.getMethod("getInstance");
                Object apiInstance = getInstanceMethod.invoke(null);
                if (apiInstance != null) {
                    Method isFloodgatePlayerMethod = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
                    Boolean result = (Boolean) isFloodgatePlayerMethod.invoke(apiInstance, uuid);
                    if (result != null && result) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 反射失敗或類別不存在時安全略過
        }

        // 2. 嘗試由 Geyser API 判斷
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot") ||
                Bukkit.getPluginManager().isPluginEnabled("Geyser")) {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Method apiMethod = geyserApiClass.getMethod("api");
                Object apiInstance = apiMethod.invoke(null);
                if (apiInstance != null) {
                    Method isBedrockPlayerMethod = geyserApiClass.getMethod("isBedrockPlayer", UUID.class);
                    Boolean result = (Boolean) isBedrockPlayerMethod.invoke(apiInstance, uuid);
                    if (result != null && result) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 反射失敗或類別不存在時安全略過
        }

        // 3. Floodgate 預設 UUID 前綴備援檢查 (00000000-0000-0000-0009-...)
        if (uuid.getMostSignificantBits() == 0L && (uuid.getLeastSignificantBits() >>> 32) == 9L) {
            return true;
        }

        // 4. Floodgate 預設玩家名稱前綴備援檢查 (例如 . 或 * 開頭)
        String name = player.getName();
        if (name != null && (name.startsWith(".") || name.startsWith("*"))) {
            return true;
        }

        return false;
    }
}
