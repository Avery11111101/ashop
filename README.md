<div align="right">

[![English](https://img.shields.io/badge/🌐-English-blue?style=for-the-badge)](README.en.md)

</div>

# ashop

> **動態經濟商店** — 價格隨供需即時浮動，越買越貴、越賣越便宜、物以稀為貴。

Paper **26.1.x ~ 26.2** 伺服器插件，以**玩家驅動的動態市場**為核心，搭配原版全物品目錄、多語搜尋與 NBT 交易。

---

## 動態經濟（核心）

ashop 不是固定標價的傳統商店。每一筆交易都會影響市場：

| 玩家行為 | 市場反應 |
|----------|----------|
| **購買** | 該物品漲價 ↑ |
| **賣給系統** | 該物品跌價 ↓ |
| **庫存稀少** | 額外漲價 ↑（物以稀為貴） |

價格依 `market-data.yml` 累積的買賣數據與即時庫存計算，GUI 即時顯示趨勢（`↑+25%`）。  
所有浮動幅度皆可在 `config.yml` 以**百分比**自由調整。

```yaml
dynamic-pricing:
  enabled: true
  base-price: 10.0
  per-buy-increase: 2.0              # 每買一次 +2%
  per-sell-decrease: 1.5             # 每賣給系統一次 -1.5%
  per-stock-shortage-increase: 3.0   # 庫存每少 1 件 +3%
  reference-stock: 5                 # 稀缺基準
  min-multiplier: 0.2                # 最低 20%
  max-multiplier: 5.0                # 最高 500%
```

用 `/shop price` 可隨時查詢手持物品的**系統售價與收購價**。

---

## 其他功能

- **原版全物品目錄** — 含藥水、附魔書等 NBT 變體
- **12 大分類瀏覽** — 方塊、工具、武器、護甲等
- **多語搜尋** — 物品 ID + 本地化名稱，支援自訂語系檔
- **遊戲內語言切換** — `/lang`，可在 config 新增任意語言
- **NBT 完整支援** — 附魔書、藥水、自訂 NBT 皆可交易
- **Vault 經濟** — 整合 Vault 及經濟插件

---

## 安裝

1. 將 `ashop-1.2.6.jar` 放入 `plugins/` 資料夾
2. 安裝 [Vault](https://www.spigotmc.org/resources/vault.34315/) 及經濟插件（如 EssentialsX）
3. 重啟伺服器

---

## 指令

| 指令 | 說明 |
|------|------|
| `/shop` | 開啟動態市場 GUI |
| `/shop price` | 查詢系統售價與收購價 |
| `/shop search <關鍵字>` | 搜尋物品 |
| `/shop sell` | 賣手持物品給系統 |
| `/shop reload` | 重新載入（管理員） |
| `/shop reset` | 還原預設全物品商店（管理員） |
| `/lang <代碼>` | 切換語言 |

**別名：** `/商店` `/vs` `/language` `/語言`

---

## 設定

```yaml
system-shop:
  enabled: true
  player-listings: false   # 禁止玩家上架
  sell-to-system: true     # 允許賣給系統
  sell-ratio: 0.5          # 收購價 = 購買價 × 50%
  require-listed-item: true

languages:
  default: zh_tw
  fallback: en_us
  locales:
    zh_tw: 繁體中文
    en_us: English
    ja_jp: 日本語          # 自訂語言：新增代碼 + 建立 locales/ja_jp.properties
```

- 語系檔：`plugins/ashop/locales/`（首次啟動自動釋出 `zh_tw`、`en_us`、`_template`）
- **商店分類**：完全由 `plugins/ashop/shop/<分類>/items.yml` 定義（可自由新增/刪除分類）
- 自訂語言：在 `locales` 加入代碼，複製 `_template.properties` 翻譯即可
- 完整設定：`plugins/ashop/config.yml`
- 市場統計：`plugins/ashop/market-data.yml`

---

## 編譯

```bash
./gradlew build
```

需要 **Java 25**。

---

## 更新日誌

詳見 [CHANGELOG.md](CHANGELOG.md)。
