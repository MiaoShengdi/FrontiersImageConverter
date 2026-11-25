# Frontiers 图片转换工具

[English](README.en.md) | 中文

一键把 PNG / JPG / JPEG / BMP / WEBP / GIF / TIF 等常见图片转换为符合 Frontiers 投稿要求的 TIFF 图片，提供 Windows 桌面版（exe）与 Android 手机版（apk）。

## 输出规格

- 格式：TIFF（.tif），LZW 无损压缩
- 色彩：RGB 模式；带透明通道的图片自动白底压平
- 分辨率：300 dpi
- 体积：单文件不超过 10 MB；超限时自动等比缩小（每轮 0.9 倍，短边保底 600 px）
- 自动处理手机照片的 EXIF 旋转信息

## 桌面端（Windows）

直接运行 `FrontiersImageConverter.exe`，可选择多张图片或整个文件夹（递归处理），默认在每张原图旁边创建 `Frontiers_TIFF` 目录输出，也可勾选统一输出目录。

## 手机端（Android）

安装 `FrontiersImageConverter.apk`（需 Android 8.0 及以上，安装时允许未知来源）。打开后先选择图片（支持多选），再选择输出目录，点击开始转换。全程通过系统文件选择器访问文件，不申请任何权限。

## 源码与构建

- `desktop/`：Python + Pillow 转换核心与 tkinter 界面，PyInstaller 打包
- `android/`：原生 Java 单 Activity，内置自研 LZW TIFF 编码器，无第三方依赖，运行 `build.ps1` 手动构建
- `assets/`：应用图标

## 授权声明

本软件仅供学术投稿与学习使用，**禁止任何形式的商业用途**。软件界面内同样标注有该声明。

## 作者

Shengdi Miao（苗圣笛）
邮箱：miaoshengdi@hunnu.edu.cn
