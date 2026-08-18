# 真信盾 Android 实时推送通知配置指南

本补丁完成以下能力：

- Android 登录后把 Firebase Installation ID（FID）登记到真信盾后端；
- 新建家庭告警、确认告警、完成告警时发送 FCM 系统通知；
- Android 13 及以上申请通知权限；
- App 在前台、后台或被关闭后均可接收通知；
- 点击通知直接进入对应的家庭告警详情；
- 退出账户时停用当前设备；
- 修改密码或找回密码时停用该账户的全部旧推送设备；
- Firebase 返回设备已失效时，后端自动把该 FID 标为 revoked。

> 本补丁不包含 `google-services.json` 或 Firebase 服务账号私钥。它们必须由你在自己的 Firebase 项目中生成，不能使用他人的配置文件。

---

## 一、在 Firebase Console 中创建 Android 应用

1. 打开 Firebase Console，创建一个 Firebase 项目，例如 `true-shield`。
2. 在项目概览中选择“添加应用”→“Android”。
3. Android 软件包名称必须填写：

```text
com.trueshield.app
```

4. 下载 `google-services.json`。
5. 将文件放到以下精确位置：

```text
D:\Projects\true-shield\apps\android_app\app\google-services.json
```

注意：文件名必须正好是 `google-services.json`，不能是 `google-services (1).json`。

---

## 二、生成后端 Firebase 服务账号

在 Firebase Console 中进入：

```text
项目设置 → 服务账号 → Firebase Admin SDK → 生成新的私钥
```

将下载的 JSON 重命名为：

```text
firebase-service-account.json
```

并放到：

```text
D:\Projects\true-shield\secrets\firebase-service-account.json
```

`secrets` 目录和 Firebase 配置文件已加入 `.gitignore`。不要把私钥上传到 GitHub、聊天、网盘公开链接或应用安装包中。

---

## 三、覆盖补丁

将补丁中的全部内容复制到项目根目录：

```text
D:\Projects\true-shield
```

选择“替换目标中的文件”。

不要使用补丁中的 `.env.example` 覆盖你现有的 `.env`；只需手动把下一节的三个变量加入 `.env`。

---

## 四、配置后端 `.env`

打开：

```text
D:\Projects\true-shield\.env
```

加入：

```env
FIREBASE_PUSH_ENABLED=true
FIREBASE_PROJECT_ID=你的 Firebase 项目 ID
FIREBASE_CREDENTIALS_FILE=secrets/firebase-service-account.json
```

`FIREBASE_PROJECT_ID` 填 Firebase 的“项目 ID”，不是项目显示名称。它也可以在 `google-services.json` 的 `project_info.project_id` 中找到。

---

## 五、更新后端依赖并迁移数据库

```powershell
cd D:\Projects\true-shield\apps\api_server

uv sync
uv run alembic upgrade head
```

本次迁移会新增：

```text
push_devices
```

然后执行测试：

```powershell
uv run pytest tests/test_push_devices.py tests/test_push_notification.py tests/test_family_alert_automation_execution.py -q
```

最后重启后端：

```powershell
uv run uvicorn app.main:app --reload
```

---

## 六、同步并编译 Android

确保 `google-services.json` 已经放在 `app` 模块目录中，然后执行：

```powershell
cd D:\Projects\true-shield\apps\android_app

.\gradlew.bat :app:compileDebugKotlin
```

首次同步需要下载 Firebase Android 依赖，耗时可能比之前更长。

---

## 七、选择正确的测试设备

FCM 需要 Google Play 服务。建议使用：

- 安装了 Google Play 服务的 Android 真机；或
- Android Studio Device Manager 中带 **Google Play** 图标的系统镜像；或
- 支持 **Google APIs** 的模拟器镜像。

普通 AOSP 镜像通常无法正常完成 FCM 注册。

---

## 八、验证推送设备登记

1. 重新安装或启动 App；
2. 登录账户；
3. Android 13 及以上系统出现通知授权弹窗时点击“允许”；
4. 等待几秒，让 App 把 FID 同步到后端。

可以在 DBeaver 中检查：

```sql
SELECT
    user_id,
    installation_id,
    platform,
    device_name,
    app_version,
    status,
    last_seen_at
FROM push_devices
ORDER BY last_seen_at DESC;
```

当前设备应显示：

```text
status = active
```

---

## 九、验证家庭告警通知

推荐使用两个设备和两个账户，并让两个账户加入同一个家庭。

设备 A：

```text
文本风险检测
→ 创建一条高风险事件
→ 创建或自动生成家庭告警
```

设备 B 应收到：

```text
真信盾：新的家庭高风险告警
```

点击通知后，应直接打开对应的“家庭告警详情”。

接着在任一设备中：

```text
确认告警
→ 完成处理
```

家庭成员设备还应分别收到“家庭告警已确认”和“家庭告警已处理完成”的通知。

---

## 十、常见问题

### 1. Gradle 提示找不到 google-services.json

确认文件位于：

```text
apps\android_app\app\google-services.json
```

而不是 Android 项目根目录或 `src/main` 目录。

### 2. 后端提示缺少 firebase-admin

执行：

```powershell
uv sync
```

### 3. 后端提示服务账号文件不存在

检查 `.env` 中的相对路径，并确认文件真实存在：

```text
D:\Projects\true-shield\secrets\firebase-service-account.json
```

### 4. 登录成功但 `push_devices` 没有数据

检查：

- 模拟器是否包含 Google Play 服务；
- `google-services.json` 的包名是否为 `com.trueshield.app`；
- Android Studio Logcat 中是否存在 `TrueShieldPush` 或 `TrueShieldFCM` 错误；
- 手机是否联网。

### 5. 后端发送成功但手机不显示通知

检查：

- Android 通知权限是否允许；
- 系统设置中 TrueShield 的通知是否开启；
- `push_devices.status` 是否为 `active`；
- Firebase 项目 ID、Android 配置文件和服务账号是否来自同一个 Firebase 项目。
