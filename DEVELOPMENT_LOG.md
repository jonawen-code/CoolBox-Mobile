# Development Log: CoolBox-Mobile

## 2026-03-21 — V1.2-Pre41 (Build 137)

### 🚨 过期管理与安全提醒 (核心优化)
- **过期强制置顶**：重写 `MainViewModel` 排序逻辑，确保所有已过期食品（深红背景）无论用户选择何种排序方式，始终位列列表顶端。
- **实时弹窗提醒**：
  - 引入 `LifecycleEventObserver` 监听应用生命周期。
  - 确保每次从后台切回前台或冷启动时，只要存在过期/临期物品，均会自动弹出安全提醒。
  - 弹窗字体大幅度加粗（`Black`）并支持跟随系统字体缩放。
- **临期逻辑对齐**：统一采用“保质期最后 25%”或“3 天内”的双重判定标准。

### 🎨 UI 细节重塑与易读性增强
- **超黑体字重应用**：将 App 名称、食品名称、总量数字及弹窗关键文字全部更换为 **`FontWeight.Black`**，极大提升视觉辨识度。
- **配色微调**：过期背景色修正为 **`#A20000`**。
- **版本可视化**：在首页 TopBar左上角 App 名称旁以小字体标注当前版本号。
- **扁平化回归**：根据用户反馈移除了图标阴影，恢复简洁的扁平设计。

### 🔍 搜索功能扩展
- 搜索范围扩展至 **`remark` (备注)** 字段，支持更精细的库存查找。

### 📦 维护与发布
- 建立 `RELEASE_NOTES.md` 标准化发布文档。
- versionCode: 137，versionName: V1.2-Pre41

---

## 2026-03-20 — V1.2-Pre40 (Build 136)

### 🐛 核心修复：Room 数据库迁移错误
- **根本原因**：平板端 sync.db（schema v7）与手机端 Room 管理的数据库之间存在 identity hash 冲突，导致启动时抛出 `IllegalStateException: A migration from 7 to 9 was required but not found`
- **解决方案**：放弃物理文件覆盖方案，改用 **SQL ATTACH 注入**：
  - 将 NAS 下载的 sync.db 存入临时文件（`cacheDir/temp_sync.db`）
  - 通过 `ATTACH DATABASE` 挂载临时库，使用显式字段映射的 `INSERT INTO food_items SELECT ...` 完成跨库数据注入
  - Room 管理的主库文件始终不被替换，彻底避免 schema 版本冲突
  - `DETACH` 移至 `finally` 块，防止事务失败时死锁

### 📐 Schema 对齐（与 sync.db 完全一致）
- **重写 `FoodEntity.kt`**，字段从 11 个扩展至 **14 个**，与平板端 sync.db binary audit 完全对齐：
  - `note` → `remark`（与 NAS 字段名一致）
  - 新增 `inputDateMs`、`weightPerPortion`、`portions`
  - `isDeleted` 类型从 `Boolean` 改为 `Int`（匹配 SQLite INTEGER）
  - 字段顺序与 sync.db 列顺序对齐，支持 `SELECT *` 直注入
- **AppDatabase 版本升至 11**，配合 `fallbackToDestructiveMigration()` + `setJournalMode(TRUNCATE)` 防止 WAL 文件污染指纹

### 🔧 架构改进
- `FoodDao.getAllIncludeDeleted()` → `getAllVisibleItems()`，过滤逻辑下沉至 SQL 层（`WHERE isDeleted = 0`）
- `AppDatabase.deleteAndReset()`：新增自愈方法，加载失败时自动删库重建，确保 App 始终可启动
- `MainViewModel.loadLocalInventory()` 加入双重 catch：主路径失败 → 自动 deleteAndReset → 重试

### 🖼️ 图标修复
- 添加 `ic_food_lamb.png`（羊肉图标），补齐 drawable 库
- `MainActivity.kt`：`item.remark` 替换旧的 `item.note`，`icon` 字段改为非空处理

### 📦 依赖 & 构建
- KSP 替代 Kapt 进行 Room 注解处理（解决 JDK 17+ 模块化冲突）
- `gradle.properties` 添加 JVM `--add-exports` 参数（兼容 Kapt/KSP 混用场景）
- versionCode: 136，versionName: V1.2-Pre40

---

## 2026-03-15
- **Git Feature**: Successfully configured NAS private remote repository.
  - **URL**: `ssh://jonawen@192.168.31.94:22/volume1/Backup/git_repos/MyCoolBox.git`
  - **Status**: Remote added as `nas`.
- **CI/CD Feature**: Configured GitHub Actions automatic build flow.
  - **Path**: `.github/workflows/android_build.yml`
- **Logic Upgrade (V3.0.0-Pre26)**:
  - Refactored takeover flow into a strictly sequential coroutine-based chain.
  - Implemented `refreshConfig()` to ensure reactive UI updates across all storage changes.
  - Unified storage keys to `v2` and added `clearLegacyKeys` for data hygiene.
