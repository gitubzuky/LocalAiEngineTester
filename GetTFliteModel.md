# 获取 RTMDet TFLite 模型

本文记录通过 Qualcomm AI Hub export 获取 RTMDet TFLite 模型的流程，并说明如何放入本项目 assets 目录供 `LocalModelDiscovery` 自动发现。

> 注意：Qualcomm AI Hub 的 `qai_hub_models.models.rtmdet` 当前导出的是 AI Hub 默认 RTMDet 模型，不等同于 OpenMMLab `rtmdet_tiny_8xb32-300e_coco`。如果需要真正的 RTMDet-tiny，请参考 `tools/rtmdet_tiny_tflite/README.md`。

## 1. 安装 Miniconda

Miniconda [下载地址](https://www.anaconda.com/docs/getting-started/miniconda/main)


在 Windows 上建议安装 Miniconda 图形界面安装包：

```text
Miniconda3 Windows 64-bit graphical installer
```

安装选项建议：

- 勾选 `Create shortcuts`
- 不勾选 `Register Miniconda3 as the system Python 3.13 ...`
- `Clear the package cache upon completion...` 可勾选，用于节省空间

安装完成后，从开始菜单打开 `Miniconda Prompt`。

## 2. 验证 conda

在 `Miniconda Prompt` 中执行：

```powershell
conda --version
conda info
```

如果遇到 Terms of Service 未接受的错误，按提示接受默认 channel：

```powershell
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/main
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/r
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/msys2
```

## 3. 创建导出环境

建议切到一个临时工作目录，便于保存导出产物和日志：

```powershell
E:
cd E:\Other\AI_Model
mkdir rtmdet_tiny_person
cd rtmdet_tiny_person
```

创建 Python 3.10 环境：

```powershell
conda create -n qai_hub python=3.10 -y
conda activate qai_hub
python --version
```

确认输出为 `Python 3.10.x`。

安装 AI Hub RTMDet 工具包：

```powershell
python -m pip install -U pip
pip install "qai-hub-models[rtmdet]"
```

## 4. 注册 Qualcomm AI Hub 并获取 token

打开 Qualcomm AI Hub Workbench：

```text
https://workbench.aihub.qualcomm.com/
```

登录或注册账号，然后进入：

```text
Account / Settings / API Token
```

复制 API token 后，在 `Miniconda Prompt` 中配置：

```powershell
qai-hub configure --api_token YOUR_API_TOKEN
```

将 `YOUR_API_TOKEN` 替换为实际 token。

## 5. 导出 RTMDet TFLite

在已激活的 `qai_hub` 环境中执行：

```powershell
python -m qai_hub_models.models.rtmdet.export --target-runtime tflite
```

过程中可能出现类似提示：

```text
gaze_estimation requires external repo https://github.com/david-wb/gaze-estimation. Ok to clone?[Y/n]
```

这是其他可选模型依赖触发的提示，不是 RTMDet 的关键依赖。可以输入 `n` 后回车；如果已经自动继续下载，通常也不会影响 RTMDet export。

导出成功后，日志中会出现类似内容：

```text
rtmdet.tflite: 100%|...| 105M/105M
Downloaded model to ...\rtmdet-tflite-float\rtmdet.tflite

Performance results on-device for Rtmdet_Float.
Runtime                         : TFLITE
Estimated inference time (ms)   : 9.1

rtmdet_float was saved to E:\Other\AI_Model\rtmdet_tiny_person\export_assets\rtmdet-tflite-float
```

最终 TFLite 文件通常位于：

```text
E:\Other\AI_Model\rtmdet_tiny_person\export_assets\rtmdet-tflite-float\rtmdet.tflite
```

实际路径以命令输出为准。

## 6. 放入项目 assets

本项目通过 `LocalModelDiscovery` 扫描：

```text
app/src/main/assets/models/tflite/
```

RTMDet person detector 文件名需要同时包含：

```text
rtmdet
person
```

推荐复制并改名为：

```text
E:\Project\LocalAIEngineTester\app\src\main\assets\models\tflite\rtmdet_tiny_person.tflite
```

PowerShell 示例：

```powershell
Copy-Item `
  "E:\Other\AI_Model\rtmdet_tiny_person\export_assets\rtmdet-tflite-float\rtmdet.tflite" `
  "E:\Project\LocalAIEngineTester\app\src\main\assets\models\tflite\rtmdet_tiny_person.tflite"
```

## 7. 当前 AI Hub RTMDet 输出格式

本次 AI Hub export 的 TFLite 输出示例：

```text
boxes     (1, 8400, 4)
scores    (1, 8400)
class_idx (1, 8400)
```

其中：

- `boxes` 是候选框
- `scores` 是每个候选框的置信度
- `class_idx` 是每个候选框的类别索引

项目的 RTMDet 解码器需要支持 `boxes + scores + class_idx` 这种输出格式，并按 COCO person 类过滤后再喂给 RTMPose。

## 8. 参考链接

- Qualcomm AI Hub Workbench: https://workbench.aihub.qualcomm.com/
- Qualcomm AI Hub RTMDet: https://aihub.qualcomm.com/models/rtmdet
- AI Hub Models RTMDet: https://github.com/qualcomm/ai-hub-models/tree/main/qai_hub_models/models/rtmdet
