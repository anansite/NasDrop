package com.nasdrop

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import java.util.Collections

/**
 * 把本机网卡清单推给 Go 侧。
 *
 * 为什么需要这个：Android SDK 30 起禁止 app UID 使用 NETLINK_ROUTE 和 /proc/net，
 * Go 标准库的 net.Interfaces() 必然失败，tsnet 会报
 * "route ip+net: netlinkrib: permission denied" 起不来。
 *
 * 但 Java 的 NetworkInterface.getNetworkInterfaces() 和 ConnectivityManager 都是允许的，
 * 所以这里取清单、序列化成 JSON 交给 Go，由 Go 注册成 netmon 的接口获取器
 * （这正是官方 Tailscale Android App 的做法）。
 */
internal object IfaceReporter {

	private const val TAG = "IfaceReporter"

	@Volatile
	private var registered = false

	/** 注册一次网络变化监听，并立刻推一份当前快照 */
	fun register(context: Context) {
		push()
		if (registered) return
		registered = true
		runCatching {
			val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
			// 默认路由（网卡名 + 网关）也读不到，一并推过去
			pushRoute(cm.getLinkProperties(cm.activeNetwork))
			cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
				override fun onAvailable(network: Network) = push()
				override fun onLost(network: Network) = push()
				override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = push()
				override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
					push()
					pushRoute(lp)
				}
			})
			Log.i(TAG, "已注册网络变化监听")
		}.onFailure { Log.w(TAG, "注册网络监听失败", it) }
	}

	private fun pushRoute(lp: LinkProperties?) {
		val ifName = lp?.interfaceName ?: return
		val gw = lp.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: ""
		runCatching { TsBridge.setDefaultRoute(ifName, gw) }
			.onFailure { Log.w(TAG, "推送默认路由失败", it) }
	}

	/** 重新读取并推送。网络切换后调用，Go 侧下次取值就是新的。 */
	fun push() {
		val json = runCatching { snapshot() }.getOrElse {
			Log.w(TAG, "读取网卡清单失败", it)
			return
		}
		runCatching { TsBridge.setInterfaces(json) }
			.onFailure { Log.w(TAG, "推送网卡清单失败", it) }
	}

	private fun snapshot(): String {
		val arr = JSONArray()
		// 用 Collections.list 转成 List，避免依赖 Kotlin 对 Enumeration 的扩展
		for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
			val addrs = JSONArray()
			for (ia in ni.interfaceAddresses) {
				val host = ia.address?.hostAddress ?: continue
				// IPv6 的 hostAddress 会带 %wlan0 这样的 scope，Go 的 ParseCIDR 不认
				val ip = host.substringBefore('%')
				addrs.put("$ip/${ia.networkPrefixLength}")
			}

			val flags = JSONArray()
			if (ni.isUp) flags.put("up")
			if (ni.isLoopback) flags.put("loopback")
			if (ni.isPointToPoint) flags.put("pointtopoint")
			if (ni.supportsMulticast()) flags.put("multicast")

			arr.put(
				JSONObject()
					.put("name", ni.name)
					.put("index", ni.index)
					.put("mtu", runCatching { ni.mtu }.getOrDefault(1500))
					.put("flags", flags)
					.put("addrs", addrs)
			)
		}
		return arr.toString()
	}
}
