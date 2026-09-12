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

- **獨立基礎資源分類與收購管控** — 頂層獨立「礦產與原礦」、「原木木材」、「天然石材」、「農耕作物」、「生鮮肉品」等 5 大採集資源分類，預設開放系統收購與玩家買賣（TradeMode.BOTH）；其餘分類（採集工具、作戰武器、防禦護甲、熟食料理、鍊金藥水、魔法附魔書、紅石機關、加工建材等）預設為只賣不收（TradeMode.BUY_ONLY），防止伺服器經濟被非必要或衍生商品無限制收購沖垮
- **全商品統一分頁瀏覽** — 主選單（Slot 49，知識之書圖示）提供「全部商品」快捷頁面，一站式分頁查看全伺服器所有已啟用分類之商品，支援即時動態定價、趨勢漲跌、左鍵購買與管理員右鍵編輯
- **管理員自訂物品上架系統** — 支援 GUI 拖放工作台（分類列表與上層分類 Slot 47）與 `/shop add` 指令，自動判定最深層葉子子分類，上架後自動跳轉至目標頁面，完整保留自訂名稱、Lore、附魔與 NBT
- **防時運鎬套利定價體系** — 嚴密推導原礦與成品礦物價格比率，杜絕時運 III 刷錢漏洞
- **基岩版 (Bedrock) 觸控保護** — 自動辨識 Floodgate / Geyser 玩家，禁止開啟遊戲內 GUI 避免面板操作異常，引導至 Discord Bot 購買
- **Discord 線上商店預覽與購買** — 中文斜線指令 `/商店`、動態選單與 DiscordSRV 帳號繫結、背包空間防呆交割

- **Discord 每日/每週/每月營運報表** — 斜線指令 `/report` 查詢，Bot 頻道附帶永久按鈕與下拉選單（切換熱門商品 Top 10、活躍玩家榜與系統明細），支援定期自動推播與 Webhook
- **GitHub Releases 自動更新與設定檔無縫銜接** — 支援遊戲內指令 `/shop update` 非同步檢查與下載最新發布版本；支援伺服器開機自動檢查/下載（預設關閉，可於設定檔自訂開啟）；外掛升級時自動無痛銜接保留所有自訂設定並補全新項目與中文註解（附帶自動備份）
- **多語搜尋** — 物品 ID + 本地化名稱，支援自訂語系檔
- **遊戲內語言切換** — `/lang`，可在 config 新增任意語言
- **NBT 完整支援** — 附魔書、藥水、自訂 NBT 皆可交易
- **Vault 經濟** — 整合 Vault 及經濟插件

---

## 預設商店分類體系

ashop 預設提供完善的原版全物品分類體系。為了維持伺服器經濟平衡並防止通貨膨脹，系統將預設分類嚴格劃分為**「5 大基礎資源（預設開放收購）」**與**「12 大加工/產物分類（預設只賣不收）」**：

| 圖示 | 頂層分類 ID | 繁體中文名稱 | 預設交易模式 | 結構層級 | 包含核心範例與定位 |
|:---:|:---|:---|:---:|:---:|:---|
| ⛏️ | `minerals` | **礦產與原礦** | **可買可賣 (BOTH)** | 雙子分類 (Container) | 純挖掘產物（原礦石 vs 粗礦/天然寶石），防時運套利 |
| 🪵 | `logs` | **原木木材** | **可買可賣 (BOTH)** | 單層全品項 (Leaf) | 各樹種原生原木、去皮原木與菌柄（排除木板等加工品） |
| 🪨 | `stones` | **天然石材** | **可買可賣 (BOTH)** | 單層全品項 (Leaf) | 純石頭、深板岩、花崗岩、玄武岩等（排除階梯半磚石磚） |
| 🌾 | `crops` | **農耕作物** | **可買可賣 (BOTH)** | 單層全品項 (Leaf) | 小麥、馬鈴薯、胡蘿蔔、甘蔗、可可豆、南瓜、西瓜、各類種子 |
| 🥩 | `raw_meat` | **生鮮肉品** | **可買可賣 (BOTH)** | 單層全品項 (Leaf) | 生牛肉、生豬肉、生羊肉、生雞肉、生兔肉、生鱈魚、生鮭魚等 |
| 🧱 | `blocks` | **建築方塊** | **只賣不收 (BUY_ONLY)** | 巢狀多層 (Container) | 建築構件（木製品/石材變種/銅建材）、染色、自然、功能性方塊 |
| ⛏️ | `tools` | **採集工具** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 鎬、斧、鏟、鋤、工具雜項（剪刀/刷子/釣竿等） |
| ⚔️ | `weapons` | **作戰武器** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 劍、遠程武器（弓/弩）、特殊武器（三叉戟/重錘） |
| 🛡️ | `armor` | **防禦護甲** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 頭盔、胸甲、護腿、靴子、盾牌、鞘翅、馬鎧與狼鎧 |
| 🍲 | `food` | **熟食料理** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 熟食烹飪（熟牛排/熟豬排/麵包等）、點心與特色食品 |
| 🧪 | `potions` | **鍊金藥水** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 飲用藥水、噴濺藥水、滯留藥水各效果等級變體 |
| 📖 | `enchanted_books` | **魔法附魔書** | **只賣不收 (BUY_ONLY)** | 單層全品項 (Leaf) | 原版全附魔與全等級附魔書 |
| 🔴 | `redstone` | **紅石機關** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 紅石元件（線/火把/中繼器/比較器）、機械方塊（活塞/漏斗/發射器等） |
| 🛒 | `transport` | **交通載具** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 鐵軌、各式木船、礦車與鞍 |
| 🎨 | `decorations` | **裝飾物品** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 旗幟、蠟燭、花盆、物品展示框、盔甲架等 |
| 🥚 | `spawn_eggs` | **生物生怪蛋** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 被動、敵對與首領生怪蛋（預設受生存過濾器排除） |
| 📦 | `misc` | **雜項物資** | **只賣不收 (BUY_ONLY)** | 容器 (Container) | 金屬錠、皮革、羽毛、骨頭、合成材料與鍊金素材 |

> **提示**：管理員可隨時於 `plugins/ashop/shop/<分類>/items.yml` 自訂或修改各分類/商品的 `trade-mode`（`BOTH`、`BUY_ONLY` 或 `SELL_ONLY`）。

---

## 安裝

1. 將 `ashop-1.8.0-beta.5.jar` 放入 `plugins/` 資料夾
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
| `/ashop add <價格> [分類] [模式]` | 將手持自訂物品上架至商店（管理員） |
| `/ashop report <daily|weekly|monthly>` | 查詢商店營運報表，加上 `send` 可推播至 Discord（管理員） |
| `/ashop update [check|download]` | 檢查或下載 GitHub Releases 最新發行版本（管理員） |
| `/ashop reload` | 重新載入（管理員） |
| `/ashop reset` | 還原預設全物品商店（管理員） |
| `/lang <語言>` | 切換介面語言 |

**首次安裝**：若 `plugins/ashop/shop/` 尚無商品分類，插件會自動建立預設巢狀商店（僅含**生存可取得**物品，可在 `config.yml` 關閉 `shop.survival-only-defaults`）。

**別名：** `/商店` `/vs` `/language` `/語言`

---

## 設定

```yaml
# GitHub Releases 自動更新設定
updater:
  check-on-startup: false       # 伺服器開機時是否自動檢查更新（預設關閉）
  auto-download: false          # 發現新版本時是否自動下載至 plugins/update/（預設關閉，需重啟生效）
  notify-admin-on-join: true   # 管理員登入時是否提示新版本通知
  repo: "Avery11111101/ashop"   # GitHub 專案倉庫

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
