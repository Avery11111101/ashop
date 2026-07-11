package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.ItemStackUtil;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 上架資料持久化
 */
public final class ShopStorage {

    private ShopStorage() {}

    public static List<ShopListing> load(ShopPlugin plugin) {
        var file = getFile(plugin);
        if (!file.exists()) return new ArrayList<>();

        var yaml = YamlConfiguration.loadConfiguration(file);
        var list = new ArrayList<ShopListing>();

        var section = yaml.getConfigurationSection("listings");
        if (section == null) return list;

        for (var key : section.getKeys(false)) {
            try {
                var id = UUID.fromString(key);
                var sellerId = UUID.fromString(section.getString(key + ".seller-id"));
                var sellerName = section.getString(key + ".seller-name", "Unknown");
                var itemData = section.getString(key + ".item");
                var price = section.getDouble(key + ".price");
                var createdAt = section.getLong(key + ".created-at");

                var item = ItemStackUtil.deserialize(itemData);
                if (item == null) continue;

                list.add(new ShopListing(id, sellerId, sellerName, item, price, createdAt));
            } catch (Exception e) {
                plugin.getLogger().warning("跳過無效上架資料：" + key);
            }
        }
        return list;
    }

    public static void save(ShopPlugin plugin, List<ShopListing> listings) {
        var file = getFile(plugin);
        var yaml = new YamlConfiguration();

        for (var listing : listings) {
            var path = "listings." + listing.getId();
            yaml.set(path + ".seller-id", listing.getSellerId().toString());
            yaml.set(path + ".seller-name", listing.getSellerName());
            yaml.set(path + ".item", listing.serializeItem());
            yaml.set(path + ".price", listing.getPrice());
            yaml.set(path + ".created-at", listing.getCreatedAt());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("儲存上架資料失敗：" + e.getMessage());
        }
    }

    private static File getFile(ShopPlugin plugin) {
        var name = plugin.getConfig().getString("storage.listings-file", "listings.yml");
        return new File(plugin.getDataFolder(), name);
    }
}
