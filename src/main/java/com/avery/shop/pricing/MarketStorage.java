package com.avery.shop.pricing;

import com.avery.shop.ShopPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 市場統計持久化
 */
public final class MarketStorage {

    private MarketStorage() {}

    public static Map<String, MarketData> load(ShopPlugin plugin) {
        var file = getFile(plugin);
        var result = new HashMap<String, MarketData>();
        if (!file.exists()) return result;

        var yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("items");
        if (section == null) return result;

        for (var key : section.getKeys(false)) {
            var buys = section.getInt(key + ".buys", 0);
            var sells = section.getInt(key + ".sells", 0);
            result.put(key, new MarketData(buys, sells));
        }
        return result;
    }

    public static void save(ShopPlugin plugin, Map<String, MarketData> market) {
        var file = getFile(plugin);
        var yaml = new YamlConfiguration();

        for (var entry : market.entrySet()) {
            var path = "items." + entry.getKey();
            yaml.set(path + ".buys", entry.getValue().getTotalBuys());
            yaml.set(path + ".sells", entry.getValue().getTotalSells());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("儲存市場資料失敗：" + e.getMessage());
        }
    }

    private static File getFile(ShopPlugin plugin) {
        var name = plugin.getConfig().getString("storage.market-file", "market-data.yml");
        return new File(plugin.getDataFolder(), name);
    }
}
