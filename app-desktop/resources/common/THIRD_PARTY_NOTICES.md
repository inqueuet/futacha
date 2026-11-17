# Futacha Desktop — third-party components

This local desktop build includes the components below. Their authors retain all
copyrights. This inventory does not assign a license to Futacha itself.

| Component | Version | Upstream / license information |
| --- | --- | --- |
| VLC / libVLC and bundled plugins | macOS arm64: 3.0.23; Windows x64: 4.0.0-dev (2384e64a) | https://www.videolan.org/vlc/ — GPL / LGPL; COPYING and COPYING.LIB in `licenses/` |
| vlcj | 5.0.0-M6 | https://github.com/caprica/vlcj — GPL-3.0; text in `licenses/` |
| JavaCV / JavaCPP | 1.5.14 | https://github.com/bytedeco/javacv and https://github.com/bytedeco/javacpp — Apache-2.0 / GPL with Classpath exception; upstream text in `licenses/` |
| FFmpeg (JavaCPP preset) | 8.1.2-1.5.14 | https://github.com/bytedeco/javacpp-presets/tree/1.5.14/ffmpeg — see https://ffmpeg.org/legal.html and `licenses/FFmpeg-COPYING.LGPLv2.1.txt` |
| OpenCV | 5.1.0, commit e4f78fbcf1a773fbed69e14500e8b734963eb585 | https://github.com/opencv/opencv — Apache-2.0; text in `licenses/` |
| ONNX Runtime | 1.32.0 | https://github.com/microsoft/onnxruntime — MIT |
| SQLite JDBC | 3.54.1.0 | https://github.com/xerial/sqlite-jdbc — Apache-2.0 / BSD; SQLite core is public domain |
| JNA | dependency resolved by vlcj | https://github.com/java-native-access/jna — LGPL-2.1 / Apache-2.0 |
| Kotlin, Compose Multiplatform, AndroidX, Ktor, OkHttp, Coil, Okio | Gradle resolved versions | Respective upstream licenses and dependency metadata |
| Java runtime | See bundled `runtime/release` (Windows) or `runtime/Contents/Home/release` (macOS) | Runtime notices are retained in the corresponding `legal/` directory |

The native staging recipe, hashes and OpenCV build options are recorded in
`tools/prepare-desktop-resources.py` and `tools/prepare-windows-resources.py`. Windows bridge runtime notices are also in `native/licenses/`. FFmpeg encoding uses libopenh264; other
bundled codecs/plugins have their own notices and source requirements.

This artifact is for local verification. Before publishing a binary, complete
the source/notices bundle for all native dependencies and decide the application
license or obtain an appropriate vlcj license. Upstream links alone are not a
complete redistribution source bundle. No public release has been performed.
