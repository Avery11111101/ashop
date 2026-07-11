package com.avery.shop;

import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.command.LangCommand;
import com.avery.shop.command.ShopCommand;
import com.avery.shop.economy.EconomyService;
import com.avery.shop.gui.GuiListener;
import com.avery.shop.locale.LocaleService;
import com.avery.shop.pricing.DynamicPricingService;
import com.avery.shop.shop.ShopManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopPlugin extends JavaPlugin {

    private LocaleService localeService;
    private ItemCatalog itemCatalog;
    private ShopManager shopManager;
    private EconomyService economyService;
    private DynamicPricingService pricingService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        localeService = new LocaleService(this);
        localeService.load();

        itemCatalog = new ItemCatalog(this, localeService);
        itemCatalog.build();

        economyService = new EconomyService(this);
        economyService.setup();

        pricingService = new DynamicPricingService(this, itemCatalog);

        shopManager = new ShopManager(this, itemCatalog, economyService, pricingService);
        shopManager.load();

        var guiListener = new GuiListener(shopManager);
        getServer().getPluginManager().registerEvents(guiListener, this);

        var command = new ShopCommand(this, shopManager, itemCatalog);
        command.setGuiListener(guiListener);
        getCommand("shop").setExecutor(command);
        getCommand("shop").setTabCompleter(command);

        var langCommand = new LangCommand(this, localeService);
        getCommand("lang").setExecutor(langCommand);
        getCommand("lang").setTabCompleter(langCommand);

        getLogger().info("ashop 已啟用，目錄共 " + itemCatalog.size() + " 種物品");
    }

    @Override
    public void onDisable() {
        if (shopManager != null) {
            shopManager.save();
        }
        if (localeService != null) {
            localeService.savePlayerLocales();
        }
    }

    public LocaleService getLocaleService() {
        return localeService;
    }

    public ItemCatalog getItemCatalog() {
        return itemCatalog;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public DynamicPricingService getPricingService() {
        return pricingService;
    }
}
