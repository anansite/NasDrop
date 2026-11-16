//go:build android

package bridge

// 默认路由信息在 Android 上同样读不到（/proc/net/route 也是 EACCES）。
// 官方 Android App 是靠 ConnectivityManager 把「默认网卡名 + 网关」推给 netmon 的，
// 这里做同样的事。拿不到时不设置，netmon 会容忍空值。
//
// 单独放一个 android-only 文件，是因为这两个函数只在 netmon 的 android 实现里存在。

import "tailscale.com/net/netmon"

// SetDefaultRoute 由 Kotlin 侧推送当前默认路由的网卡名与网关。
func SetDefaultRoute(ifName, gateway string) {
	if ifName != "" {
		netmon.UpdateLastKnownDefaultRouteInterface(ifName)
	}
	if gateway != "" {
		netmon.UpdateLastKnownDefaultGateway(gateway)
	}
}
