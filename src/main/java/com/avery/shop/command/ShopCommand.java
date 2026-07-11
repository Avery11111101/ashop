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
        if (!(sender instanceof Player player)) {
            var locale = plugin.getLocaleService();
            sender.sendMessage("§c" + locale.msg(locale.getDefaultLocale(), "msg.cmd.players-only"));
            return true;
        }

        var locale = plugin.getLocaleService();
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
                var hand = player.getInventory().getItemInMainHand();
                var sellQuote = shopManager.getSellToSystemQuote(hand);
                var result = shopManager.sellToSystem(player);
                switch (result) {
                    case SUCCESS -> {
                        player.sendMessage("§a" + locale.msg(player, "msg.sell.success",
                                shopManager.getEconomy().format(sellQuote.price())));
                        if (plugin.getConfig().getBoolean("dynamic-pricing.enabled", true)) {
                            player.sendMessage("§7" + locale.msg(player, "msg.sell.price-hint"));
                        }
                    }
                    case NO_ITEM -> player.sendMessage("§c" + locale.msg(player, "msg.sell.no-item"));
                    case NOT_ACCEPTED -> player.sendMessage("§c" + locale.msg(player, "msg.sell.not-accepted"));
                    case NO_PERMISSION -> player.sendMessage("§c" + locale.msg(player, "msg.sell.no-permission"));
                    case DISABLED -> player.sendMessage("§c" + locale.msg(player, "msg.sell.disabled"));
                    default -> player.sendMessage("§c" + locale.msg(player, "msg.sell.failed"));
                }
            }
            case "price", "價格" -> {
                var hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    player.sendMessage("§c" + locale.msg(player, "msg.price.no-item"));
                    return true;
                }
                var buyQuote = shopManager.getItemPriceQuote(hand);
                var sellQuote = shopManager.getSellToSystemQuote(hand);
                player.sendMessage("§6" + locale.msg(player, "msg.price.buy-result",
                        shopManager.getEconomy().format(buyQuote.price()),
                        buyQuote.trendSymbol(),
                        String.format("%+.0f", buyQuote.changePercent())));
                if (shopManager.isSellToSystemEnabled()) {
                    player.sendMessage("§6" + locale.msg(player, "msg.price.sell-result",
                            shopManager.getEconomy().format(sellQuote.price())));
                }
            }
            case "reload" -> {
                if (!player.hasPermission("shop.admin")) {
                    player.sendMessage("§c" + locale.msg(player, "msg.cmd.no-admin"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getLocaleService().load();
                catalog.build();
                plugin.getShopConfigService().load(catalog);
                shopManager.load();
                player.sendMessage("§a" + locale.msg(player, "msg.cmd.reload.success"));
            }
            default -> {
                player.sendMessage("§e" + locale.msg(player, "msg.cmd.help.shop"));
                player.sendMessage("§e" + locale.msg(player, "msg.cmd.help.search"));
                player.sendMessage("§e" + locale.msg(player, "msg.cmd.help.sell"));
                player.sendMessage("§e" + locale.msg(player, "msg.cmd.help.price"));
            }
        }
        return true;
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
            return filter(List.of("search", "sell", "price", "reload"), args[0]);
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
