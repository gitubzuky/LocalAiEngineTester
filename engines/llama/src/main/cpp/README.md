# llama.cpp 源码

本模块通过 Git submodule 引入 `llama.cpp` 源码，并通过 Android CMake 编译 `libllama_jni.so`。

submodule 路径：

```text
engines/llama/src/main/cpp/llama.cpp
```

当前固定版本：

```text
describe: b9222-16-g45c4c1c61
commit: 45c4c1c618b74b9911e5cbe5910ad0caba085d2d
```

注意：固定到 tag 或 commit 时通常会进入 `detached HEAD`，这是 submodule 固定版本的正常状态。

## 初始化 submodule

首次接入时，在项目根目录执行：

```powershell
git submodule add https://github.com/ggml-org/llama.cpp.git engines/llama/src/main/cpp/llama.cpp
git -C engines/llama/src/main/cpp/llama.cpp fetch --tags
git -C engines/llama/src/main/cpp/llama.cpp checkout 45c4c1c618b74b9911e5cbe5910ad0caba085d2d
git submodule update --init --recursive
```

完成后提交 `.gitmodules` 和 submodule 指针：

```powershell
git add .gitmodules engines/llama/src/main/cpp/llama.cpp
```

如果 `git fetch --tags` 因 GitHub 443 连接失败而报错，但 `git checkout b9204` 可以成功，说明 clone 时已经带有该 tag，可以继续后续步骤。

## 验证版本

```powershell
git -C engines/llama/src/main/cpp/llama.cpp describe --tags --always
git -C engines/llama/src/main/cpp/llama.cpp rev-parse HEAD
```

期望输出分别为：

```text
b9222-16-g45c4c1c61
45c4c1c618b74b9911e5cbe5910ad0caba085d2d
```

## 验证 native 构建

`engines:llama` 模块会在 `llama.cpp` 目录存在后自动启用 CMake native 编译。

```powershell
.\gradlew.bat :engines:llama:externalNativeBuildDebug
.\gradlew.bat :app:assembleDebug
```

当前已验证通过：

- `:engines:llama:externalNativeBuildDebug`
- `:app:assembleDebug`
- `test`

当前仅启用 `arm64-v8a` ABI。后续如需增加 ABI，请同步调整 `engines/llama/build.gradle.kts` 中的 `abiFilters`。

## 常见问题

- `git fetch --tags` 连接 GitHub 失败：通常是本机代理或网络问题；如果本地已有 `b9204` tag，可直接 `git checkout b9204`。
- 拉取 GitHub PR ref 时不要配置全局 Git 代理，因为其他项目可能需要直连自己的 Git 服务器。可以只给单条 Git 命令临时指定代理：

  ```powershell
  git -C engines/llama/src/main/cpp/llama.cpp -c http.proxy=http://127.0.0.1:7897 -c https.proxy=http://127.0.0.1:7897 fetch origin pull/22836/head:pr-22836-stq_0
  ```

  `$env:HTTP_PROXY`、`$env:HTTPS_PROXY`、`$env:ALL_PROXY` 只影响当前 PowerShell 会话以及它启动的子进程；如果使用这些环境变量，执行完 Git 命令后应恢复或移除，避免影响同一终端里的后续命令。
- 看到 `detached HEAD` 提示：这是 checkout tag 的正常现象，submodule 固定版本时符合预期。
- CMake 找不到 `llama.cpp`：确认 `engines/llama/src/main/cpp/llama.cpp/CMakeLists.txt` 是否存在。
