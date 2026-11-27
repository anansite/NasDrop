package com.nasdrop

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DlnaDevice(
	val id: String,
	val name: String,
	val controlUrl: String,
	val location: String,
	val host: String,
)

enum class DlnaPlaybackState {
	IDLE,
	CONNECTING,
	PLAYING,
	PAUSED,
	ERROR,
}

internal data class LanInterfaceInfo(
	val name: String,
	val networkInterface: NetworkInterface,
	val address: Inet4Address,
	val priority: Int,
)

internal fun listLanMulticastInterfaces(): List<LanInterfaceInfo> {
	val results = mutableListOf<LanInterfaceInfo>()
	val ifaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
	for (ni in ifaces) {
		val usable = runCatching { ni.isUp && !ni.isLoopback && !ni.isPointToPoint && !ni.isVirtual }.getOrDefault(false)
		if (!usable) continue
		val ifName = ni.name.lowercase(Locale.ROOT)
		if (ifName.startsWith("tun") || ifName.startsWith("tap") || ifName.startsWith("ppp") ||
			ifName.startsWith("rmnet") || ifName.startsWith("ccmni") || ifName.startsWith("pdp") ||
			ifName.startsWith("dummy") || ifName.startsWith("v4-")
		) continue

		val basePriority = when {
			ifName.startsWith("wlan") || ifName.startsWith("wifi") -> 0
			ifName.startsWith("eth") -> 1
			ifName.startsWith("ap") || ifName.startsWith("swlan") -> 2
			else -> 3
		}
		for (addr in ni.inetAddresses.toList()) {
			if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isMulticastAddress) {
				val siteBoost = if (addr.isSiteLocalAddress) 0 else 10
				results.add(LanInterfaceInfo(ni.name, ni, addr, basePriority + siteBoost))
			}
		}
	}
	return results.sortedBy { it.priority }
}

internal fun chooseLanIpForTarget(targetHost: String, candidateIps: List<String>): String? {
	if (candidateIps.isEmpty()) return null
	val targetParts = targetHost.split('.').mapNotNull { it.toIntOrNull() }
	if (targetParts.size == 4) {
		val best = candidateIps.maxByOrNull { ip ->
			val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
			if (parts.size != 4) -1
			else {
				var score = 0
				for (i in 0 until 4) {
					if (parts[i] == targetParts[i]) score++ else break
				}
				score
			}
		}
		if (best != null) return best
	}
	return candidateIps.firstOrNull()
}

internal fun extractSsdpHeader(response: String, name: String): String? {
	for (line in response.lineSequence()) {
		val clean = line.trimEnd('\r')
		val idx = clean.indexOf(':')
		if (idx > 0) {
			val headerName = clean.substring(0, idx).trim()
			if (headerName.equals(name, ignoreCase = true)) {
				return clean.substring(idx + 1).trim()
			}
		}
	}
	return null
}

internal fun parseUpnpDeviceDescription(xml: String, location: String, deviceId: String): DlnaDevice? {
	if (xml.isBlank()) return null
	val cleanXml = xml
		.replace(Regex("<!--[\\s\\S]*?-->"), "")
		.replace(Regex("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>")) { it.groupValues[1] }

	val urlBaseRegex = Regex("<(?:[\\w.-]+:)?URLBase\\b[^>]*>([\\s\\S]*?)</(?:[\\w.-]+:)?URLBase>", RegexOption.IGNORE_CASE)
	val urlBase = urlBaseRegex.find(cleanXml)?.groupValues?.get(1)?.trim()?.takeIf {
		it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
	}

	val serviceRegex = Regex("<(?:[\\w.-]+:)?service\\b[^>]*>([\\s\\S]*?)</(?:[\\w.-]+:)?service>", RegexOption.IGNORE_CASE)
	val serviceTypeRegex = Regex("<(?:[\\w.-]+:)?serviceType\\b[^>]*>([\\s\\S]*?)</(?:[\\w.-]+:)?serviceType>", RegexOption.IGNORE_CASE)
	val controlUrlRegex = Regex("<(?:[\\w.-]+:)?controlURL\\b[^>]*>([\\s\\S]*?)</(?:[\\w.-]+:)?controlURL>", RegexOption.IGNORE_CASE)
	val friendlyNameRegex = Regex("<(?:[\\w.-]+:)?friendlyName\\b[^>]*>([\\s\\S]*?)</(?:[\\w.-]+:)?friendlyName>", RegexOption.IGNORE_CASE)
	val deviceTagRegex = Regex("</?(?:[\\w.-]+:)?device\\b[^>]*>", RegexOption.IGNORE_CASE)

	val rootName = friendlyNameRegex.find(cleanXml)?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: "智能电视"

	var matchedControlUrl: String? = null
	var matchedStartIndex = -1
	for (svcMatch in serviceRegex.findAll(cleanXml)) {
		val block = svcMatch.groupValues[1]
		val sType = serviceTypeRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
		if (sType.startsWith("urn:schemas-upnp-org:service:AVTransport:", ignoreCase = true)) {
			val ctrl = controlUrlRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
			if (ctrl.isNotBlank()) {
				matchedControlUrl = ctrl
				matchedStartIndex = svcMatch.range.first
				break
			}
		}
	}
	val rawControlUrl = matchedControlUrl ?: return null

	// 定位归属该 AVTransport 服务的最近父级 <device> 节点，以防多子设备 XML 取错 friendlyName
	var deviceBlockStart = 0
	var deviceBlockEnd = cleanXml.length
	val stack = ArrayDeque<Int>()
	for (tag in deviceTagRegex.findAll(cleanXml)) {
		val text = tag.value
		val isClose = text.startsWith("</")
		val isSelfClose = text.endsWith("/>")
		if (!isClose && !isSelfClose) {
			stack.addLast(tag.range.first)
		} else if (isClose && stack.isNotEmpty()) {
			val openIdx = stack.removeLast()
			val closeIdx = tag.range.last + 1
			if (matchedStartIndex in openIdx..closeIdx && (closeIdx - openIdx) < (deviceBlockEnd - deviceBlockStart)) {
				deviceBlockStart = openIdx
				deviceBlockEnd = closeIdx
			}
		}
	}
	val deviceSlice = cleanXml.substring(deviceBlockStart, deviceBlockEnd)
	// 取当前 device 内部（先于嵌套 deviceList 之前）的 friendlyName，若无则回退 rootName
	val nestedDeviceListIdx = Regex("<(?:[\\w.-]+:)?deviceList\\b", RegexOption.IGNORE_CASE).find(deviceSlice)?.range?.first
	val ownSlice = if (nestedDeviceListIdx != null && nestedDeviceListIdx > 0) deviceSlice.substring(0, nestedDeviceListIdx) else deviceSlice
	val name = friendlyNameRegex.find(ownSlice)?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: rootName

	val baseUri = URI.create(urlBase ?: location)
	val resolvedControlUrl = runCatching { baseUri.resolve(rawControlUrl).toString() }.getOrElse {
		val portPart = if (baseUri.port != -1) ":${baseUri.port}" else ""
		val base = "${baseUri.scheme}://${baseUri.host}$portPart"
		if (rawControlUrl.startsWith("/")) "$base$rawControlUrl" else "$base/$rawControlUrl"
	}
	val host = runCatching { URI.create(location).host }.getOrNull() ?: baseUri.host ?: ""

	return DlnaDevice(
		id = deviceId,
		name = name,
		controlUrl = resolvedControlUrl,
		location = location,
		host = host,
	)
}

/**
 * 轻量级局域网 HTTP 流媒体中继代理：
 * 当 NAS URL 携带 Basic Auth、HTTPS 自签名或 Tailscale 本地隧道 127.0.0.1 时，
 * 智能电视的 DLNA 播放器无法直接访问，由手机端代理转发 Range / HEAD / GET 流给电视。
 */
internal class DlnaMediaProxy(private val upstreamHttp: OkHttpClient) {
	private val lock = Any()
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var serverSocket: ServerSocket? = null
	private var acceptJob: Job? = null
	private var activeToken: String = ""
	private var activeUpstreamUrl: String = ""
	private val streamHttp: OkHttpClient = upstreamHttp.newBuilder()
		.readTimeout(0, TimeUnit.SECONDS)
		.build()

	val port: Int get() = serverSocket?.localPort ?: -1

	fun expose(upstreamUrl: String, title: String, advertiseIp: String): String {
		ensureStarted()
		val token = UUID.randomUUID().toString().replace("-", "").take(16)
		synchronized(lock) {
			activeToken = token
			activeUpstreamUrl = upstreamUrl
		}
		val safeFileName = title.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "media.mp4" }
		val encoded = URLEncoder.encode(safeFileName, "UTF-8").replace("+", "%20")
		return "http://$advertiseIp:${serverSocket!!.localPort}/cast/$token/$encoded"
	}

	fun ensureStarted() {
		synchronized(lock) {
			if (serverSocket != null && !serverSocket!!.isClosed) return
			val ss = ServerSocket(0, 32)
			serverSocket = ss
			acceptJob = scope.launch {
				while (!ss.isClosed) {
					try {
						val client = ss.accept()
						launch { handleClient(client) }
					} catch (_: Exception) {
						if (ss.isClosed) break
					}
				}
			}
		}
	}

	fun close() {
		synchronized(lock) {
			activeToken = ""
			activeUpstreamUrl = ""
			runCatching { serverSocket?.close() }
			serverSocket = null
			acceptJob?.cancel()
			acceptJob = null
		}
	}

	private fun handleClient(socket: Socket) {
		socket.use { client ->
			runCatching { client.soTimeout = 15_000 }
			val input = BufferedInputStream(client.getInputStream())
			val out = BufferedOutputStream(client.getOutputStream())

			val requestLine = readLineAscii(input) ?: return
			val parts = requestLine.split(' ')
			if (parts.size < 2) {
				writeSimpleError(out, 400, "Bad Request")
				return
			}
			val method = parts[0].uppercase(Locale.ROOT)
			val reqPath = parts[1].substringBefore('?')

			var rangeHeader: String? = null
			for (i in 0 until 64) {
				val line = readLineAscii(input) ?: break
				if (line.isEmpty()) break
				val colon = line.indexOf(':')
				if (colon > 0) {
					val hName = line.substring(0, colon).trim()
					if (hName.equals("Range", ignoreCase = true)) {
						rangeHeader = line.substring(colon + 1).trim()
					}
				}
			}

			val (token, upstream) = synchronized(lock) { activeToken to activeUpstreamUrl }
			if (token.isBlank() || upstream.isBlank() || !reqPath.startsWith("/cast/$token/")) {
				writeSimpleError(out, 403, "Forbidden")
				return
			}
			if (method != "GET" && method != "HEAD") {
				writeSimpleError(out, 405, "Method Not Allowed")
				return
			}

			val reqBuilder = Request.Builder().url(upstream)
			if (method == "HEAD") reqBuilder.head() else reqBuilder.get()
			if (!rangeHeader.isNullOrBlank()) {
				reqBuilder.header("Range", rangeHeader)
			}

			try {
				streamHttp.newCall(reqBuilder.build()).execute().use { resp ->
					val statusText = when (resp.code) {
						200 -> "OK"
						206 -> "Partial Content"
						else -> resp.message.ifBlank { "Error" }
					}
					val headerSb = StringBuilder()
					headerSb.append("HTTP/1.1 ").append(resp.code).append(' ').append(statusText).append("\r\n")
					val cType = resp.header("Content-Type") ?: guessMimeFromName(reqPath)
					headerSb.append("Content-Type: ").append(cType).append("\r\n")
					resp.header("Content-Length")?.let { headerSb.append("Content-Length: ").append(it).append("\r\n") }
					resp.header("Content-Range")?.let { headerSb.append("Content-Range: ").append(it).append("\r\n") }
					headerSb.append("Accept-Ranges: ").append(resp.header("Accept-Ranges") ?: "bytes").append("\r\n")
					headerSb.append("Connection: close\r\n\r\n")
					out.write(headerSb.toString().toByteArray(Charsets.ISO_8859_1))

					if (method == "GET") {
						val bodyStream = resp.body?.byteStream()
						if (bodyStream != null) {
							val buf = ByteArray(64 * 1024)
							while (true) {
								val n = bodyStream.read(buf)
								if (n <= 0) break
								out.write(buf, 0, n)
							}
						}
					}
					out.flush()
				}
			} catch (_: Exception) {
				runCatching { writeSimpleError(out, 502, "Bad Gateway") }
			}
		}
	}

	private fun readLineAscii(input: BufferedInputStream): String? {
		val sb = StringBuilder()
		while (true) {
			val b = input.read()
			if (b == -1) return if (sb.isEmpty()) null else sb.toString()
			if (b == '\n'.code) break
			if (b != '\r'.code) {
				sb.append(b.toChar())
				if (sb.length > 8192) break
			}
		}
		return sb.toString()
	}

	private fun writeSimpleError(out: BufferedOutputStream, code: Int, reason: String) {
		val resp = "HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
		out.write(resp.toByteArray(Charsets.ISO_8859_1))
		out.flush()
	}

	private fun guessMimeFromName(path: String): String {
		return when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
			"mp4", "m4v" -> "video/mp4"
			"mkv" -> "video/x-matroska"
			"mov" -> "video/quicktime"
			"avi" -> "video/x-msvideo"
			"webm" -> "video/webm"
			"ts" -> "video/mp2t"
			"jpg", "jpeg" -> "image/jpeg"
			"png" -> "image/png"
			"webp" -> "image/webp"
			else -> "application/octet-stream"
		}
	}
}

/**
 * 现代轻量级 DLNA (UPnP AV) 局域网投屏管理器：
 * - 多网卡多播 SSDP 发现（显式绑定 Wi-Fi/以太网 IPv4 接口发送多轮 M-SEARCH，规避 VPN/蜂窝抢占路由）
 * - 兼容任意 XML 标签顺序与命名空间的 AVTransport 解析，等待全部描述拉取完成后汇总展示并去重
 * - 内置本地局域网 HTTP 流代理，自动携带 NAS 鉴权与跨网隧道，确保各品牌电视零鉴权壁垒流畅直播
 */
class DlnaManager private constructor(
	private val context: Context,
	private val http: OkHttpClient,
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var searchJob: Job? = null
	private val mediaProxy = DlnaMediaProxy(http)

	private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
	val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

	private val _isSearching = MutableStateFlow(false)
	val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

	private val _activeDevice = MutableStateFlow<DlnaDevice?>(null)
	val activeDevice: StateFlow<DlnaDevice?> = _activeDevice.asStateFlow()

	private val _activeTitle = MutableStateFlow("")
	val activeTitle: StateFlow<String> = _activeTitle.asStateFlow()

	private val _playbackState = MutableStateFlow(DlnaPlaybackState.IDLE)
	val playbackState: StateFlow<DlnaPlaybackState> = _playbackState.asStateFlow()

	private val _statusMessage = MutableStateFlow("")
	val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

	private val soapClient = http.newBuilder()
		.connectTimeout(5, TimeUnit.SECONDS)
		.readTimeout(8, TimeUnit.SECONDS)
		.build()

	/**
	 * 启动局域网 DLNA 设备搜索（多网卡 SSDP 广播）
	 */
	fun searchDevices() {
		searchJob?.cancel()
		searchJob = scope.launch {
			_isSearching.value = true
			_statusMessage.value = "正在扫描局域网电视设备…"
			val discoveredByKey = LinkedHashMap<String, DlnaDevice>()
			val seenLocations = HashSet<String>()
			val parseJobs = java.util.Collections.synchronizedList(mutableListOf<Job>())

			_activeDevice.value?.let { current ->
				val dedupKey = current.controlUrl.ifBlank { current.id }
				discoveredByKey[dedupKey] = current
				_devices.value = listOf(current)
			}

			val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
			val multicastLock = runCatching {
				wifiManager?.createMulticastLock("nasdrop_dlna_discovery")?.apply {
					setReferenceCounted(true)
					acquire()
				}
			}.getOrNull()

			val lanIfaces = listLanMulticastInterfaces()
			val socketsToClose = java.util.Collections.synchronizedList(mutableListOf<DatagramSocket>())
			runCatching {
				Log.i("DlnaManager", "SSDP search start, lanInterfaces=${lanIfaces.map { "${it.name}:${it.address.hostAddress}" }}")
			}

			try {
				val ssdpTarget = InetAddress.getByName("239.255.255.250")
				val ssdpPort = 1900
				val queries = listOf(
					"urn:schemas-upnp-org:device:MediaRenderer:1",
					"urn:schemas-upnp-org:service:AVTransport:1",
					"urn:schemas-upnp-org:service:AVTransport:2",
					"ssdp:all",
					"upnp:rootdevice",
				)

				coroutineScope {
					val targetNics: List<LanInterfaceInfo?> = if (lanIfaces.isNotEmpty()) lanIfaces else listOf(null)
					for (nic in targetNics) {
						launch {
							val socket: DatagramSocket = try {
								if (nic != null) {
									MulticastSocket(InetSocketAddress(nic.address, 0)).apply {
										networkInterface = nic.networkInterface
										timeToLive = 4
										soTimeout = 700
									}
								} else {
									DatagramSocket().apply { soTimeout = 700 }
								}
							} catch (e: Exception) {
								runCatching { Log.w("DlnaManager", "bind socket failed nic=${nic?.name}", e) }
								return@launch
							}
							socketsToClose.add(socket)

							val sender = launch {
								repeat(3) { round ->
									for (st in queries) {
										val mSearchMsg = "M-SEARCH * HTTP/1.1\r\n" +
											"HOST: 239.255.255.250:1900\r\n" +
											"MAN: \"ssdp:discover\"\r\n" +
											"MX: 3\r\n" +
											"ST: $st\r\n\r\n"
										val bytes = mSearchMsg.toByteArray(Charsets.UTF_8)
										runCatching {
											socket.send(DatagramPacket(bytes, bytes.size, ssdpTarget, ssdpPort))
										}
									}
									if (round < 2) delay(550)
								}
							}

							val startTime = System.currentTimeMillis()
							val receiveBuf = ByteArray(8192)
							while (System.currentTimeMillis() - startTime < 3500) {
								try {
									val recvPacket = DatagramPacket(receiveBuf, receiveBuf.size)
									socket.receive(recvPacket)
									val responseStr = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8)
									val location = extractSsdpHeader(responseStr, "LOCATION")
									val usn = extractSsdpHeader(responseStr, "USN")
									if (!location.isNullOrBlank()) {
										val shouldFetch = synchronized(seenLocations) { seenLocations.add(location) }
										if (shouldFetch) {
											val deviceId = usn?.substringBefore("::") ?: location
											val job = launch {
												parseDeviceDescription(location, deviceId)?.let { device ->
													val dedupKey = device.controlUrl.ifBlank { device.id }
													synchronized(discoveredByKey) {
														if (!discoveredByKey.containsKey(dedupKey)) {
															discoveredByKey[dedupKey] = device
															_devices.value = discoveredByKey.values.toList()
														}
													}
												}
											}
											parseJobs.add(job)
										}
									}
								} catch (_: SocketTimeoutException) {
									// 超时继续接收直至窗口结束
								} catch (_: Exception) {
									break
								}
							}
							sender.cancel()
						}
					}
				}

				// 等待所有已收到的描述 XML 请求与解析完成后再发布最终结果，避免抢先判空
				val snapshotJobs = synchronized(parseJobs) { parseJobs.toList() }
				snapshotJobs.joinAll()
			} catch (e: Exception) {
				_statusMessage.value = "搜索异常: ${e.message}"
			} finally {
				synchronized(socketsToClose) {
					socketsToClose.forEach { runCatching { it.close() } }
				}
				runCatching {
					if (multicastLock?.isHeld == true) multicastLock.release()
				}
				val finalList = synchronized(discoveredByKey) { discoveredByKey.values.toList() }
				_devices.value = finalList
				_isSearching.value = false
				_statusMessage.value = if (finalList.isEmpty()) {
					"未发现投屏设备，请确认手机与电视在同一 Wi-Fi 并关闭 AP 隔离"
				} else {
					"已发现 ${finalList.size} 台可投屏设备"
				}
			}
		}
	}

	private suspend fun parseDeviceDescription(location: String, deviceId: String): DlnaDevice? = withContext(Dispatchers.IO) {
		try {
			val req = Request.Builder().url(location).get().build()
			soapClient.newCall(req).execute().use { res ->
				if (!res.isSuccessful) return@withContext null
				val xml = res.body?.string() ?: return@withContext null
				parseUpnpDeviceDescription(xml, location, deviceId)
			}
		} catch (e: Exception) {
			runCatching { Log.d("DlnaManager", "parseDeviceDescription failed location=$location", e) }
			null
		}
	}

	/**
	 * 执行 DLNA 投屏：自动经由手机局域网 HTTP 代理暴露直链，彻底解决服务端鉴权/隧道地址电视不可达问题
	 */
	suspend fun cast(device: DlnaDevice, videoUrl: String, title: String): Result<Unit> = withContext(Dispatchers.IO) {
		try {
			_playbackState.value = DlnaPlaybackState.CONNECTING
			_activeDevice.value = device
			_activeTitle.value = title
			_statusMessage.value = "正在连接 ${device.name}…"

			val lanIps = listLanMulticastInterfaces().mapNotNull { it.address.hostAddress }
			val advertiseIp = chooseLanIpForTarget(device.host, lanIps)
			val castUrl = if (!advertiseIp.isNullOrBlank()) {
				mediaProxy.expose(videoUrl, title, advertiseIp)
			} else {
				videoUrl
			}

			// 1. 尝试停止上一个播放会话
			runCatching {
				postSoap(device.controlUrl, "Stop", makeStopXml()).use { }
			}

			// 2. 发送 SetAVTransportURI
			val setUriXml = makeSetUriXml(castUrl, title)
			val (uriOk, uriCode, uriMsg) = postSoap(device.controlUrl, "SetAVTransportURI", setUriXml).use { res ->
				Triple(res.isSuccessful, res.code, res.message)
			}
			if (!uriOk) {
				val err = "电视未能接收直链 ($uriCode): $uriMsg"
				_statusMessage.value = err
				_playbackState.value = DlnaPlaybackState.ERROR
				return@withContext Result.failure(Exception(err))
			}

			// 3. 发送 Play
			val (playOk, playCode, playMsg) = postSoap(device.controlUrl, "Play", makePlayXml()).use { res ->
				Triple(res.isSuccessful, res.code, res.message)
			}
			if (playOk) {
				_playbackState.value = DlnaPlaybackState.PLAYING
				_statusMessage.value = "正在 ${device.name} 上投屏播放"
				Result.success(Unit)
			} else {
				val err = "投屏播放失败 ($playCode): $playMsg"
				_statusMessage.value = err
				_playbackState.value = DlnaPlaybackState.ERROR
				Result.failure(Exception(err))
			}
		} catch (e: Exception) {
			val msg = "投屏连接失败: ${e.message}"
			_statusMessage.value = msg
			_playbackState.value = DlnaPlaybackState.ERROR
			Result.failure(e)
		}
	}

	suspend fun pause(): Result<Unit> = withContext(Dispatchers.IO) {
		val device = _activeDevice.value ?: return@withContext Result.failure(Exception("未连接设备"))
		try {
			postSoap(device.controlUrl, "Pause", makePauseXml()).use { res ->
				if (res.isSuccessful) {
					_playbackState.value = DlnaPlaybackState.PAUSED
					Result.success(Unit)
				} else {
					Result.failure(Exception("暂停失败 (${res.code})"))
				}
			}
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	suspend fun resume(): Result<Unit> = withContext(Dispatchers.IO) {
		val device = _activeDevice.value ?: return@withContext Result.failure(Exception("未连接设备"))
		try {
			postSoap(device.controlUrl, "Play", makePlayXml()).use { res ->
				if (res.isSuccessful) {
					_playbackState.value = DlnaPlaybackState.PLAYING
					Result.success(Unit)
				} else {
					Result.failure(Exception("恢复播放失败 (${res.code})"))
				}
			}
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
		val device = _activeDevice.value ?: return@withContext Result.failure(Exception("未连接设备"))
		try {
			postSoap(device.controlUrl, "Stop", makeStopXml()).use { }
			_playbackState.value = DlnaPlaybackState.IDLE
			_activeDevice.value = null
			_statusMessage.value = "已停止投屏"
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	private fun postSoap(controlUrl: String, action: String, xmlBody: String): Response {
		val body = xmlBody.toRequestBody("text/xml; charset=utf-8".toMediaType())
		val req = Request.Builder()
			.url(controlUrl)
			.addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
			.post(body)
			.build()
		return soapClient.newCall(req).execute()
	}

	private fun makeSetUriXml(url: String, title: String): String {
		val safeUrl = escapeXml(url)
		val safeTitle = escapeXml(title)
		val mime = when (title.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
			"mp4", "m4v" -> "video/mp4"
			"mkv" -> "video/x-matroska"
			"mov" -> "video/quicktime"
			"avi" -> "video/x-msvideo"
			"webm" -> "video/webm"
			else -> "video/*"
		}
		val didl = "&lt;DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"&gt;" +
			"&lt;item id=\"0\" parentID=\"-1\" restricted=\"1\"&gt;" +
			"&lt;dc:title&gt;$safeTitle&lt;/dc:title&gt;" +
			"&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;" +
			"&lt;res protocolInfo=\"http-get:*:$mime:*\"&gt;$safeUrl&lt;/res&gt;" +
			"&lt;/item&gt;&lt;/DIDL-Lite&gt;"

		return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <CurrentURI>$safeUrl</CurrentURI>
      <CurrentURIMetaData>$didl</CurrentURIMetaData>
    </u:SetAVTransportURI>
  </s:Body>
</s:Envelope>"""
	}

	private fun makePlayXml(): String {
		return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <Speed>1</Speed>
    </u:Play>
  </s:Body>
</s:Envelope>"""
	}

	private fun makePauseXml(): String {
		return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
    </u:Pause>
  </s:Body>
</s:Envelope>"""
	}

	private fun makeStopXml(): String {
		return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
    </u:Stop>
  </s:Body>
</s:Envelope>"""
	}

	private fun escapeXml(str: String): String {
		return str.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&apos;")
	}

	companion object {
		@Volatile
		private var instance: DlnaManager? = null

		fun getInstance(context: Context): DlnaManager =
			instance ?: synchronized(this) {
				instance ?: DlnaManager(
					context.applicationContext,
					context.app.repo.http()
				).also { instance = it }
			}
	}
}
