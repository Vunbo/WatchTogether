# WatchTogether 发布流程

## 版本源

版本只修改 `app/version.properties`。

```properties
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=2
VERSION_CODE=3
VERSION_SUFFIX=
```

`VERSION_SUFFIX` 为空时，版本名为 `1.0.2`；如果填 `beta.1`，版本名为 `1.0.2-beta.1`，GitHub Release 会标记为 prerelease。

## 本地签名配置

正式发布必须使用同一个签名证书。证书文件不要提交到 Git。

本地可复制：

```text
release/keystore.properties.example -> release/keystore.properties
```

然后填写：

```properties
storeFile=D:\\Desktop\\VideoTogether\\wt_key.jks
storePassword=你的密码
keyAlias=你的alias
keyPassword=你的密码
```

如果忘记 alias，可在本机执行：

```powershell
keytool -list -v -keystore D:\Desktop\VideoTogether\wt_key.jks
```

## GitHub Secrets

进入 GitHub 仓库：

```text
Settings -> Secrets and variables -> Actions -> New repository secret
```

添加：

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

PowerShell 生成 keystore base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\Desktop\VideoTogether\wt_key.jks")) | Set-Clipboard
```

然后把剪贴板内容填入 `ANDROID_KEYSTORE_BASE64`。

## 测试分支

你当前使用 `test-cicd` 分支测试时，需要先把代码推到该分支。正式发布 workflow 是 tag 触发，tag 指向哪个分支的提交，就发布哪个提交。

## 发布命令

确认版本文件已更新，例如 `VERSION_MAJOR=1`、`VERSION_MINOR=0`、`VERSION_PATCH=2` 组成的版本名 `1.0.2` 对应 tag `v1.0.2`。

```bash
git add .
git commit -m "release 1.0.2"
git push origin test-cicd

git tag -a v1.0.2 -m "Release v1.0.2"
git push origin v1.0.2
```

GitHub Actions 会自动：

1. 构建 release APK
2. 使用 Secrets 中的证书签名
3. 生成 `update.json`
4. 创建 GitHub Release
5. 上传 `WatchTogether-版本号.apk` 和 `update.json`

App 检测更新时读取：

```text
https://github.com/Vunbo/WatchTogether/releases/latest/download/update.json
```
