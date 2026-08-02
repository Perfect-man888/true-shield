# 真信盾：本地录音文件上传补丁

本补丁在现有“现场录音 → FunASR 转写 → 风险检测”基础上，新增：

- Android 系统文件选择器；
- 选择手机中的已有录音；
- 支持 `m4a`、`mp3`、`wav`、`aac`、`mp4`、`ogg`；
- 显示文件名与大小；
- 单文件最大 20 MB；
- 按真实扩展名设置 Multipart Content-Type；
- 复制到应用缓存后上传，不修改用户原文件；
- 上传成功、清空页面或退出页面时删除缓存副本；
- 页面中的旧 Whisper 文案改为 FunASR。

## 覆盖位置

将压缩包内的 `apps` 文件夹复制到项目根目录：

```text
D:\Projects\true-shield
```

出现同名文件提示时选择“替换目标中的文件”。

## 编译

```powershell
cd D:\Projects\true-shield\apps\android_app
.\gradlew.bat :app:compileDebugKotlin
```

本补丁不修改数据库和后端接口，因此不需要 Alembic，也不需要重新下载 FunASR 模型。

## 验证

```text
首页 → 语音风险检测 → 选择本地录音
→ 选择 m4a/mp3/wav/aac 文件
→ 查看文件名和大小
→ 上传、转写并检测风险
```

Android 使用 Storage Access Framework 读取用户主动选择的文件，不需要新增存储权限。
