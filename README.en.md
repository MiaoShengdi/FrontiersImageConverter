# Frontiers Image Converter

English | [中文](README.md)

One-click conversion of common image formats (PNG / JPG / JPEG / BMP / WEBP / GIF / TIF) into TIFF files meeting Frontiers submission requirements. Available as a Windows desktop app (exe) and an Android app (apk).

## Output Specifications

- Format: TIFF (.tif) with lossless LZW compression
- Color: RGB mode; images with an alpha channel are automatically flattened onto a white background
- Resolution: 300 dpi
- File size: no more than 10 MB per file; oversized images are automatically downscaled iteratively (0.9x per round, minimum 600 px on the short edge)
- EXIF orientation of mobile photos is corrected automatically

## Desktop (Windows)

Run `FrontiersImageConverter.exe` directly. You can select multiple images or an entire folder (processed recursively). By default, output files are written to a `Frontiers_TIFF` folder next to each source image; a single shared output folder can also be selected.

## Mobile (Android)

Install `FrontiersImageConverter.apk` (Android 8.0 or above; allow installation from unknown sources when prompted). Select one or more images, choose an output directory, and tap start. All file access goes through the system document picker, so no permissions are requested.

## Source Code and Build

- `desktop/`: Python + Pillow conversion core with a tkinter GUI, packaged with PyInstaller
- `android/`: native Java single-activity app with a custom in-house LZW TIFF encoder and no third-party dependencies; run `build.ps1` to build manually
- `assets/`: application icon

## License

This software is for academic submission and learning purposes only. **Any commercial use is prohibited.** The same notice is displayed within the application interface.

## Author

Shengdi Miao
Email: miaoshengdi@hunnu.edu.cn
