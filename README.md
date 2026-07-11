<div align="right">

[![English](https://img.shields.io/badge/🌐-English-blue?style=for-the-badge)](README.en.md)

</div>

# ashop

Paper **26.1.x ~ 26.2** 原版全物品商店插件。

---

## 功能

- **原版全物品目錄**：含藥水、附魔書等 NBT 變體
- **12 大分類瀏覽**：方塊、工具、武器、護甲等
- **多語搜尋**：物品 ID + 本地化名稱（繁中 / 英文）
- **遊戲內語言切換**：`/lang zh_tw` / `/lang en_us`
- **NBT 完整支援**：附魔書、藥水、自訂 NBT 皆可上架購買
- **動態定價**：越買越貴、越賣越便宜、物以稀為貴
- **Vault 經濟**：整合 Vault 經濟插件（可選）

---

## 安裝

1. 將 `ashop-1.2.1.jar` 放入 `plugins/` 資料夾
2. 安裝 [Vault](https://www.spigotmc.org/resources/vault.34315/) 及經濟插件（如 EssentialsX）
3. 重啟伺服器

---

## 指令

| 指令 | 說明 |
|------|------|
| `/shop` | 開啟商店 GUI |
| `/shop search <關鍵字>` | 搜尋物品 |
| `/shop sell [價格]` | 上架手持物品（省略價格使用建議市價） |
| `/shop price` | 查詢手持物品的動態市價 |
| `/shop reload` | 重新載入（管理員） |
| `/lang` | 顯示可用語言 |
| `/lang <代碼>` | 切換語言 |
| `/lang list` | 同 `/lang` |

**別名：** `/商店` `/vs` `/language` `/語言`

**支援語言：** `zh_tw` `en_us`

---

## 設定

```yaml
languages:
  default: zh_tw
  available: [zh_tw, en_us]

dynamic-pricing:
  enabled: true
  base-price: 10.0
  per-buy-increase: 2.0          # 每買一次 +2%
  per-sell-decrease: 1.5         # 每上架一次 -1.5%
  per-stock-shortage-increase: 3.0  # 庫存每少 1 件 +3%
  reference-stock: 5             # 稀缺基準庫存
  min-multiplier: 0.2            # 最低價 20%
  max-multiplier: 5.0            # 最高價 500%
  system-shop: true
  player-listings: false
  auto-suggest-price: true

default-prices:
  enabled: true
  base-price: 10.0
```

- 語系檔：`plugins/ashop/locales/`（內建於 JAR）
- 玩家語言偏好：`plugins/ashop/player-locales.yml`
- 市場統計：`plugins/ashop/market-data.yml`

---

## 建置

```bash
./gradlew build
```

需要 **Java 25**。

---

## 更新日誌

詳見 [CHANGELOG.md](CHANGELOG.md)。
