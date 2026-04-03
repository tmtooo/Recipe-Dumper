# Recipe Pathfinder Extractor

这是一个用于自动提取 Minecraft 模组且适配 **GregTech Modern (GTCEu)** 及其附属配方(可导出如电压，时间，功率，输入输出物品/流体及其数量)的配方提取模组。通过官方 API 稳定抓取合成树数据，并导出为 JSON 格式。

**声明：本项目大部分由 AI 编写，质量参差不齐**

## 项目配合使用

抓取的数据文件（如 `recipes_example.json`）可喂给与配套的**前端可视化配方树系统**进行搜索和展示。
🔗 配套展示项目：[0]

## 如何构建 (Build)

本项目基于标准的 Minecraft Forge 构建系统。
在根目录打开终端，运行：
```bash
./gradlew build (Linux/Mac)
# 或
.\gradlew.bat build (Windows)
```
编译成功后，生成的 `.jar` 模组文件将位于 `build/libs` 目录下。

## 许可协议 (License)

采用 [MIT License](LICENSE) 协议开源。
