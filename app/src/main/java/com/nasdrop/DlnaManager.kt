package com.nasdrop

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.util.Locale
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

/**
 * 现代轻量级 DLNA (UPnP AV) 局域网投屏管理器：
 * - 基于 SSDP 协议（UDP 239.255.255.250:1900）无感知发现局域网智能电视、盒子、投影仪（如小米、索尼、华为、当贝等）
 * - 自动解析 UPnP 设备描述文档，提取 AVTransport 控制端点
 * - 通过标准 SOAP 协议发送 SetAVTransportURI、Play、Pause、Stop 投屏控制指令
 * - 内置 DIDL-Lite 元数据封装，确保各品牌大屏设备完美读取视频标题与原画流
 */
class DlnaManager private constructor(
	private val context: Context,
	private val http: OkHttpClient,
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var searchJob: Job? = null

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
	 * 启动局域网 DLNA 设备搜索（SSDP 广播）
	 */
	fun searchDevices() {
		searchJob?.cancel()
		searchJob = scope.launch {
			_isSearching.value = true
			_statusMessage.value = "正在扫描局域网电视设备…"
			val discoveredMap = LinkedHashMap<String, DlnaDevice>()

			// 保留当前已连接设备
			_activeDevice.value?.let { current ->
				discoveredMap[current.id] = current
			}

			val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
			val multicastLock = wifiManager?.createMulticastLock("nasdrop_dlna_discovery")?.apply {
				setReferenceCounted(true)
			}

			var socket: DatagramSocket? = null
			try {
				multicastLock?.acquire()
				socket = DatagramSocket()
				socket.soTimeout = 1000

				val ssdpTarget = InetAddress.getByName("239.255.255.250")
				val ssdpPort = 1900

				// 发送针对 AVTransport 及通用 MediaRenderer 的 M-SEARCH 探测包
				val queries = listOf(
					"urn:schemas-upnp-org:service:AVTransport:1",
					"urn:schemas-upnp-org:device:MediaRenderer:1",
					"ssdp:all",
				)

				for (st in queries) {
					val mSearchMsg = "M-SEARCH * HTTP/1.1\r\n" +
						"HOST: 239.255.255.250:1900\r\n" +
						"MAN: \"ssdp:discover\"\r\n" +
						"MX: 3\r\n" +
						"ST: $st\r\n\r\n"
					val sendPacket = DatagramPacket(mSearchMsg.toByteArray(), mSearchMsg.length, ssdpTarget, ssdpPort)
					socket.send(sendPacket)
				}

				val startTime = System.currentTimeMillis()
				val receiveBuf = ByteArray(8192)

				while (System.currentTimeMillis() - startTime < 3500) {
					try {
						val recvPacket = DatagramPacket(receiveBuf, receiveBuf.size)
						socket.receive(recvPacket)
						val responseStr = String(recvPacket.data, 0, recvPacket.length)
						val location = extractHeader(responseStr, "LOCATION")
						val usn = extractHeader(responseStr, "USN")

						if (!location.isNullOrBlank()) {
							val deviceId = usn ?: location
							if (!discoveredMap.containsKey(deviceId)) {
								// 异步解析设备描述
								launch {
									parseDeviceDescription(location, deviceId)?.let { device ->
										synchronized(discoveredMap) {
											discoveredMap[device.id] = device
											_devices.value = discoveredMap.values.toList()
										}
									}
								}
							}
						}
					} catch (e: java.net.SocketTimeoutException) {
						// 循环超时继续等待直至 3.5 秒
					} catch (e: Exception) {
						break
					}
				}
			} catch (e: Exception) {
				_statusMessage.value = "搜索异常: ${e.message}"
			} finally {
				socket?.close()
				try {
					if (multicastLock?.isHeld == true) multicastLock.release()
				} catch (e: Exception) {}
				_isSearching.value = false
				_devices.value = discoveredMap.values.toList()
				if (discoveredMap.isEmpty()) {
					_statusMessage.value = "未发现投屏设备，请确认手机与电视在同一 Wi-Fi"
				} else {
					_statusMessage.value = "已发现 ${discoveredMap.size} 台可投屏设备"
				}
			}
		}
	}

	private suspend fun parseDeviceDescription(location: String, deviceId: String): DlnaDevice? = withContext(Dispatchers.IO) {
		try {
			val req = Request.Builder().url(location).get().build()
			val res = soapClient.newCall(req).execute()
			if (!res.isSuccessful) return@withContext null
			val xml = res.body?.string() ?: return@withContext null

			// 解析 friendlyName
			val nameMatch = Regex("<friendlyName>\\s*(.*?)\\s*</friendlyName>", RegexOption.IGNORE_CASE).find(xml)
			val name = nameMatch?.groupValues?.get(1)?.trim() ?: "智能电视"

			// 解析 AVTransport controlURL
			val avTransportBlockRegex = Regex(
				"<service>[\\s\\S]*?<serviceType>\\s*urn:schemas-upnp-org:service:AVTransport:1\\s*</serviceType>[\\s\\S]*?<controlURL>\\s*(.*?)\\s*</controlURL>[\\s\\S]*?</service>",
				RegexOption.IGNORE_CASE
			)
			val match = avTransportBlockRegex.find(xml) ?: return@withContext null
			val rawControlUrl = match.groupValues[1].trim()

			val uri = URI.create(location)
			val host = uri.host ?: ""
			val resolvedControlUrl = if (rawControlUrl.startsWith("http://", ignoreCase = true) || rawControlUrl.startsWith("https://", ignoreCase = true)) {
				rawControlUrl
			} else {
				val portPart = if (uri.port != -1) ":${uri.port}" else ""
				val base = "${uri.scheme}://${uri.host}$portPart"
				if (rawControlUrl.startsWith("/")) "$base$rawControlUrl" else "$base/$rawControlUrl"
			}

			DlnaDevice(
				id = deviceId,
				name = name,
				controlUrl = resolvedControlUrl,
				location = location,
				host = host,
			)
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * 执行 DLNA 投屏
	 */
	suspend fun cast(device: DlnaDevice, videoUrl: String, title: String): Result<Unit> = withContext(Dispatchers.IO) {
		try {
			_playbackState.value = DlnaPlaybackState.CONNECTING
			_activeDevice.value = device
			_activeTitle.value = title
			_statusMessage.value = "正在连接 ${device.name}…"

			// 1. 尝试停止上一个播放会话
			runCatching {
				postSoap(device.controlUrl, "Stop", makeStopXml())
			}

			// 2. 发送 SetAVTransportURI
			val setUriXml = makeSetUriXml(videoUrl, title)
			val setUriRes = postSoap(device.controlUrl, "SetAVTransportURI", setUriXml)
			if (!setUriRes.isSuccessful) {
				val err = "电视未能接收直链 (${setUriRes.code}): ${setUriRes.message}"
				_statusMessage.value = err
				_playbackState.value = DlnaPlaybackState.ERROR
				return@withContext Result.failure(Exception(err))
			}

			// 3. 发送 Play
			val playXml = makePlayXml()
			val playRes = postSoap(device.controlUrl, "Play", playXml)
			if (playRes.isSuccessful) {
				_playbackState.value = DlnaPlaybackState.PLAYING
				_statusMessage.value = "正在 ${device.name} 上投屏播放"
				Result.success(Unit)
			} else {
				val err = "投屏播放失败 (${playRes.code}): ${playRes.message}"
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
			val res = postSoap(device.controlUrl, "Pause", makePauseXml())
			if (res.isSuccessful) {
				_playbackState.value = DlnaPlaybackState.PAUSED
				Result.success(Unit)
			} else {
				Result.failure(Exception("暂停失败"))
			}
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	suspend fun resume(): Result<Unit> = withContext(Dispatchers.IO) {
		val device = _activeDevice.value ?: return@withContext Result.failure(Exception("未连接设备"))
		try {
			val res = postSoap(device.controlUrl, "Play", makePlayXml())
			if (res.isSuccessful) {
				_playbackState.value = DlnaPlaybackState.PLAYING
				Result.success(Unit)
			} else {
				Result.failure(Exception("恢复播放失败"))
			}
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
		val device = _activeDevice.value ?: return@withContext Result.failure(Exception("未连接设备"))
		try {
			postSoap(device.controlUrl, "Stop", makeStopXml())
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
		val didl = "&lt;DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"&gt;" +
			"&lt;item id=\"0\" parentID=\"-1\" restricted=\"1\"&gt;" +
			"&lt;dc:title&gt;$safeTitle&lt;/dc:title&gt;" +
			"&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;" +
			"&lt;res protocolInfo=\"http-get:*:video/*:*\"&gt;$safeUrl&lt;/res&gt;" +
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

	private fun extractHeader(response: String, name: String): String? {
		for (line in response.split("\r\n")) {
			val idx = line.indexOf(':')
			if (idx > 0) {
				val headerName = line.substring(0, idx).trim()
				if (headerName.equals(name, ignoreCase = true)) {
					return line.substring(idx + 1).trim()
				}
			}
		}
		return null
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
