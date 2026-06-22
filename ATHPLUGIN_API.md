# ATHPlugin ContentProvider API

本文档记录 AndroidTestHelper 桌面端当前对 ATHPlugin 的全部调用契约。ATHPlugin 侧实现或调整接口时，以本文档和桌面端调用代码同步为准。

## 基础信息

- 插件包名：`com.floatingmuseum.android.test.helper.plugin`
- Provider authority：`com.floatingmuseum.android.test.helper.plugin.provider`
- 桌面端调用方式：通过本项目内置 `adb` 执行显式设备命令，所有命令都带 `adb -s <serial>`。
- 插件检测命令：

```bash
adb -s <serial> shell pm path com.floatingmuseum.android.test.helper.plugin
adb -s <serial> shell pm list packages -d com.floatingmuseum.android.test.helper.plugin
```

当 `pm path` 输出以 `package:` 开头时，桌面端认为 ATHPlugin 已安装；当 `pm list packages -d` 未返回该包名时，桌面端认为 ATHPlugin 已启用且 Provider 可用。若插件未安装、插件处于禁用状态，或任一插件接口调用失败，桌面端会回退到标准 ADB 解析路径。

## Content Query 返回格式

桌面端通过 `adb shell content query` 读取 JSON，当前解析逻辑会寻找第一行包含 `json_data=` 的内容，并取 `json_data=` 后面的字符串作为完整 JSON。

推荐 ATHPlugin 返回形态：

```text
Row: 0 json_data=<json>
```

约束：

- `json_data` 必须包含完整、未截断的 JSON。
- JSON 字段名使用 camelCase，必须与本文档字段名一致。
- 应用列表接口当前使用严格 JSON 解析，应用对象内不要返回未约定字段；新增字段前需要同步调整 AndroidTestHelper。
- 应用详情接口允许额外字段，但桌面端当前只消费 `items[].label` 和 `items[].value`。

## 接口一览

| 功能 | 调用方式 | URI |
| --- | --- | --- |
| 查询安装应用列表 | `content query` | `content://com.floatingmuseum.android.test.helper.plugin.provider/apps?isSystem=<true|false>` |
| 查询应用详情 | `content query` | `content://com.floatingmuseum.android.test.helper.plugin.provider/details/<packageName>?section=<section>` |
| 读取应用图标 | `content read` | `content://com.floatingmuseum.android.test.helper.plugin.provider/icon/<packageName>` |

## 1. 查询安装应用列表

作用：按系统应用或第三方应用分类，返回目标设备上已安装应用的基础元数据。桌面端会用该接口替代多条 `pm list packages`、`dumpsys package` 和 APK 拉取解析。

调用命令：

```bash
adb -s <serial> shell content query --uri "content://com.floatingmuseum.android.test.helper.plugin.provider/apps?isSystem=false"
adb -s <serial> shell content query --uri "content://com.floatingmuseum.android.test.helper.plugin.provider/apps?isSystem=true"
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `isSystem` | Boolean | 是 | `true` 返回系统应用，`false` 返回第三方应用。返回对象里的 `isSystem` 必须与筛选结果一致。 |

返回 JSON：`json_data` 中放置应用数组。

```json
[
  {
    "packageName": "com.example.app",
    "appName": "Example",
    "versionName": "1.2.3",
    "versionCode": 123,
    "compileSdkVersion": 35,
    "minSdkVersion": 23,
    "targetSdkVersion": 35,
    "isSystem": false,
    "isEnabled": true
  }
]
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `packageName` | String | 是 | 应用包名。 |
| `appName` | String | 是 | 应用展示名。取不到标签时建议返回包名。 |
| `versionName` | String | 是 | 版本名。取不到时返回 `"-"`，不要返回 `null`。 |
| `versionCode` | Long 或 null | 是 | 版本号。取不到时可返回 `null`。 |
| `compileSdkVersion` | Int 或 null | 否 | compile SDK 版本。取不到时可省略或返回 `null`。 |
| `minSdkVersion` | Int 或 null | 否 | min SDK 版本。取不到时可省略或返回 `null`。 |
| `targetSdkVersion` | Int 或 null | 否 | target SDK 版本。取不到时可省略或返回 `null`。 |
| `isSystem` | Boolean | 是 | 是否系统应用。 |
| `isEnabled` | Boolean | 是 | 当前应用是否启用。 |

桌面端行为：

- 应用列表加载完成后，桌面端会按 `appName.lowercase()`、`packageName` 排序。
- 图标不随列表返回。桌面端会按需调用图标接口，并按 `packageName + versionCode/versionName` 本地缓存。
- 若 ATHPlugin 已安装但处于禁用状态，桌面端不会调用该接口或图标接口，会直接走标准 ADB 模式。
- 该接口失败时，桌面端自动回退标准 ADB 模式。

## 2. 查询应用详情

作用：返回指定应用某一分类的详情项，用于应用模块右侧详情页。

调用命令：

```bash
adb -s <serial> shell content query --uri "content://com.floatingmuseum.android.test.helper.plugin.provider/details/com.example.app?section=basic"
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `packageName` | String | 是 | 应用包名。当前桌面端直接拼入 URI 路径，插件侧按包名解析。 |

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `section` | String | 是 | 详情分类。可选值见下表。 |

`section` 可选值：

| section | UI 标题 | 期望内容 |
| --- | --- | --- |
| `basic` | 基础 | 应用名、包名、版本、SDK、安装路径、dataDir、User 0 状态等基础信息。 |
| `permissions` | 权限 | Manifest 声明权限和安装态权限。 |
| `activities` | Activity | Manifest 中声明的 Activity 组件。 |
| `services` | Service | Manifest 中声明的 Service 组件。 |
| `receivers` | BroadcastReceiver | Manifest 中声明的 BroadcastReceiver 组件。 |
| `providers` | ContentProvider | Manifest 中声明的 ContentProvider 组件。 |
| `signatures` | 签名 | 应用签名、证书或 signing details 信息。 |

返回 JSON：`json_data` 中放置对象，顶层字段为 `items`。

```json
{
  "items": [
    {
      "label": "应用名",
      "value": "Example"
    },
    {
      "label": "包名",
      "value": "com.example.app"
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | Array | 否 | 详情项数组。省略时桌面端按空数组处理。 |
| `items[].label` | String | 是 | 详情项标题或组件名。 |
| `items[].value` | String | 是 | 详情项内容。 |

推荐格式：

- `basic`、`signatures`：`label` 作为左侧标题，`value` 作为右侧内容。
- `permissions`：建议用 `label` 区分来源，例如 `声明权限`、`安装态`；`value` 中放权限名和状态。桌面端会优先从 `value` 中识别 `name=<permissionName>`，否则按分隔符尝试提取权限名。
- `activities`、`services`、`receivers`、`providers`：建议 `label` 放组件名，`value` 放属性。属性建议用 ` · ` 或 ` | ` 分隔，例如：

```json
{
  "items": [
    {
      "label": "com.example.MainActivity",
      "value": "exported=true · enabled=true · permission=com.example.permission.START"
    },
    {
      "label": "com.example.SyncProvider",
      "value": "exported=false · enabled=true · authorities=com.example.provider"
    }
  ]
}
```

桌面端显示规则：

- 权限和组件分类支持搜索。
- 组件分类会尝试从 `value` 中解析 `name=<value>`；若没有该属性，则使用 `label` 作为组件名。
- `items` 为空时显示“该分类无可显示信息。”
- 若 ATHPlugin 已安装但处于禁用状态，桌面端不会调用该接口，会直接走 `dumpsys package` 加 APK Manifest 解析。
- 若插件调用失败，桌面端回退到 `dumpsys package` 加 APK Manifest 解析。

## 3. 读取应用图标

作用：按包名读取应用图标二进制，供桌面端应用列表和详情页显示。

调用命令：

```bash
adb -s <serial> exec-out content read --uri "content://com.floatingmuseum.android.test.helper.plugin.provider/icon/com.example.app"
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `packageName` | String | 是 | 应用包名。 |

返回数据：

- 直接返回图标原始二进制流，不包 JSON，不包 Base64。
- 推荐返回 PNG。Skia 能解码的常见图片编码也可用，但插件侧应避免返回 Android adaptive icon XML、VectorDrawable XML 或纯资源引用。
- 找不到图标时应让调用失败或返回空流。桌面端会显示包名首字母占位图标。

桌面端行为：

- 图标读取失败不影响应用列表显示。
- 图标会按应用版本缓存；同包名版本变化后会重新读取。
- 若 ATHPlugin 已安装但处于禁用状态，桌面端不会调用该接口，应用列表会直接走标准 ADB 模式下的 APK 图标解析。

## 非 ContentProvider 操作

以下能力当前不要求 ATHPlugin 通过 Provider 提供：

- 安装或更新 ATHPlugin APK：桌面端使用 `adb install -r -t`。
- 获取 ATHPlugin 安装版本：桌面端通过 `dumpsys package com.floatingmuseum.android.test.helper.plugin` 解析。
- 应用启动、强停、清除数据、启用、停用、卸载、导出 APK：桌面端直接使用标准 ADB 命令。

## 变更维护规则

- AndroidTestHelper 新增、删除或修改 ATHPlugin 调用时，必须同步更新本文档。
- ATHPlugin 侧新增返回字段前，先确认桌面端解析是否允许未知字段。应用列表目前不允许未知字段。
- 新增接口时至少补充：作用、完整 URI、调用命令、参数、返回 JSON 或二进制格式、失败后的桌面端行为。
