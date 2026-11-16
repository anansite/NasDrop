module nasdrop

go 1.24

// 这个文件里的 require 由 ./build-aar.sh 自动维护：
//   首次构建 / ./build-aar.sh --upgrade  -> 拉取最新版 Tailscale 并写回这里
//   之后每次构建                        -> 用这里锁定的版本，保证可复现
// 想升级 Tailscale，只需要重新跑一次 --upgrade，Kotlin 侧不用改任何代码。
