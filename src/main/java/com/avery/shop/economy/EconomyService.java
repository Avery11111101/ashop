package com.avery.shop.economy;

import com.avery.shop.ShopPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 經濟整合（可選）
 */
public final class EconomyService {

    private final ShopPlugin plugin;
    private Economy economy;
    private boolean enabled;

    public EconomyService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        enabled = plugin.getConfig().getBoolean("economy.enabled", true);
        if (!enabled) return;

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("未找到 Vault，經濟功能已停用");
            enabled = false;
            return;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("未找到 Economy 提供者，經濟功能已停用");
            enabled = false;
            return;
        }

        economy = rsp.getProvider();
        plugin.getLogger().info("已連接經濟系統：" + economy.getName());
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public String format(double amount) {
        if (!isEnabled()) return String.format("%.2f", amount);
        return economy.format(amount);
    }

    public double getBalance(Player player) {
        if (!isEnabled()) return 0;
        return economy.getBalance(player);
    }

    public boolean withdraw(Player player, double amount) {
        if (!isEnabled()) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        if (!isEnabled()) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(java.util.UUID playerId, double amount) {
        if (!isEnabled()) return false;
        return economy.depositPlayer(Bukkit.getOfflinePlayer(playerId), amount).transactionSuccess();
    }

    public boolean has(Player player, double amount) {
        if (!isEnabled()) return false;
        return economy.has(player, amount);
    }
}
