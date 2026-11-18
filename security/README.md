# 安装包来源与签名

正式 APK 使用 v2 + v3 签名，信任的证书 SHA-256 固定为：

```text
f89eb1342441f6dd6a62de79f1a54cc527ce3838c9f26c45f28084d8dc501eb4
```

公开证书在 `bongocat-signing-cert.pem`；它只有公钥，不含私钥。证书沿用旧版本，支持覆盖更新。其历史主题仍是 `CN=Android Debug, O=Android, C=US`，不能仅凭显示名称判断身份；验签以完整证书指纹为准。这不是商店或第三方的实名认证。

## 下载后验签

从 [项目仓库](https://github.com/yangyuyu666/bongocat-android) 获取 `tools/verify-apk.ps1`，在安装前运行。需要 JDK 和 Android SDK Build Tools：

```powershell
powershell -NoProfile -File tools/verify-apk.ps1 -Apk .\BongoCat-v3.16.apk -BuildToolsDirectory 'D:\Android\Sdk\build-tools\36.0.0'
```

工具先让 Android 官方 `apksigner` 验证 APK 内容和签名，再比对固定的发布证书指纹、包名和正式包标记，输出版本及文件 SHA-256。不要只检查 `SHA256SUMS.txt`，也不要从不可信来源同时获取 APK 与验签脚本。

## 应用启动自检

正式包在 `PetApplication` 初始化 Rive 之前检查 Android 提供的当前安装签名。证书缺失、签名者不匹配、多签名者、包名异常或读取失败都会终止初始化。Android 8 使用旧签名接口，Android 9 起使用 `SigningInfo`；历史证书不能代替当前签名者通过检查。本地 Debug 构建不启用启动拦截，方便独立开发。

Android 的 APK 签名和独立验签工具用于验证来源与完整性。源码公开，应用里的自检可以被修改者删除，所以它只是补充检查，不能替代安装前验签。换钥匙重签的修改版仍不能通过上述固定证书校验，也不能直接覆盖原签名的已安装版本。

## 发布构建

正式包读取未提交的 `.signing/release.properties`：

```properties
storeFile=.signing/release.p12
storePassword=<本地随机口令>
keyAlias=bongocat-release
keyPassword=<本地随机口令>
```

发布构建会检查该 PKCS12 中的证书与 `signer.sha256` 一致。缺少配置或证书不匹配时停止构建，不回退到临时签名。签名身份与旧版本保持一致，现有私钥副本使用随机口令重新加密；`.signing` 的本机目录访问权限限制为当前用户与系统。请离线备份自己的密钥和口令，不能上传到公开仓库或 Release 附件。

没有维护者私钥的贡献者使用 `assembleDebug`。自行生成的调试包不能作为正式发行包，也不能直接覆盖维护者签名的版本。

实现依据：[Android 应用签名](https://developer.android.com/studio/publish/app-signing)、[apksigner](https://developer.android.com/tools/apksigner)、[APK v3 签名](https://source.android.com/docs/security/features/apksigning/v3)、[SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo)。
