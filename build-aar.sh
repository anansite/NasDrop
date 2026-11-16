#!/usr/bin/env bash
# 把 Go 侧的 tsnet 桥编译成 Android 可用的 AAR。
#
#   ./build-aar.sh                 # 用当前 go.mod 里锁定的 Tailscale 版本构建
#   ./build-aar.sh --upgrade       # 先把 tailscale.com 升到最新版，再构建
#   ABIS=android/arm64 ./build-aar.sh   # 只编一个 ABI，迭代时快很多
#
# 升级 Tailscale 的完整流程就是：
#   ./build-aar.sh --upgrade && ./gradlew assembleRelease
# Kotlin 侧不需要任何改动。
set -euo pipefail
cd "$(dirname "$0")"

UPGRADE=0
[[ "${1:-}" == "--upgrade" ]] && UPGRADE=1

if ! command -v go >/dev/null 2>&1; then
	echo "找不到 Go。请先安装 Go 1.24+：https://go.dev/dl/" >&2
	exit 1
fi

if ! command -v gomobile >/dev/null 2>&1; then
	echo "==> 安装 gomobile"
	go install golang.org/x/mobile/cmd/gomobile@latest
	export PATH="$PATH:$(go env GOPATH)/bin"
fi

# gomobile bind 内部会调用 gobind，两者必须同时存在。
# 只装 gomobile 时这里会报 "gobind was not found. Please run gomobile init"。
if ! command -v gobind >/dev/null 2>&1; then
	echo "==> 安装 gobind"
	go install golang.org/x/mobile/cmd/gobind@latest
	export PATH="$PATH:$(go env GOPATH)/bin"
fi

# 注意：不能直接用 grep 找 "tailscale.com"，go.mod 的注释里也会有这个词，
# 那样会误判成"已拉取"从而跳过整步。这里只认真正的 require 行。
has_require() {
	grep -qE "^[[:space:]]*$1[[:space:]]+v" go.mod
}

if [[ $UPGRADE == 1 ]] || ! has_require 'tailscale\.com'; then
	echo "==> 拉取 Tailscale 依赖"
	go get tailscale.com@latest
	go mod tidy
fi

# 顺序很重要：go mod tidy 会把没有被任何代码 import 的 golang.org/x/mobile 删掉，
# 而 gomobile bind 又要求它出现在 go.mod 里，所以必须放在 tidy 之后。
if ! has_require 'golang\.org/x/mobile'; then
	echo "==> 拉取 gomobile 运行时依赖"
	go get golang.org/x/mobile/bind@latest
fi

echo "==> 当前 Tailscale 版本"
go list -m tailscale.com

ABIS="${ABIS:-android/arm64,android/arm,android/amd64}"
mkdir -p app/libs

echo "==> gomobile bind ($ABIS)"
# -androidapi 26 与 app/build.gradle.kts 里的 minSdk 保持一致。
# 体积优化：需要更小的包可以加 -tags ts_omit_*，但那会削减 tsnet 能力，
# 建议先按默认全量跑通，再逐项裁剪。
gomobile bind \
	-target="$ABIS" \
	-androidapi 26 \
	-o app/libs/bridge.aar \
	./bridge

ls -lh app/libs/bridge.aar
echo "==> 完成。现在可以 ./gradlew assembleRelease 出包。"
