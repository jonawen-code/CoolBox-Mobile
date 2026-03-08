# CoolBox Mobile

CoolBox 是一款专为家用冰箱和家庭库存管理设计的智能 Android 应用程序。它支持语音输入、自动到期提醒，并具备强大的跨设备双向同步功能。

## 核心功能

- **智能语音录入**: 集成 NLP 技术，支持通过语音直接识别食物名称、存放位置、过期时间等信息。
- **双向行级同步**: 
  - 采用 **Row-Level Merge (行级合并)** 策略，解决多设备同时操作导致的冲突。
  - 支持 **软删除 (Soft Delete)**，确保删除操作在手机和平板间同步生效。
  - 基于 `lastModifiedMs` 时间戳自动保留最新版本的数据。
- **过期提醒**: 自动计算食物保质期，并在列表页面以醒目的方式展示到期状态。
- **灵活排序**: 支持按名称、过期时间进行升序或降序排列。
- **分类管理**: 预设多种分类，方便快速查找库存。

## 技术栈

- **语言**: Kotlin
- **架构**: MVVM (ViewModel, StateFlow)
- **数据库**: Room Persistence Library (SQLite)
- **网络**: OkHttp 3 / 4
- **语音识别**: 集成 Sherpa-ONNX 离线语音唤醒与识别 (待完善)
- **同步**: 与 Synology NAS (或其他支持 HTTP POST/GET 的后端) 进行双向数据库合并同步。

## 同步机制说明 (v1.0.29+)

本版本引入了全新的双向同步引擎：
1. **对账合并**: 刷新时下载云端数据库，逐条比对 `id` 和 `lastModifiedMs`。
2. **冲突处理**: 自动合并两端新增的记录，对于相同 ID 的记录，保留最新修改的版本。
3. **墓碑化删除**: 删除操作不再是物理删除，而是打上 `isDeleted` 标记，用于在同步时告知其他设备删除该项。
4. **稳定性修复**: 解决了移动端在同步过程中的 UI 冻结问题，确保 WAL 缓存及时刷入主库。

## 安装要求

- Android 5.0+ (Mobile 版)
- Android 4.1+ (Legacy/Tablet 版)

## 开发与运行

1. 克隆仓库: `git clone https://github.com/jonawen-code/CoolBox-Mobile.git`
2. 使用 Android Studio 打开项目。
3. 配置服务器地址: 在 App 设置页面填写您的 NAS 后端同步接口地址 (如 `http://your-ip:3000/coolbox`)。

---
*Note: 本项目目前处于快速开发阶段。*
