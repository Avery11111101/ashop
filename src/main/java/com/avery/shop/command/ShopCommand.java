package com.avery.shop.command;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.gui.GuiListener;
import com.avery.shop.gui.ShopGui;
import com.avery.shop.shop.ShopManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /shop 指令處理（多語系）
 */
public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopPlugin plugin;
    private final ShopManager shopManager;
    private final ItemCatalog catalog;
    private GuiListener guiListener;

    public ShopCommand(ShopPlugin plugin, ShopManager shopManager, ItemCatalog catalog) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.catalog = catalog;
    }

    public void setGuiListener(GuiListener guiListener) {
        this.guiListener = guiListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var locale = plugin.getLocaleService();

        if (args.length >= 1) {
            var sub = args[0].toLowerCase();
            if (sub.equals("help") || sub.equals("說明") || sub.equals("?")) {
                sendHelp(sender);
                return true;
            }
            if (sub.equals("reload") || sub.equals("重新載入")) {
                return handleReload(sender);
            }
            if (sub.equals("reset") || sub.equals("還原") || sub.equals("restore")) {
                return handleReset(sender);
            }
            if (sub.equals("resync-prices") || sub.equals("重算價格") || sub.equals("resync")) {
                return handleResyncPrices(sender);
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c" + locale.msg(locale.getDefaultLocale(), "msg.cmd.players-only"));
            return true;
        }

        if (!player.hasPermission("shop.use")) {
            player.sendMessage("§c" + locale.msg(player, "msg.cmd.no-permission"));
            return true;
        }

        if (args.length == 0) {
            openShop(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "search", "搜尋" -> {
                if (args.length < 2) {
                    player.sendMessage("§c" + locale.msg(player, "msg.cmd.usage.search"));
                    player.sendMessage("§e(英文或物品ID比較容易搜尋到，中文查不到不妨用物品ID)");
                    return true;
                }
                var query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                var session = guiListener.getOrCreateSession(player);
                session.setSearchQuery(query);
                session.setPage(0);
                ShopGui.openSearch(shopManager, player, session);
                var count = shopManager.usesCatalogBrowse()
                        ? shopManager.searchCatalog(player, query).size()
                        : shopManager.searchListings(player, query).size();
                player.sendMessage("§a" + locale.msg(player, "msg.search.found", query, count));
            }
            case "sell", "上架", "賣" -> {
                if (!player.hasPermission("shop.sell")) {
                    player.sendMessage("§c" + locale.msg(player, "msg.sell.no-permission"));
                    return true;
                }
                if (!shopManager.isSellToSystemEnabled()) {
                    player.sendMessage("§c" + locale.msg(player, "msg.sell.disabled"));
                    return true;
                }
                if (guiListener == null) {
                    player.sendMessage("§c" + locale.msg(player, "msg.cmd.gui-not-ready"));
                    return true;
                }
                var session = guiListener.getOrCreateSession(player);
                ShopGui.openSellToSystem(shopManager, player, session);
            }
            case "sellable", "sell-list", "list-sellable", "可收購" -> {
                if (!player.hasPermission("shop.sell")) {
                    player.sendMessage("§c" + locale.msg(player, "msg.sell.no-permission"));
                    return true;
                }
                if (!shopManager.isSellToSystemEnabled()) {
                    player.sendMessage("§c" + locale.msg(player, "msg.sell.disabled"));
                    return true;
                }
                if (guiListener == null) {
                    player.sendMessage("§c" + locale.msg(player, "msg.cmd.gui-not-ready"));
                    return true;
                }
                var session = guiListener.getOrCreateSession(player);
                ShopGui.openSellableCatalog(shopManager, player, session, 0);
            }
            case "price", "價格" -> {
                var hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    player.sendMessage("§c" + locale.msg(player, "msg.price.no-item"));
                    return true;
                }
                var canBuy = shopManager.canBuyFromSystem(hand);
                var canSell = shopManager.canSellToSystem(hand);
                var buyQuote = shopManager.getItemPriceQuote(hand);
                var sellQuote = shopManager.getSellToSystemQuote(hand);

                if (!canBuy && !canSell) {
                    player.sendMessage("§c" + locale.msg(player, "msg.price.not-in-shop"));
                    return true;
                }

                if (canBuy && buyQuote.available()) {
                    player.sendMessage("§a" + locale.msg(player, "msg.price.buy-result",
                            shopManager.getEconomy().format(buyQuote.price()),
                            buyQuote.formatTrend(locale, player)));
                } else if (canBuy) {
                    player.sendMessage("§c" + locale.msg(player, "msg.price.buy-unavailable"));
                }

                if (shopManager.isSellToSystemEnabled()) {
                    if (canSell && sellQuote.available()) {
                        player.sendMessage("§a" + locale.msg(player, "msg.price.sell-result",
                                shopManager.getEconomy().format(sellQuote.price()),
                                sellQuote.formatTrend(locale, player)));
                    } else if (canSell) {
                        player.sendMessage("§c" + locale.msg(player, "msg.price.sell-unavailable"));
                    } else {
                        player.sendMessage("§7" + locale.msg(player, "msg.price.sell-not-accepted"));
                    }
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        var locale = plugin.getLocaleService();
        var loc = sender instanceof Player player ? locale.getPlayerLocale(player) : locale.getDefaultLocale();

        sender.sendMessage("§6§l" + locale.msg(loc, "msg.cmd.help.title"));
        sender.sendMessage("§7" + locale.msg(loc, "msg.cmd.help.subtitle"));
        sender.sendMessage("");

        sender.sendMessage("§e§l" + locale.msg(loc, "msg.cmd.help.section.player"));
        sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.open"));
        sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.search"));
        sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.sell"));
        sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.price"));
        sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.lang"));
        sender.sendMessage("");

        sender.sendMessage("§a§l" + locale.msg(loc, "msg.cmd.help.section.gui"));
        for (var line : locale.msg(loc, "msg.cmd.help.gui").split("\n")) {
            sender.sendMessage("§7" + line);
        }
        sender.sendMessage("");

        if (sender.hasPermission("shop.admin")) {
            sender.sendMessage("§c§l" + locale.msg(loc, "msg.cmd.help.section.admin"));
            sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.reload"));
            sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.reset"));
            sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.resync-prices"));
            sender.sendMessage("§7" + locale.msg(loc, "msg.cmd.help.reset.warn"));
            sender.sendMessage("§f" + locale.msg(loc, "msg.cmd.help.admin-gui"));
            sender.sendMessage("");

            sender.sendMessage("§c§l" + locale.msg(loc, "msg.cmd.help.section.config"));
            for (var line : locale.msg(loc, "msg.cmd.help.config").split("\n")) {
                sender.sendMessage("§7" + line);
            }
        }

        sender.sendMessage("§8" + locale.msg(loc, "msg.cmd.help.footer"));
    }

    private boolean handleReload(CommandSender sender) {
        var locale = plugin.getLocaleService();
        if (!sender.hasPermission("shop.admin")) {
            sendError(sender, locale, "msg.cmd.no-admin");
            return true;
        }
        plugin.reloadConfig();
        plugin.getLocaleService().load();
        catalog.build();
        plugin.getShopConfigService().load(catalog);
        var seedResult = plugin.getShopConfigService().seedDefaultsIfEmpty(catalog);
        if (seedResult != null) {
            send(sender, locale, "msg.cmd.seed.success", seedResult.categories(), seedResult.items());
        }
        shopManager.load();
        send(sender, locale, "msg.cmd.reload.success");
        return true;
    }

    private boolean handleResyncPrices(CommandSender sender) {
        var locale = plugin.getLocaleService();
        if (!sender.hasPermission("shop.admin")) {
            sendError(sender, locale, "msg.cmd.no-admin");
            return true;
        }
        catalog.build();
        var count = plugin.getShopConfigService().resyncSurvivalPrices(catalog);
        shopManager.load();
        send(sender, locale, "msg.cmd.resync-prices.success", count);
        return true;
    }

    private boolean handleReset(CommandSender sender) {
        var locale = plugin.getLocaleService();
        if (!sender.hasPermission("shop.admin")) {
            sendError(sender, locale, "msg.cmd.no-admin");
            return true;
        }
        catalog.build();
        var result = plugin.getShopConfigService().restoreDefaults(catalog);
        shopManager.load();
        send(sender, locale, "msg.cmd.reset.success",
                result.categories(), result.items(), result.removedFolders());
        return true;
    }

    private void sendError(CommandSender sender, com.avery.shop.locale.LocaleService locale, String key) {
        if (sender instanceof Player player) {
            sender.sendMessage("§c" + locale.msg(player, key));
        } else {
            sender.sendMessage("§c" + locale.msg(locale.getDefaultLocale(), key));
        }
    }

    private void send(CommandSender sender, com.avery.shop.locale.LocaleService locale,
                      String key, Object... args) {
        if (sender instanceof Player player) {
            sender.sendMessage("§a" + locale.msg(player, key, args));
        } else {
            sender.sendMessage("§a" + locale.msg(locale.getDefaultLocale(), key, args));
        }
    }

    private void send(CommandSender sender, com.avery.shop.locale.LocaleService locale, String key) {
        send(sender, locale, key, new Object[0]);
    }

    private void openShop(Player player) {
        if (guiListener == null) {
            player.sendMessage("§c" + plugin.getLocaleService().msg(player, "msg.cmd.gui-not-ready"));
            return;
        }
        var session = guiListener.getOrCreateSession(player);
        ShopGui.openMain(shopManager, player, session);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            var options = new ArrayList<>(List.of("help", "search", "sell", "sellable", "price", "說明", "搜尋", "上架", "賣", "可收購", "價格"));
            if (sender.hasPermission("shop.admin")) {
                options.addAll(List.of("reload", "reset", "resync-prices", "重算價格", "還原", "restore", "重新載入"));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            return filter(List.of("diamond", "鑽石", "minecraft:stone"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        var result = new ArrayList<String>();
        for (var opt : options) {
            if (opt.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(opt);
            }
        }
        return result;
    }
}
