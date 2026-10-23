package com.avery.shop.storage;

import com.avery.shop.locale.LocaleService;
import com.avery.shop.util.AtomicFileUtil;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StorageAndReportTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Running StorageAndReportTest...");

        testAtomicSave();
        testLocaleMergeThreadSafety();

        System.out.println("==================================================");
        System.out.println("StorageAndReportTest ALL PASSED 100%!");
        System.out.println("==================================================");
    }

    private static void testAtomicSave() throws Exception {
        System.out.println("Testing AtomicFileUtil.saveYaml...");
        File tempDir = Files.createTempDirectory("atomic_test").toFile();
        File targetFile = new File(tempDir, "test-data.yml");
        File tmpFile = new File(tempDir, "test-data.yml.tmp");

        YamlConfiguration config = new YamlConfiguration();
        config.set("test.key", "value-12345");
        config.set("items.count", 42);

        AtomicFileUtil.saveYaml(config, targetFile);

        if (!targetFile.exists()) {
            throw new AssertionError("Target file should exist after atomic save!");
        }
        if (tmpFile.exists()) {
            throw new AssertionError("Temp file should not exist after successful move!");
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(targetFile);
        if (!"value-12345".equals(loaded.getString("test.key")) || loaded.getInt("items.count") != 42) {
            throw new AssertionError("Saved content mismatch!");
        }

        // Test overwrite with new content
        config.set("test.key", "updated-value");
        AtomicFileUtil.saveYaml(config, targetFile);
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(targetFile);
        if (!"updated-value".equals(reloaded.getString("test.key"))) {
            throw new AssertionError("Overwritten content mismatch!");
        }

        System.out.println("AtomicFileUtil.saveYaml passed!");
    }

    private static void testLocaleMergeThreadSafety() throws Exception {
        System.out.println("Testing LocaleService.mergeOfficialTranslations thread safety...");
        LocaleService localeService = new LocaleService(null);

        // Prepopulate with a custom key
        Map<String, String> initial = new HashMap<>();
        initial.put("item.minecraft.diamond", "我的鑽石（自訂）");
        localeService.mergeOfficialTranslations("zh_tw", initial);

        // Run multi-threaded merge
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            pool.submit(() -> {
                try {
                    Map<String, String> batch = new HashMap<>();
                    batch.put("item.minecraft.diamond", "官方鑽石"); // should NOT overwrite existing
                    batch.put("item.minecraft.gold_ingot_" + threadId, "金錠_" + threadId);
                    localeService.mergeOfficialTranslations("zh_tw", batch);
                } finally {
                    latch.countDown();
                }
            });
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timeout waiting for locale merge threads!");
        }
        pool.shutdown();

        // Check custom item was preserved
        String diamondName = localeService.getDisplayName("zh_tw", "diamond");
        if (!"我的鑽石（自訂）".equals(diamondName)) {
            throw new AssertionError("Custom translation was overridden by official merge! Got: " + diamondName);
        }

        for (int i = 0; i < threadCount; i++) {
            String gold = localeService.getDisplayName("zh_tw", "gold_ingot_" + i);
            if (!("金錠_" + i).equals(gold)) {
                throw new AssertionError("Missing merged key: gold_ingot_" + i);
            }
        }

        System.out.println("LocaleService thread-safe merge passed!");
    }
}
