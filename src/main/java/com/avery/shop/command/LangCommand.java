package com.avery.shop.command;

import com.avery.shop.ShopPlugin;
import com.avery.shop.locale.LocaleService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /lang 語言切換指令
 */
public final class LangCommand implements CommandExecutor, TabCompleter {

    private final ShopPlugin plugin;
    private final LocaleService locale;

    public LangCommand(ShopPlugin plugin, LocaleService locale) {
        this.plugin = plugin;
        this.locale = locale;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c" + locale.msg(locale.getDefaultLocale(), "msg.cmd.players-only"));
            return true;
        }

        if (!player.hasPermission("shop.lang")) {
            player.sendMessage("§c" + locale.msg(player, "msg.cmd.no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            showLanguageList(player);
            return true;
        }

        var target = args[0].toLowerCase();
        if (!locale.isAvailable(target)) {
            player.sendMessage("§c" + locale.msg(player, "msg.lang.invalid", target));
            showLanguageList(player);
            return true;
        }

        locale.setPlayerLocale(player, target);
        player.sendMessage("§a" + locale.msg(player, "msg.lang.switched",
                locale.getLanguageDisplayName(target)));
        return true;
    }

    private void showLanguageList(Player player) {
        var current = locale.getPlayerLocale(player);
        player.sendMessage("§6" + locale.msg(player, "msg.lang.list-header"));
        for (var loc : locale.getAvailableLocales()) {
            var name = locale.getLanguageDisplayName(loc);
            var prefix = loc.equals(current) ? "§a▸ " : "§7  ";
            player.sendMessage(prefix + "§f" + loc + " §8- §f" + name);
        }
        player.sendMessage("§7" + locale.msg(player, "msg.lang.usage"));
        player.sendMessage("§8" + locale.msg(player, "msg.lang.file-hint"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(locale.getAvailableLocales(), args[0]);
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
