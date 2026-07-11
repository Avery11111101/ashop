package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.catalog.ItemCategory;
import com.avery.shop.economy.EconomyService;
import com.avery.shop.pricing.DynamicPricingService;
import com.avery.shop.pricing.PriceQuote;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商店核心邏輯 - 系統商店買賣、瀏覽、動態定價
 */
public final class ShopManager {

    public static final UUID SYSTEM_SELLER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ShopPlugin plugin;
    private final ItemCatalog catalog;
    private final EconomyService economy;
    private final DynamicPricingService pricing;
    private final ShopConfigService shopConfig;
    private final ListingIndex index = new ListingIndex();
    private AsyncSaveService asyncSave;
    private final List<ShopListing> listings = new ArrayList<>();
    private final Object listingLock = new Object();

    public ShopManager(ShopPlugin plugin, ItemCatalog catalog, EconomyService economy,
                       DynamicPricingService pricing, ShopConfigService shopConfig) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.economy = economy;
        this.pricing = pricing;
        this.shopConfig = shopConfig;
    }

    public void bindAsyncSave(AsyncSaveService asyncSave) {
        this.asyncSave = asyncSave;
    }

    public void load() {
        synchronized (listingLock) {
            listings.clear();
            if (isPlayerListingsEnabled()) {
                var loaded = ShopStorage.load(plugin);
                if (usesCatalogBrowse()) {
                    int skipped = 0;
                    for (var listing : loaded) {
                        if (listing.getSellerId().equals(SYSTEM_SELLER_ID)) {
                            skipped++;
                            continue;
                        }
                        listings.add(listing);
                    }
                    if (skipped > 0) {
                        plugin.getLogger().info("目錄模式：略過 " + skipped
                                + " 筆系統上架，改為 shop 設定檔瀏覽");
                        markDirty();
                    }
                } else {
                    listings.addAll(loaded);
                    seedDefaultListings();
                }
            } else {
                var loaded = ShopStorage.load(plugin);
                int removed = loaded.size();
                if (removed > 0) {
                    plugin.getLogger().info("系統商店模式：已清除 " + removed + " 筆玩家上架資料");
                    markDirty();
                }
            }
        }
        pricing.load();
        rebuildIndex();
    }

    public boolean isSystemShopEnabled() {
        return plugin.getConfig().getBoolean("system-shop.enabled", true);
    }

    public boolean isPlayerListingsEnabled() {
        return plugin.getConfig().getBoolean("system-shop.player-listings", false);
    }

    public boolean isSellToSystemEnabled() {
        return plugin.getConfig().getBoolean("system-shop.sell-to-system", true);
    }

    public double getSellRatio() {
        return plugin.getConfig().getDouble("system-shop.sell-ratio", 0.5);
    }

    public boolean usesCatalogBrowse() {
        return isCatalogMode() || !isPlayerListingsEnabled();
    }

    public void save() {
        if (asyncSave != null) {
            asyncSave.flushSync();
        } else {
            synchronized (listingLock) {
                ShopStorage.save(plugin, listings);
                pricing.save();
            }
        }
    }

    private void markDirty() {
        if (asyncSave != null) {
            asyncSave.markDirty();
        } else {
            synchronized (listingLock) {
                ShopStorage.save(plugin, listings);
                pricing.save();
            }
        }
    }

    public boolean isCatalogMode() {
        return plugin.getConfig().getString("default-prices.mode", "catalog")
                .equalsIgnoreCase("catalog");
    }

    private void rebuildIndex() {
        synchronized (listingLock) {
            index.rebuild(listings, catalog, pricing);
        }
    }

    private void seedDefaultListings() {
        if (!plugin.getConfig().getBoolean("default-prices.enabled", true)) return;
        if (!listings.isEmpty()) return;

        double basePrice = plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        var systemName = plugin.getLocaleService().msg(
                plugin.getLocaleService().getDefaultLocale(), "msg.system.shop-name");

        for (var entry : catalog.getAll()) {
            var item = entry.getTemplate();
            item.setAmount(1);
            var listing = new ShopListing(
                    UUID.randomUUID(),
                    SYSTEM_SELLER_ID,
                    systemName,
                    item,
                    basePrice,
                    System.currentTimeMillis()
            );
            listings.add(listing);
            index.register(listing, catalog, pricing);
        }
        markDirty();
    }

    public int getCategoryDisplayCount(ItemCategory category) {
        if (usesCatalogBrowse()) {
            return shopConfig.getEnabledCount(category);
        }
        return index.getCategoryCount(category);
    }

    public List<CatalogEntry> getCatalogByCategory(ItemCategory category) {
        return shopConfig.getEnabledEntries(category);
    }

    public List<CatalogEntry> searchCatalog(org.bukkit.entity.Player player, String query) {
        return shopConfig.search(player, query, catalog);
    }

    public PriceQuote getCatalogPriceQuote(String catalogKey) {
        var entry = catalog.getByKey(catalogKey);
        var base = entry != null
                ? shopConfig.getBasePriceForEntry(entry)
                : plugin.getConfig().getDouble("dynamic-pricing.base-price",
                        plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        var stock = index.getStock(catalogKey);
        return pricing.quote(catalogKey, base, stock);
    }

    public double getEffectivePrice(ShopListing listing) {
        var key = index.getKey(listing.getId());
        if (key == null) key = pricing.resolveKey(listing.getItem());
        return pricing.getEffectivePrice(listing, index.getStock(key));
    }

    public PriceQuote getPriceQuote(ShopListing listing) {
        var key = index.getKey(listing.getId());
        if (key == null) key = pricing.resolveKey(listing.getItem());
        return pricing.quoteForListing(listing, index.getStock(key));
    }

    public double getSuggestedPrice(ItemStack item) {
        var key = pricing.resolveKey(item);
        return pricing.getSuggestedPrice(item, index.getStock(key));
    }

    public PriceQuote getItemPriceQuote(ItemStack item) {
        var key = pricing.resolveKey(item);
        var global = plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        var base = shopConfig.resolveBasePrice(catalog, key, global);
        return pricing.quote(key, base, index.getStock(key));
    }

    public List<ShopListing> getAllListings() {
        synchronized (listingLock) {
            return Collections.unmodifiableList(new ArrayList<>(listings));
        }
    }

    List<ShopListing> getListingsInternal() {
        return listings;
    }

    Object getListingLock() {
        return listingLock;
    }

    public List<ShopListing> getListingsByCategory(ItemCategory category) {
        synchronized (listingLock) {
            return listings.stream()
                    .filter(l -> {
                        var match = catalog.findMatching(l.getItem());
                        return match != null && match.getCategory() == category;
                    })
                    .collect(Collectors.toList());
        }
    }

    public List<ShopListing> searchListings(org.bukkit.entity.Player player, String query) {
        if (isCatalogMode()) return List.of();

        var catalogResults = catalog.search(player, query);
        var keys = catalogResults.stream().map(CatalogEntry::getKey).collect(Collectors.toSet());

        synchronized (listingLock) {
            return listings.stream()
                    .filter(l -> {
                        var match = catalog.findMatching(l.getItem());
                        if (match != null && keys.contains(match.getKey())) return true;
                        return l.getSellerName().toLowerCase(Locale.ROOT)
                                .contains(query.toLowerCase(Locale.ROOT));
                    })
                    .collect(Collectors.toList());
        }
    }

    public List<ShopListing> getPlayerListings(UUID playerId) {
        synchronized (listingLock) {
            return listings.stream()
                    .filter(l -> l.getSellerId().equals(playerId))
                    .collect(Collectors.toList());
        }
    }

    public Optional<ShopListing> getListing(UUID id) {
        synchronized (listingLock) {
            return listings.stream().filter(l -> l.getId().equals(id)).findFirst();
        }
    }

    public PriceQuote getSellToSystemQuote(ItemStack item) {
        var buyQuote = getItemPriceQuote(item);
        var sellPrice = Math.max(0.01, buyQuote.price() * getSellRatio());
        var sellChange = buyQuote.changePercent() * getSellRatio();
        return new PriceQuote(sellPrice, buyQuote.multiplier() * getSellRatio(), sellChange);
    }

    public enum SellToSystemResult {
        SUCCESS, NO_ITEM, NO_ECONOMY, NO_PERMISSION, NOT_ACCEPTED, ECONOMY_DISABLED, DISABLED
    }

    public SellToSystemResult sellToSystem(Player player) {
        if (!isSellToSystemEnabled()) return SellToSystemResult.DISABLED;
        if (!player.hasPermission("shop.sell")) return SellToSystemResult.NO_PERMISSION;
        if (!economy.isEnabled()) return SellToSystemResult.ECONOMY_DISABLED;

        var hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return SellToSystemResult.NO_ITEM;

        var entry = catalog.findMatching(hand);
        var requireListed = plugin.getConfig().getBoolean("system-shop.require-listed-item", true);
        if (requireListed && !shopConfig.isItemSellable(entry)) {
            return SellToSystemResult.NOT_ACCEPTED;
        }

        var key = entry != null ? entry.getKey() : pricing.resolveKey(hand);
        var sellPrice = getSellToSystemQuote(hand).price();
        var toSell = hand.clone();
        toSell.setAmount(1);

        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        if (!economy.deposit(player, sellPrice)) {
            var leftover = player.getInventory().addItem(toSell);
            leftover.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
            return SellToSystemResult.NO_ECONOMY;
        }

        pricing.recordSell(key);
        markDirty();
        return SellToSystemResult.SUCCESS;
    }

    public enum BuyResult {
        SUCCESS, NOT_FOUND, NO_ECONOMY, NO_MONEY, NO_SPACE, OWN_ITEM, ECONOMY_DISABLED
    }

    public BuyResult buyCatalogEntry(Player buyer, String catalogKey) {
        var entry = catalog.getByKey(catalogKey);
        if (entry == null) return BuyResult.NOT_FOUND;
        if (!shopConfig.isItemPurchasable(entry)) return BuyResult.NOT_FOUND;
        if (!economy.isEnabled()) return BuyResult.ECONOMY_DISABLED;

        var quote = getCatalogPriceQuote(catalogKey);
        double price = quote.price();
        if (!economy.has(buyer, price)) return BuyResult.NO_MONEY;

        var item = entry.getTemplate().clone();
        var leftover = buyer.getInventory().addItem(item);
        if (!leftover.isEmpty()) return BuyResult.NO_SPACE;

        if (!economy.withdraw(buyer, price)) return BuyResult.NO_MONEY;

        pricing.recordBuy(catalogKey);
        markDirty();
        return BuyResult.SUCCESS;
    }

    public BuyResult buyListing(Player buyer, UUID listingId) {
        ShopListing listing;
        synchronized (listingLock) {
            var opt = getListing(listingId);
            if (opt.isEmpty()) return BuyResult.NOT_FOUND;
            listing = opt.get();
        }

        if (listing.getSellerId().equals(buyer.getUniqueId())) return BuyResult.OWN_ITEM;
        if (!economy.isEnabled()) return BuyResult.ECONOMY_DISABLED;

        double price = getEffectivePrice(listing);
        if (!economy.has(buyer, price)) return BuyResult.NO_MONEY;

        var item = listing.getItem();
        var leftover = buyer.getInventory().addItem(item.clone());
        if (!leftover.isEmpty()) return BuyResult.NO_SPACE;

        if (!economy.withdraw(buyer, price)) return BuyResult.NO_MONEY;

        var catalogKey = index.getKey(listing.getId());
        if (catalogKey == null) catalogKey = pricing.resolveKey(listing.getItem());
        pricing.recordBuy(catalogKey);

        synchronized (listingLock) {
            listings.remove(listing);
            index.unregister(listing, catalog);
        }
        markDirty();

        if (!listing.getSellerId().equals(SYSTEM_SELLER_ID)) {
            var seller = plugin.getServer().getPlayer(listing.getSellerId());
            if (seller != null && seller.isOnline()) {
                economy.deposit(seller, price);
                var loc = plugin.getLocaleService();
                seller.sendMessage("§a" + loc.msg(seller, "msg.buy.seller-notify",
                        buyer.getName(), economy.format(price)));
            }
        }
        return BuyResult.SUCCESS;
    }

    public boolean removeListing(Player player, UUID listingId) {
        ShopListing listing;
        synchronized (listingLock) {
            var opt = getListing(listingId);
            if (opt.isEmpty()) return false;
            listing = opt.get();
            if (!listing.getSellerId().equals(player.getUniqueId()) && !player.hasPermission("shop.admin")) {
                return false;
            }
            listings.remove(listing);
            index.unregister(listing, catalog);
        }

        var leftover = player.getInventory().addItem(listing.getItem().clone());
        leftover.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));

        markDirty();
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

    public ShopConfigService getShopConfig() {
        return shopConfig;
    }

    public boolean isCategoryVisible(ItemCategory category) {
        return shopConfig.isCategoryEnabled(category);
    }
}
