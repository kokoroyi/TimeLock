# 发布签名

仓库里不包含任何密钥，`keystore.properties` 与 `*.jks` 都在 `.gitignore` 里。
要构建可安装的 release 包，自己生成一份：

## 1. 生成 keystore（只需一次）

```bash
keytool -genkeypair -v -keystore release.jks \
  -alias timelock -keyalg RSA -keysize 2048 -validity 10000
```

## 2. 在工程根目录建 `keystore.properties`

```properties
storeFile=release.jks
storePassword=你的密码
keyAlias=timelock
keyPassword=你的密码
```

`storeFile` 可以写相对工程根目录的路径，也可以写绝对路径。**不要提交这个文件。**

## 3. 构建

```bash
./gradlew assembleRelease
```

产物在 `app/build/outputs/apk/release/`。

没有 `keystore.properties` 时 release 一样能构建，只是产出未签名 APK，不能直接安装到手机。
日常调试用 `./gradlew assembleDebug` 即可，debug 包由 Gradle 自动签名。
