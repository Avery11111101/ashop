package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.catalog.ItemCategory;
import com.avery.shop.economy.EconomyService;
import com.avery.shop.gui.GuiListener;
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
    private GuiListener guiListener;
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

    public void bindGuiListener(GuiListener guiListener) {
        this.guiListener = guiListener;
    }

    public void resetPlayerGuiFlow(Player player) {
        if (guiListener != null) {
            guiListener.resetFlowState(player);
        }
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

        boolean survivalOnly = plugin.getConfig().getBoolean("shop.survival-only-defaults", true);

        for (var entry : catalog.getAll()) {
            if (survivalOnly && !com.avery.shop.catalog.SurvivalObtainability.isObtainableInSurvival(entry)) {
                continue;
            }
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

    public int getCategoryDisplayCount(String categoryId) {
        if (usesCatalogBrowse()) {
            return shopConfig.getEnabledCount(categoryId);
        }
        var legacy = com.avery.shop.catalog.ItemCategory.fromId(categoryId);
        return legacy != null ? index.getCategoryCount(legacy) : 0;
    }

    public List<CatalogEntry> getCatalogByCategory(String categoryId) {
        return shopConfig.getEnabledEntries(categoryId);
    }

    public List<CatalogEntry> searchCatalog(org.bukkit.entity.Player player, String query) {
        return shopConfig.search(player, query, catalog);
    }

    public PriceQuote getCatalogPriceQuote(String catalogKey) {
        var entry = catalog.getByKey(catalogKey);
        var base = entry != null
                ? shopConfig.applyServerPrice(shopConfig.getBasePriceForEntry(entry))
                : shopConfig.applyServerPrice(plugin.getConfig().getDouble("dynamic-pricing.base-price",
                        plugin.getConfig().getDouble("default-prices.base-price", 10.0)));
        return pricing.quote(catalogKey, base, quoteStock(catalogKey));
    }

    /** catalog 模式視為系統無限供應，避免 scarcity 虛高 */
    private int quoteStock(String catalogKey) {
        if (isCatalogMode()) {
            return plugin.getConfig().getInt("dynamic-pricing.reference-stock", 5);
        }
        return index.getStock(catalogKey);
    }

    public double getEffectivePrice(ShopListing listing) {
        var key = index.getKey(listing.getId());
        if (key == null) key = pricing.resolveKey(listing.getItem());
        return pricing.getEffectivePrice(listing, index.getStock(key));
    }

    public double getSuggestedPrice(ItemStack item) {
        var key = pricing.resolveKey(item);
        return pricing.getSuggestedPrice(item, index.getStock(key));
    }

    public PriceQuote getPriceQuote(ShopListing listing) {
        var key = index.getKey(listing.getId());
        if (key == null) key = pricing.resolveKey(listing.getItem());
        return pricing.quoteForListing(listing, index.getStock(key));
    }

    public PriceQuote getItemPriceQuote(ItemStack item) {
        var resolved = shopConfig.resolvePlayerItem(tradeItem(item), catalog);
        if (resolved.isEmpty()) {
            return PriceQuote.unavailable();
        }
        var key = resolved.get().entry().getKey();
        var base = shopConfig.applyServerPrice(resolved.get().setting().getPrice());
        return pricing.quote(key, base, quoteStock(key));
    }

    public Optional<ResolvedShopItem> resolvePlayerItem(ItemStack item) {
        return shopConfig.resolvePlayerItem(tradeItem(item), catalog);
    }

    /** 交易前移除收購箱 GUI 寫入的 lore，避免比對失敗 */
    private static ItemStack tradeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        return stripSellGuiLore(item.clone());
    }

    public boolean canSellToSystem(ItemStack item) {
        return shopConfig.canPlayerSell(tradeItem(item), catalog);
    }

    public boolean canBuyFromSystem(ItemStack item) {
        return shopConfig.canPlayerBuy(tradeItem(item), catalog);
    }

    public PriceQuote getSellToSystemQuote(ItemStack item) {
        var tradeItem = tradeItem(item);
        if (!shopConfig.canPlayerSell(tradeItem, catalog)) {
            return PriceQuote.unavailable();
        }
        var buyQuote = getItemPriceQuote(tradeItem);
        if (!buyQuote.available()) {
            return PriceQuote.unavailable();
        }
        var ratio = resolveSellRatio(tradeItem);
        var baseSellPrice = buyQuote.price() * ratio;
        var sellPrice = baseSellPrice;
        
        var meta = tradeItem.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int maxDurability = tradeItem.getType().getMaxDurability();
            if (maxDurability > 0 && damageable.getDamage() > 0) {
                double remainingDurability = Math.max(0, maxDurability - damageable.getDamage());
                double durabilityRatio = remainingDurability / maxDurability;
                sellPrice = (baseSellPrice / 2.0) + ((baseSellPrice / 2.0) * durabilityRatio);
            }
        }
        
        sellPrice = Math.max(0.01, sellPrice);
        var sellChange = buyQuote.changePercent() * ratio;
        return new PriceQuote(sellPrice, buyQuote.multiplier() * ratio, sellChange, buyQuote.cap());
    }

    private double resolveSellRatio(ItemStack item) {
        var resolved = shopConfig.resolvePlayerItem(item, catalog);
        if (resolved.isPresent()) {
            var shopItem = resolved.get();
            return shopConfig.resolveSellRatio(
                    shopItem.setting().getCatalogKey(),
                    shopItem.entry().getTemplate().getType());
        }
        return getSellRatio();
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

    public List<ShopListing> getListingsByCategory(String categoryId) {
        var enabledKeys = shopConfig.getEnabledEntries(categoryId).stream()
                .map(CatalogEntry::getKey)
                .collect(Collectors.toSet());

        synchronized (listingLock) {
            return listings.stream()
                    .filter(l -> {
                        var match = catalog.findMatching(l.getItem());
                        return match != null && enabledKeys.contains(match.getKey());
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

    public double calculateStackSellTotal(ItemStack stack) {
        if (!canSellToSystem(stack)) return 0;
        var quote = getSellToSystemQuote(stack);
        if (!quote.available()) return 0;
        return quote.price() * stack.getAmount();
    }

    public SellBatchResult sellDepositToSystem(Player player, org.bukkit.inventory.Inventory depositInv,
                                               int startSlot, int endSlot) {
        if (!isSellToSystemEnabled()) {
            return new SellBatchResult(0, 0, collectDepositItems(depositInv, startSlot, endSlot));
        }
        if (!player.hasPermission("shop.sell")) {
            return new SellBatchResult(0, 0, collectDepositItems(depositInv, startSlot, endSlot));
        }
        if (!economy.isEnabled()) {
            return new SellBatchResult(0, 0, collectDepositItems(depositInv, startSlot, endSlot));
        }

        double total = 0;
        int soldCount = 0;
        var rejected = new ArrayList<ItemStack>();
        var pendingSold = new ArrayList<ItemStack>();
        var sellCounts = new HashMap<String, Integer>();

        for (int slot = startSlot; slot <= endSlot; slot++) {
            var stack = depositInv.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;

            var tradeStack = tradeItem(stack);
            if (!canSellToSystem(tradeStack)) {
                rejected.add(tradeStack);
                depositInv.setItem(slot, null);
                continue;
            }

            var sellQuote = getSellToSystemQuote(stack);
            if (!sellQuote.available()) {
                rejected.add(tradeStack);
                depositInv.setItem(slot, null);
                continue;
            }

            var unitPrice = sellQuote.price();
            total += unitPrice * stack.getAmount();
            soldCount += stack.getAmount();
            var key = shopConfig.resolvePlayerItem(tradeStack, catalog)
                    .map(r -> r.entry().getKey())
                    .orElseGet(() -> pricing.resolveKey(tradeStack));
            sellCounts.merge(key, stack.getAmount(), Integer::sum);
            pendingSold.add(tradeStack);
            depositInv.setItem(slot, null);
        }

        if (total > 0 && !economy.deposit(player, total)) {
            rejected.addAll(pendingSold);
            return new SellBatchResult(0, 0, rejected);
        }

        for (var entry : sellCounts.entrySet()) {
            var stock = quoteStock(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                pricing.recordSell(entry.getKey(), stock);
            }
        }
        if (soldCount > 0) {
            markDirty();
            
            List<String> itemsList = new ArrayList<>();
            StringBuilder rawItems = new StringBuilder();
            for (var entry : sellCounts.entrySet()) {
                String catKey = entry.getKey();
                itemsList.add("**" + getChineseItemName(catKey) + "** x " + entry.getValue());
                rawItems.append(catKey).append(": ").append(entry.getValue()).append(", ");
            }
            
            plugin.getDiscordWebhookService().sendMessage(buildDiscordMessage(
                "🛍️ 收購箱大量販售",
                player.getName(), null, economy.format(total), null,
                itemsList,
                "Action: SellBox\nPlayer: " + player.getName() + "\nTotalEarned: " + total + "\nTotalItems: " + soldCount + "\nItems: {" + rawItems.toString() + "}"
            ));
        }

        return new SellBatchResult(total, soldCount, rejected);
    }

    public static ItemStack stripSellGuiLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        var meta = item.getItemMeta();
        var lore = meta.lore();
        if (lore == null || lore.isEmpty()) return item;

        var cleaned = new ArrayList<net.kyori.adventure.text.Component>();
        boolean skipping = false;
        for (var line : lore) {
            var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line);
            if (plain.contains("───")) {
                skipping = true;
                continue;
            }
            if (skipping) continue;
            cleaned.add(line);
        }
        if (cleaned.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(cleaned);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static List<ItemStack> collectDepositItems(org.bukkit.inventory.Inventory inv,
                                                       int startSlot, int endSlot) {
        var items = new ArrayList<ItemStack>();
        for (int slot = startSlot; slot <= endSlot; slot++) {
            var stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;
            items.add(stripSellGuiLore(stack.clone()));
            inv.setItem(slot, null);
        }
        return items;
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

        if (!canSellToSystem(hand)) {
            return SellToSystemResult.NOT_ACCEPTED;
        }

        var resolved = resolvePlayerItem(hand);
        var key = resolved.map(r -> r.entry().getKey())
                .orElseGet(() -> pricing.resolveKey(tradeItem(hand)));
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

        pricing.recordSell(key, quoteStock(key));
        markDirty();
            plugin.getDiscordWebhookService().sendMessage(buildDiscordMessage(
                "🛒 單件物品販售",
                player.getName(), null, economy.format(sellPrice), null,
                List.of("**" + getChineseItemName(key) + "** x 1"),
                "Action: SellSingle\nPlayer: " + player.getName() + "\nEarned: " + sellPrice + "\nItemKey: " + key
            ));
        return SellToSystemResult.SUCCESS;
    }

    public enum BuyResult {
        SUCCESS, NOT_FOUND, NO_ECONOMY, NO_MONEY, NO_SPACE, OWN_ITEM, ECONOMY_DISABLED
    }

    public BuyResult buyCatalogEntry(Player buyer, String catalogKey) {
        return buyCatalogEntry(buyer, catalogKey, 1);
    }

    public BuyResult buyCatalogEntry(Player buyer, String catalogKey, int amount) {
        if (amount < 1) return BuyResult.NOT_FOUND;

        var entry = catalog.getByKey(catalogKey);
        if (entry == null) return BuyResult.NOT_FOUND;
        if (!shopConfig.isItemPurchasable(entry)) return BuyResult.NOT_FOUND;
        if (!economy.isEnabled()) return BuyResult.ECONOMY_DISABLED;

        int maxBuy = plugin.getConfig().getInt("gui.max-buy-amount", 2304);
        amount = Math.min(amount, maxBuy);

        var quote = getCatalogPriceQuote(catalogKey);
        double unitPrice = quote.price();
        double totalPrice = unitPrice * amount;

        var deliveries = buildDeliveryStacks(entry.getTemplate(), amount);
        if (deliveries.isEmpty()) return BuyResult.NOT_FOUND;

        if (!economy.has(buyer, totalPrice)) return BuyResult.NO_MONEY;
        if (!com.avery.shop.util.InventorySpaceUtil.canFitStorage(buyer, deliveries)) {
            return BuyResult.NO_SPACE;
        }
        if (!economy.withdraw(buyer, totalPrice)) return BuyResult.NO_MONEY;

        for (var stack : deliveries) {
            buyer.getInventory().addItem(stack);
        }

        var stock = quoteStock(catalogKey);
        for (int i = 0; i < amount; i++) {
            pricing.recordBuy(catalogKey, stock);
        }
        markDirty();
        plugin.getDiscordWebhookService().sendMessage(buildDiscordMessage(
            "🛍️ 系統商店購買",
            buyer.getName(), null, null, economy.format(totalPrice),
            List.of("**" + getChineseItemName(catalogKey) + "** x " + amount),
            "Action: BuySystem\nPlayer: " + buyer.getName() + "\nCost: " + totalPrice + "\nAmount: " + amount + "\nItemKey: " + catalogKey
        ));
        return BuyResult.SUCCESS;
    }

    private static List<ItemStack> buildDeliveryStacks(ItemStack template, int amount) {
        var stacks = new ArrayList<ItemStack>();
        if (template == null || template.getType().isAir() || amount < 1) {
            return stacks;
        }

        int maxStack = template.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(maxStack, remaining);
            var stack = template.clone();
            stack.setAmount(size);
            stacks.add(stack);
            remaining -= size;
        }
        return stacks;
    }

    public int getMaxBuyAmount() {
        return plugin.getConfig().getInt("gui.max-buy-amount", 2304);
    }

    public BuyResult buyListing(Player buyer, UUID listingId) {
        synchronized (listingLock) {
            var opt = getListing(listingId);
            if (opt.isEmpty()) return BuyResult.NOT_FOUND;
            var listing = opt.get();

            if (listing.getSellerId().equals(buyer.getUniqueId())) return BuyResult.OWN_ITEM;
            if (!economy.isEnabled()) return BuyResult.ECONOMY_DISABLED;

            double price = getEffectivePrice(listing);
            if (!economy.has(buyer, price)) return BuyResult.NO_MONEY;
            if (!economy.withdraw(buyer, price)) return BuyResult.NO_MONEY;

            var item = listing.getItem();
            var leftover = buyer.getInventory().addItem(item.clone());
            if (!leftover.isEmpty()) {
                economy.deposit(buyer, price);
                return BuyResult.NO_SPACE;
            }

            var catalogKey = index.getKey(listing.getId());
            if (catalogKey == null) catalogKey = pricing.resolveKey(listing.getItem());

            listings.remove(listing);
            index.unregister(listing, catalog);
            markDirty();
            var stock = index.getStock(catalogKey);
            pricing.recordBuy(catalogKey, stock);

            plugin.getDiscordWebhookService().sendMessage(buildDiscordMessage(
                "🏪 玩家商店購買",
                buyer.getName(), listing.getSellerName(), null, economy.format(price),
                List.of("**" + getChineseItemName(catalogKey) + "** x " + listing.getItem().getAmount()),
                "Action: BuyPlayerListing\nBuyer: " + buyer.getName() + "\nSeller: " + listing.getSellerName() + "\nCost: " + price + "\nItemKey: " + catalogKey + "\nAmount: " + listing.getItem().getAmount()
            ));

            if (!listing.getSellerId().equals(SYSTEM_SELLER_ID)) {
                economy.deposit(listing.getSellerId(), price);
                var seller = plugin.getServer().getPlayer(listing.getSellerId());
                if (seller != null && seller.isOnline()) {
                    var loc = plugin.getLocaleService();
                    seller.sendMessage("§a" + loc.msg(seller, "msg.buy.seller-notify",
                            buyer.getName(), economy.format(price)));
                }
            }
            return BuyResult.SUCCESS;
        }
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

    public ShopAdminService getAdminService() {
        return plugin.getShopAdminService();
    }

    public boolean isCategoryVisible(String categoryId) {
        return shopConfig.isCategoryVisible(categoryId);
    }
    
    private String getChineseItemName(String catalogKey) {
        var entry = catalog.getByKey(catalogKey);
        String locale = "zh_tw";
        if (entry != null) {
            String base = plugin.getLocaleService().getDisplayName(locale, entry.getTemplate().getType());
            if (entry.getDisplayTag() != null) {
                return base + " " + plugin.getLocaleService().getVariantName(locale, entry.getDisplayTag());
            }
            return base;
        }
        return catalogKey;
    }

    private String buildDiscordMessage(String title, String player, String seller, String earned, String cost, List<String> items, String rawJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(title).append("**\n");
        if (player != null) {
            sb.append("> 👤 玩家: `").append(player).append("`\n");
        }
        if (seller != null) {
            sb.append("> 🏪 賣家: `").append(seller).append("`\n");
        }
        if (earned != null) {
            sb.append("> 💰 獲得: `").append(earned).append("`\n");
        }
        if (cost != null) {
            sb.append("> 💸 花費: `").append(cost).append("`\n");
        }
        if (items != null && !items.isEmpty()) {
            sb.append("\n**📋 物品明細:**\n");
            for (String item : items) {
                sb.append("• ").append(item).append("\n");
            }
        }
        sb.append("\n_ _\n");
        sb.append("`[原始資訊]`\n");
        sb.append("```yaml\n");
        sb.append(rawJson).append("\n");
        sb.append("```");
        return sb.toString();
    }
}
