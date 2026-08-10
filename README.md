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

價格依 `market-data.yml` 累積的有效買賣次數與即時庫存計算，GUI 即時顯示趨勢（`↑+25%`）；觸及上下限時顯示「已達上限／下限」。  
所有浮動幅度皆可在 `config.yml` 以**百分比**自由調整。

```yaml
dynamic-pricing:
  enabled: true
  base-price: 10.0
  per-buy-increase: 2.0              # 每買一次 +2%
  per-sell-decrease: 2.0             # 每賣給系統一次 -2%（預設 1:1 恢復）
  per-stock-shortage-increase: 3.0   # 庫存每少 1 件 +3%
  reference-stock: 5                 # 稀缺基準
  min-multiplier: 0.2                # 最低 20%
  max-multiplier: 5.0                # 最高 500%

  # 基準值物價自動回歸機制（時間週期自動回調）
  auto-reversion:
    enabled: false                   # 預設關閉
    interval-minutes: 60             # 每 60 分鐘（1小時）執行一次
    increase-rate-percent: 1.0       # 物價低於基準價時每小時 +1.0% 回歸
    decrease-rate-percent: 1.0       # 物價高於基準價時每小時 -1.0% 回歸
```

漲停後的購買仍記錄於 `total-buys`，但不計入有效 `buys`；恢復原價只需賣出造成漲停的有效數量。

用 `/shop price` 可隨時查詢手持物品的**系統售價與收購價**（含趨勢）。

---

## 其他功能

- **原版全物品目錄** — 含藥水、附魔書等 NBT 變體
- **12 大分類瀏覽** — 方塊、工具、武器、護甲等
- **Discord 線上商店預覽與購買** — 中文斜線指令 `/商店`、動態選單與 DiscordSRV 帳號繫結、背包空間防呆交割
- **多語搜尋** — 物品 ID + 本地化名稱，支援自訂語系檔
- **遊戲內語言切換** — `/lang`，可在 config 新增任意語言
- **NBT 完整支援** — 附魔書、藥水、自訂 NBT 皆可交易
- **Vault 經濟** — 整合 Vault 及經濟插件

---

## 安裝

1. 將 `ashop-1.7.0.jar` 放入 `plugins/` 資料夾
2. 安裝 [Vault](https://www.spigotmc.org/resources/vault.34315/) 及經濟插件（如 EssentialsX）
3. 重啟伺服器

---

## 指令

| 指令 | 說明 |
|------|------|
| `/ashop` 或 `/shop` | 開啟動態市場 GUI |
| `/ashop help` | 查看完整指令與 GUI 教學 |
| `/ashop price` | 查詢系統售價與收購價 |
| `/ashop search <關鍵字>` | 搜尋物品 |
| `/ashop sell` | 開啟收購箱（角落支援一鍵填入與查看可收購商品） |
| `/ashop sellable` | 查看目前開放系統收購的所有商品清單 |
| `/ashop reload` | 重新載入（管理員） |
| `/ashop reset` | 還原預設全物品商店（管理員） |
| `/lang <語言>` | 切換介面語言 |

**首次安裝**：若 `plugins/ashop/shop/` 尚無商品分類，插件會自動建立預設巢狀商店（僅含**生存可取得**物品，可在 `config.yml` 關閉 `shop.survival-only-defaults`）。
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

shop:
  pricing:
    exchange:
      multiply: 2.0   # 基準價 ×2
      add: 30.0       # 再加 30（例：基準 10 → 實際 50）

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
gradle build
```

需要 **Java 21+**。

---

## 更新日誌

詳見 [CHANGELOG.md](CHANGELOG.md)。
