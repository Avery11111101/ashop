package com.avery.shop.update;

import com.avery.shop.ShopPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * GitHub Releases 自動更新服務
 * 支援非同步檢查最新發布版本、語意化版本比對，以及將最新 .jar 下載至 plugins/update/ 目錄。
 */
public final class UpdateService {

    public record ReleaseInfo(
            String tagName,
            String name,
            String body,
            String htmlUrl,
            String downloadUrl,
            String assetName,
            long assetSize
    ) {}

    private final ShopPlugin plugin;
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private final AtomicBoolean isChecking = new AtomicBoolean(false);
    private volatile ReleaseInfo cachedLatestRelease = null;
    private volatile boolean updateAvailable = false;

    public UpdateService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasUpdate() {
        return updateAvailable && cachedLatestRelease != null;
    }

    public ReleaseInfo getCachedLatestRelease() {
        return cachedLatestRelease;
    }

    /**
     * 伺服器啟動時的初始化檢查
     */
    public void handleStartupCheck() {
        boolean checkOnStartup = plugin.getConfig().getBoolean("updater.check-on-startup", false);
        boolean autoDownload = plugin.getConfig().getBoolean("updater.auto-download", false);

        if (!checkOnStartup && !autoDownload) {
            return;
        }

        plugin.getLogger().info("正在檢查 GitHub Releases 最新版本...");
        checkForUpdates(false, null, release -> {
            if (release != null) {
                plugin.getLogger().info("發現新版本: " + release.tagName() + " (目前版本: v" + plugin.getDescription().getVersion() + ")");
                plugin.getLogger().info("發布頁面: " + release.htmlUrl());
                if (autoDownload) {
                    plugin.getLogger().info("已啟用 updater.auto-download，正在背景下載更新檔案...");
                    downloadUpdate(null, null);
                } else {
                    plugin.getLogger().info("可於遊戲中由管理員執行 /shop update download 進行更新。");
                }
            } else {
                plugin.getLogger().info("目前已是最新版本 (v" + plugin.getDescription().getVersion() + ")");
            }
        });
    }

    /**
     * 非同步檢查 GitHub Releases 最新版本
     */
    public void checkForUpdates(boolean notifyIfLatest, CommandSender feedbackSender, Consumer<ReleaseInfo> onComplete) {
        if (isChecking.getAndSet(true)) {
            if (feedbackSender != null) {
                feedbackSender.sendMessage("§e[ashop] 正在檢查更新中，請稍候...");
            }
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String repo = plugin.getConfig().getString("updater.repo", "Avery11111101/ashop").trim();
                String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";

                var url = URI.create(apiUrl).toURL();
                var conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "ashop-Plugin-Updater/" + plugin.getDescription().getVersion());
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    if (feedbackSender != null) {
                        feedbackSender.sendMessage("§c[ashop] 檢查更新失敗 (HTTP " + responseCode + ")");
                    }
                    if (onComplete != null) onComplete.accept(null);
                    return;
                }

                JsonObject json;
                try (var reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    json = JsonParser.parseReader(reader).getAsJsonObject();
                }

                String tagName = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
                String releaseName = json.has("name") && !json.get("name").isJsonNull() ? json.get("name").getAsString() : tagName;
                String body = json.has("body") && !json.get("body").isJsonNull() ? json.get("body").getAsString() : "";
                String htmlUrl = json.has("html_url") ? json.get("html_url").getAsString() : "";

                String downloadUrl = null;
                String assetName = null;
                long assetSize = 0;

                if (json.has("assets") && json.get("assets").isJsonArray()) {
                    JsonArray assets = json.getAsJsonArray("assets");
                    for (var elem : assets) {
                        if (elem.isJsonObject()) {
                            var assetObj = elem.getAsJsonObject();
                            String name = assetObj.get("name").getAsString();
                            if (name.endsWith(".jar")) {
                                downloadUrl = assetObj.get("browser_download_url").getAsString();
                                assetName = name;
                                assetSize = assetObj.get("size").getAsLong();
                                break;
                            }
                        }
                    }
                }

                String currentVersion = plugin.getDescription().getVersion();
                boolean newer = isNewerVersion(currentVersion, tagName);

                if (newer) {
                    var release = new ReleaseInfo(tagName, releaseName, body, htmlUrl, downloadUrl, assetName, assetSize);
                    this.cachedLatestRelease = release;
                    this.updateAvailable = true;

                    if (feedbackSender != null) {
                        feedbackSender.sendMessage("§6=================[ ashop 更新通知 ]=================");
                        feedbackSender.sendMessage("§e發現新版本: §a" + tagName + " §7(目前版本: v" + currentVersion + ")");
                        if (!releaseName.isEmpty() && !releaseName.equalsIgnoreCase(tagName)) {
                            feedbackSender.sendMessage("§7標題: §f" + releaseName);
                        }
                        feedbackSender.sendMessage("§7連結: §b" + htmlUrl);
                        if (downloadUrl != null) {
                            feedbackSender.sendMessage("§e請輸入指令 §a/shop update download §e以立即下載更新！");
                        } else {
                            feedbackSender.sendMessage("§c(此發布版本未包含 .jar 附檔，請至網頁手動下載)");
                        }
                        feedbackSender.sendMessage("§6=================================================");
                    }

                    if (onComplete != null) onComplete.accept(release);
                } else {
                    this.updateAvailable = false;
                    if (feedbackSender != null && notifyIfLatest) {
                        feedbackSender.sendMessage("§a[ashop] 目前已是最新版本 (v" + currentVersion + ")，無需更新。");
                    }
                    if (onComplete != null) onComplete.accept(null);
                }

            } catch (Exception e) {
                if (feedbackSender != null) {
                    feedbackSender.sendMessage("§c[ashop] 檢查更新時發生錯誤: " + e.getMessage());
                } else {
                    plugin.getLogger().warning("檢查更新失敗: " + e.getMessage());
                }
                if (onComplete != null) onComplete.accept(null);
            } finally {
                isChecking.set(false);
            }
        });
    }

    /**
     * 下載最新更新檔案至 plugins/update/ 目錄
     */
    public void downloadUpdate(CommandSender feedbackSender, Consumer<Boolean> onComplete) {
        if (cachedLatestRelease == null || cachedLatestRelease.downloadUrl() == null) {
            if (feedbackSender != null) {
                feedbackSender.sendMessage("§e[ashop] 正在確認最新版本資訊...");
            }
            checkForUpdates(false, feedbackSender, release -> {
                if (release != null && release.downloadUrl() != null) {
                    downloadUpdate(feedbackSender, onComplete);
                } else {
                    if (feedbackSender != null) {
                        feedbackSender.sendMessage("§c[ashop] 無法取得更新下載連結，請確認已發行包含 .jar 的 Release。");
                    }
                    if (onComplete != null) onComplete.accept(false);
                }
            });
            return;
        }

        if (isDownloading.getAndSet(true)) {
            if (feedbackSender != null) {
                feedbackSender.sendMessage("§c[ashop] 檔案正在下載中，請稍候...");
            }
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (feedbackSender != null) {
                    feedbackSender.sendMessage("§e[ashop] 開始下載最新版本 " + cachedLatestRelease.tagName() + "...");
                } else {
                    plugin.getLogger().info("開始下載最新版本 " + cachedLatestRelease.tagName() + "...");
                }

                File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
                if (!updateFolder.exists()) {
                    updateFolder.mkdirs();
                }

                String currentJarName = plugin.getPluginFile().getName();
                File targetFile = new File(updateFolder, currentJarName);
                File tempFile = new File(updateFolder, currentJarName + ".part");

                var url = URI.create(cachedLatestRelease.downloadUrl()).toURL();
                var conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "ashop-Plugin-Updater/" + plugin.getDescription().getVersion());
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                // 處理可能出現的 302 重定向 (GitHub Releases assets 會 302 到 AWS S3)
                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
                    conn.setRequestProperty("User-Agent", "ashop-Plugin-Updater/" + plugin.getDescription().getVersion());
                }

                try (var in = new BufferedInputStream(conn.getInputStream());
                     var out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }

                // 下載成功後進行原子覆蓋
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                String successMsg = "§a[ashop] 最新版本 " + cachedLatestRelease.tagName() + " 下載完成！\n"
                        + "§a[ashop] 檔案已存放在 plugins/update/" + currentJarName + "\n"
                        + "§6[ashop] 請於伺服器離峰時「重啟伺服器 (Restart)」，更新將自動完成套用！";

                if (feedbackSender != null) {
                    feedbackSender.sendMessage(successMsg);
                }
                plugin.getLogger().info("最新版本已成功下載至 " + targetFile.getAbsolutePath() + "，請重啟伺服器以套用。");

                if (onComplete != null) onComplete.accept(true);

            } catch (Exception e) {
                String errorMsg = "§c[ashop] 下載更新失敗: " + e.getMessage();
                if (feedbackSender != null) {
                    feedbackSender.sendMessage(errorMsg);
                }
                plugin.getLogger().severe("下載更新失敗: " + e.getMessage());
                if (onComplete != null) onComplete.accept(false);
            } finally {
                isDownloading.set(false);
            }
        });
    }

    /**
     * 語意化版本比對 (SemVer comparison)
     * 比較最新版本 tag 與目前外掛版本
     * @return true 若 latest 較新
     */
    public static boolean isNewerVersion(String currentStr, String latestStr) {
        if (currentStr == null || latestStr == null) return false;

        String c = currentStr.trim().toLowerCase();
        String l = latestStr.trim().toLowerCase();

        if (c.startsWith("v")) c = c.substring(1);
        if (l.startsWith("v")) l = l.substring(1);

        if (c.equalsIgnoreCase(l)) return false;

        String[] cParts = c.split("-", 2);
        String[] lParts = l.split("-", 2);

        String[] cCore = cParts[0].split("\\.");
        String[] lCore = lParts[0].split("\\.");

        int maxLen = Math.max(cCore.length, lCore.length);
        for (int i = 0; i < maxLen; i++) {
            int cNum = 0;
            int lNum = 0;
            if (i < cCore.length) {
                try { cNum = Integer.parseInt(cCore[i]); } catch (NumberFormatException ignored) {}
            }
            if (i < lCore.length) {
                try { lNum = Integer.parseInt(lCore[i]); } catch (NumberFormatException ignored) {}
            }
            if (lNum > cNum) return true;
            if (lNum < cNum) return false;
        }

        // 若核心版本號相同（例如 1.8.0 與 1.8.0-beta.2）
        // 正式發布版 (無預發布後綴) 比任何預發布版 (beta / rc) 都新
        boolean cHasPre = cParts.length > 1;
        boolean lHasPre = lParts.length > 1;

        if (cHasPre && !lHasPre) {
            return true; // 例如 current = 1.8.0-beta.2, latest = 1.8.0 -> latest 較新
        }
        if (!cHasPre && lHasPre) {
            return false; // 例如 current = 1.8.0, latest = 1.8.0-beta.3 -> current 已是正式版
        }

        if (cHasPre && lHasPre) {
            // 兩者皆有預發布標籤，進行字串比對
            return lParts[1].compareToIgnoreCase(cParts[1]) > 0;
        }

        return false;
    }
}
