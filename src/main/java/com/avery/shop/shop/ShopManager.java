package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.economy.EconomyService;
import com.avery.shop.pricing.DynamicPricingService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商店核心邏輯 - 上架、購買、瀏覽、動態定價
 */
public final class ShopManager {

    private final ShopPlugin plugin;
    private final ItemCatalog catalog;
    private final EconomyService economy;
    private final DynamicPricingService pricing;
    private final List<ShopListing> listings = new ArrayList<>();

    public ShopManager(ShopPlugin plugin, ItemCatalog catalog, EconomyService economy,
                       DynamicPricingService pricing) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.economy = economy;
        this.pricing = pricing;
    }

    public void load() {
        listings.clear();
        listings.addAll(ShopStorage.load(plugin));
        pricing.load();
        seedDefaultListings();
    }

    public void save() {
        ShopStorage.save(plugin, listings);
        pricing.save();
    }

    private void seedDefaultListings() {
        if (!plugin.getConfig().getBoolean("default-prices.enabled", true)) return;
        if (!listings.isEmpty()) return;

        double basePrice = plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        var systemId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var systemName = plugin.getLocaleService().msg(
                plugin.getLocaleService().getDefaultLocale(), "msg.system.shop-name");

        for (var entry : catalog.getAll()) {
            var item = entry.getTemplate();
            item.setAmount(1);
            listings.add(new ShopListing(
                    UUID.randomUUID(),
                    systemId,
                    systemName,
                    item,
                    basePrice,
                    System.currentTimeMillis()
            ));
        }
        save();
    }

    public double getEffectivePrice(ShopListing listing) {
        var key = pricing.resolveKey(listing.getItem());
        var stock = pricing.countStock(key, listings);
        return pricing.getEffectivePrice(listing, stock);
    }

    public com.avery.shop.pricing.PriceQuote getPriceQuote(ShopListing listing) {
        var key = pricing.resolveKey(listing.getItem());
        var stock = pricing.countStock(key, listings);
        return pricing.quoteForListing(listing, stock);
    }

    public double getSuggestedPrice(ItemStack item) {
        var key = pricing.resolveKey(item);
        var stock = pricing.countStock(key, listings);
        return pricing.getSuggestedPrice(item, stock);
    }

    public List<ShopListing> getAllListings() {
        return Collections.unmodifiableList(listings);
    }

    public List<ShopListing> getListingsByCategory(com.avery.shop.catalog.ItemCategory category) {
        return listings.stream()
                .filter(l -> {
                    var match = catalog.findMatching(l.getItem());
                    return match != null && match.getCategory() == category;
                })
                .collect(Collectors.toList());
    }

    public List<ShopListing> searchListings(org.bukkit.entity.Player player, String query) {
        var catalogResults = catalog.search(player, query);
        var keys = catalogResults.stream()
                .map(e -> e.getKey())
                .collect(Collectors.toSet());

        return listings.stream()
                .filter(l -> {
                    var match = catalog.findMatching(l.getItem());
                    if (match != null && keys.contains(match.getKey())) return true;
                    return l.getSellerName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
                })
                .collect(Collectors.toList());
    }

    public List<ShopListing> getPlayerListings(UUID playerId) {
        return listings.stream()
                .filter(l -> l.getSellerId().equals(playerId))
                .collect(Collectors.toList());
    }

    public Optional<ShopListing> getListing(UUID id) {
        return listings.stream().filter(l -> l.getId().equals(id)).findFirst();
    }

    public enum SellResult {
        SUCCESS, NO_ITEM, NO_ECONOMY, NO_PERMISSION, INVALID_PRICE
    }

    public SellResult sellItem(Player player, Double manualPrice) {
        if (!player.hasPermission("shop.sell")) return SellResult.NO_PERMISSION;

        var hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return SellResult.NO_ITEM;

        double price;
        if (manualPrice != null) {
            if (manualPrice <= 0) return SellResult.INVALID_PRICE;
            price = manualPrice;
        } else if (plugin.getConfig().getBoolean("dynamic-pricing.auto-suggest-price", true)) {
            price = getSuggestedPrice(hand);
        } else {
            return SellResult.INVALID_PRICE;
        }

        var listing = new ShopListing(
                UUID.randomUUID(),
                player.getUniqueId(),
                player.getName(),
                hand.clone(),
                price,
                System.currentTimeMillis()
        );

        listings.add(listing);
        var key = pricing.resolveKey(hand);
        pricing.recordSell(key);

        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        save();
        return SellResult.SUCCESS;
    }

    public enum BuyResult {
        SUCCESS, NOT_FOUND, NO_ECONOMY, NO_MONEY, NO_SPACE, OWN_ITEM, ECONOMY_DISABLED
    }

    public BuyResult buyListing(Player buyer, UUID listingId) {
        var opt = getListing(listingId);
        if (opt.isEmpty()) return BuyResult.NOT_FOUND;

        var listing = opt.get();
        if (listing.getSellerId().equals(buyer.getUniqueId())) return BuyResult.OWN_ITEM;

        if (!economy.isEnabled()) return BuyResult.ECONOMY_DISABLED;

        double price = getEffectivePrice(listing);
        if (!economy.has(buyer, price)) return BuyResult.NO_MONEY;

        var item = listing.getItem();
        var leftover = buyer.getInventory().addItem(item.clone());
        if (!leftover.isEmpty()) return BuyResult.NO_SPACE;

        if (!economy.withdraw(buyer, price)) return BuyResult.NO_MONEY;

        var catalogKey = pricing.resolveKey(listing.getItem());
        pricing.recordBuy(catalogKey);

        var systemId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (!listing.getSellerId().equals(systemId)) {
            var seller = plugin.getServer().getPlayer(listing.getSellerId());
            if (seller != null && seller.isOnline()) {
                economy.deposit(seller, price);
                var loc = plugin.getLocaleService();
                seller.sendMessage("§a" + loc.msg(seller, "msg.buy.seller-notify",
                        buyer.getName(), economy.format(price)));
            }
        }

        listings.remove(listing);
        save();
        return BuyResult.SUCCESS;
    }

    public boolean removeListing(Player player, UUID listingId) {
        var opt = getListing(listingId);
        if (opt.isEmpty()) return false;

        var listing = opt.get();
        if (!listing.getSellerId().equals(player.getUniqueId()) && !player.hasPermission("shop.admin")) {
            return false;
        }

        var leftover = player.getInventory().addItem(listing.getItem().clone());
        leftover.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));

        listings.remove(listing);
        save();
        return true;
    }

    public ItemCatalog getCatalog() {
        return catalog;
    }

    public EconomyService getEconomy() {
        return economy;
    }

    public DynamicPricingService getPricing() {
        return pricing;
    }

    public ShopPlugin getPlugin() {
        return plugin;
    }
}
