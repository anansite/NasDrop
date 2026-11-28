package com.nasdrop

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class DlnaManagerTest {

	@Test
	fun parseUpnpDeviceDescriptionHandlesUnorderedTagsNamespacesAndRelativeUrls() {
		// 测试标签顺序颠倒（controlURL 在 serviceType 前）+ XML 命名空间 + 子目录相对路径
		val xml = """
			<?xml version="1.0"?>
			<root xmlns="urn:schemas-upnp-org:device-1-0">
			  <device>
			    <friendlyName>客厅小米电视</friendlyName>
			    <serviceList>
			      <ns0:service xmlns:ns0="urn:schemas-upnp-org:device-1-0">
			        <ns0:controlURL>AVTransport/control</ns0:controlURL>
			        <ns0:serviceId>urn:upnp-org:serviceId:AVTransport</ns0:serviceId>
			        <ns0:serviceType>urn:schemas-upnp-org:service:AVTransport:2</ns0:serviceType>
			      </ns0:service>
			    </serviceList>
			  </device>
			</root>
		""".trimIndent()

		val device = parseUpnpDeviceDescription(
			xml = xml,
			location = "http://192.168.31.88:49152/upnp/desc.xml",
			deviceId = "uuid:mi-tv-1",
		)
		assertNotNull(device)
		assertEquals("客厅小米电视", device!!.name)
		assertEquals("http://192.168.31.88:49152/upnp/AVTransport/control", device.controlUrl)
		assertEquals("192.168.31.88", device.host)
	}

	@Test
	fun parseUpnpDeviceDescriptionUsesNestedDeviceFriendlyNameAndUrlBase() {
		val xml = """
			<root>
			  <URLBase>http://192.168.1.50:50000/</URLBase>
			  <device>
			    <friendlyName>RootGateway</friendlyName>
			    <deviceList>
			      <device>
			        <friendlyName>索尼电视 BRAVIA</friendlyName>
			        <serviceList>
			          <service>
			            <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
			            <controlURL>/ctrl/render</controlURL>
			          </service>
			          <service>
			            <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
			            <controlURL>/ctrl/avt</controlURL>
			          </service>
			        </serviceList>
			      </device>
			    </deviceList>
			  </device>
			</root>
		""".trimIndent()

		val device = parseUpnpDeviceDescription(xml, "http://192.168.1.50:50000/desc.xml", "uuid:sony")
		assertNotNull(device)
		assertEquals("索尼电视 BRAVIA", device!!.name)
		assertEquals("http://192.168.1.50:50000/ctrl/avt", device.controlUrl)
	}

	@Test
	fun parseUpnpDeviceDescriptionRejectsNonRendererDevice() {
		val routerXml = """
			<root>
			  <device>
			    <friendlyName>Home Router</friendlyName>
			    <serviceList>
			      <service>
			        <serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
			        <controlURL>/ipc</controlURL>
			      </service>
			    </serviceList>
			  </device>
			</root>
		""".trimIndent()
		assertNull(parseUpnpDeviceDescription(routerXml, "http://192.168.1.1:1900/igd.xml", "uuid:router"))
	}

	@Test
	fun extractSsdpHeaderHandlesCrAndLfEndings() {
		val respLf = "HTTP/1.1 200 OK\nlocation: http://192.168.1.10:8008/ssdp/desc.xml\nusn: uuid:tv1\n\n"
		assertEquals("http://192.168.1.10:8008/ssdp/desc.xml", extractSsdpHeader(respLf, "LOCATION"))
		assertEquals("uuid:tv1", extractSsdpHeader(respLf, "USN"))
	}

	@Test
	fun chooseLanIpForTargetPrefersSameSubnet() {
		val chosen = chooseLanIpForTarget(
			targetHost = "192.168.31.120",
			candidateIps = listOf("10.0.2.15", "192.168.31.42", "172.17.0.1"),
		)
		assertEquals("192.168.31.42", chosen)
	}

	@Test
	fun dlnaMediaProxyForwardsBasicAuthAndByteRangesToTvWithoutAuth() {
		val server = MockWebServer()
		val expectedAuth = Credentials.basic("admin", "secret")
		val payload = "0123456789ABCDEF"
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse {
				if (request.getHeader("Authorization") != expectedAuth) {
					return MockResponse().setResponseCode(401)
				}
				val range = request.getHeader("Range")
				return if (range == "bytes=2-5") {
					MockResponse()
						.setResponseCode(206)
						.setHeader("Content-Type", "video/mp4")
						.setHeader("Content-Range", "bytes 2-5/${payload.length}")
						.setHeader("Content-Length", "4")
						.setBody("2345")
				} else {
					MockResponse()
						.setResponseCode(200)
						.setHeader("Content-Type", "video/mp4")
						.setHeader("Content-Length", "${payload.length}")
						.setBody(payload)
				}
			}
		}
		server.start()

		val upstreamClient = OkHttpClient.Builder()
			.addInterceptor { chain ->
				chain.proceed(chain.request().newBuilder().header("Authorization", expectedAuth).build())
			}
			.build()
		val proxy = DlnaMediaProxy(upstreamClient)
		try {
			val upstreamUrl = server.url("/movies/family.mp4").toString()
			val proxyUrl = proxy.expose(upstreamUrl, "family.mp4", "127.0.0.1")
			val uri = java.net.URI.create(proxyUrl)

			Socket("127.0.0.1", uri.port).use { sock ->
				val writer = sock.getOutputStream()
				writer.write("GET ${uri.rawPath} HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=2-5\r\n\r\n".toByteArray())
				writer.flush()

				val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
				val statusLine = reader.readLine()
				assertEquals("HTTP/1.1 206 Partial Content", statusLine)
				var headerLine: String?
				while (reader.readLine().also { headerLine = it } != null) {
					if (headerLine!!.isEmpty()) break
				}
				val body = reader.readLine()
				assertEquals("2345", body)
			}
		} finally {
			proxy.close()
			server.shutdown()
		}
	}

	@Test
	fun setUriXmlGeneratesAudioAndVideoDidlMetadataForCommonFormats() {
		val m4aXml = DlnaManager.makeSetUriXml("http://192.168.1.10:8080/m/story.m4a", "爸爸的高台舞狮.m4a")
		assertTrue(m4aXml.contains("object.item.audioItem.musicTrack"))
		assertTrue(m4aXml.contains("http-get:*:audio/mp4:*"))

		val flacXml = DlnaManager.makeSetUriXml("http://192.168.1.10:8080/m/lossless.flac", "lossless.flac")
		assertTrue(flacXml.contains("object.item.audioItem.musicTrack"))
		assertTrue(flacXml.contains("http-get:*:audio/flac:*"))

		val mp4Xml = DlnaManager.makeSetUriXml("http://192.168.1.10:8080/m/clip.mp4", "clip.mp4")
		assertTrue(mp4Xml.contains("object.item.videoItem"))
		assertTrue(mp4Xml.contains("http-get:*:video/mp4:*"))
	}
}
