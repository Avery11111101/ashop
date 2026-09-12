package com.avery.shop.update;

public class UpdateServiceTest {
    public static void main(String[] args) {
        System.out.println("Running UpdateServiceTest...");

        // 1. 同一核心版本：正式版比預發布版新
        assertTrue(UpdateService.isNewerVersion("1.8.0-beta.2", "1.8.0"));
        assertTrue(UpdateService.isNewerVersion("1.8.0-rc.1", "1.8.0"));
        assertFalse(UpdateService.isNewerVersion("1.8.0", "1.8.0-beta.2"));

        // 2. 預發布版序號比較
        assertTrue(UpdateService.isNewerVersion("1.8.0-beta.1", "1.8.0-beta.2"));
        assertFalse(UpdateService.isNewerVersion("1.8.0-beta.2", "1.8.0-beta.1"));

        // 3. 正常小版本遞增
        assertTrue(UpdateService.isNewerVersion("1.8.0", "1.8.1"));
        assertFalse(UpdateService.isNewerVersion("1.8.1", "1.8.0"));

        // 4. 次版本與大版本遞增
        assertTrue(UpdateService.isNewerVersion("1.8.0", "1.9.0"));
        assertTrue(UpdateService.isNewerVersion("1.8.0", "2.0.0"));
        assertFalse(UpdateService.isNewerVersion("2.0.0", "1.8.0"));

        // 5. v 前綴支援與相同版本
        assertFalse(UpdateService.isNewerVersion("1.8.0", "1.8.0"));
        assertFalse(UpdateService.isNewerVersion("v1.8.0", "1.8.0"));
        assertFalse(UpdateService.isNewerVersion("1.8.0", "v1.8.0"));
        assertTrue(UpdateService.isNewerVersion("1.8.0", "v1.8.1"));

        System.out.println("UpdateServiceTest passed 100%!");
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion failed: expected true, got false");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Assertion failed: expected false, got true");
        }
    }
}
