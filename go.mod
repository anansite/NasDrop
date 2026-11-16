module nasdrop

go 1.24

// tailscale.com 故意不写死版本：
// 首次构建执行 `./build-aar.sh --upgrade` 会自动拉取最新版并写回这里。
// 之后每次升级 Tailscale 也只需要这一条命令，Kotlin 侧无需任何改动。
