package com.avery.shop.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 檔案安全原子化儲存工具
 * <p>
 * 避免伺服器異常中斷或斷電時直接寫入原檔導致檔案損毀或被截斷為 0 位元組。
 * 先寫入 .tmp 暫存檔，再進行原子搬移覆蓋。
 */
public final class AtomicFileUtil {

    private AtomicFileUtil() {}

    /**
     * 安全原子化儲存 YAML 設定檔
     *
     * @param yaml       要儲存的 YamlConfiguration 物件
     * @param targetFile 目標檔案
     * @throws IOException 寫入或移動失敗時拋出
     */
    public static void saveYaml(YamlConfiguration yaml, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(parent != null ? parent : new File("."), targetFile.getName() + ".tmp");
        yaml.save(tempFile);

        moveSafely(tempFile, targetFile);
    }

    /**
     * 安全搬移暫存檔至目標檔（優先 ATOMIC_MOVE，失敗時 fallback 至 REPLACE_EXISTING）
     *
     * @param tempFile   來源暫存檔
     * @param targetFile 目標檔案
     * @throws IOException 移動失敗時拋出
     */
    public static void moveSafely(File tempFile, File targetFile) throws IOException {
        try {
            Files.move(tempFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 部分作業系統或檔案系統在目標已存在時 ATOMIC_MOVE 可能拋出非 AtomicMoveNotSupportedException 之 IOException
            try {
                Files.move(tempFile.toPath(), targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackEx) {
                if (tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
                throw fallbackEx;
            }
        }
    }
}
