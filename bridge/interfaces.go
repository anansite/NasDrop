package bridge

// Android 网卡枚举的绕行方案。
//
// 背景：Android SDK 30 起禁止 app UID（untrusted_app）使用 NETLINK_ROUTE 套接字
// 和 /proc/net，而 Go 标准库的 net.Interfaces() 在 linux/android 上一律走 netlink，
// 于是 netmon 起不来，tsnet.Up 直接报：
//
//	tsnet: route ip+net: netlinkrib: permission denied
//
// 官方 Android App 的解法是从 Java 侧取网卡清单，再通过 netmon.RegisterInterfaceGetter
// 灌给 Go（Java 的 NetworkInterface.getNetworkInterfaces() 是允许的）。
//
// 官方也为「裸二进制」准备了 tailscale.com/feature/androidbin，但它的注册文件
// register.go 的构建标签是：
//
//	//go:build (linux && !android) || (android && !cgo)
//
// 而 gomobile bind 生产的正是 android + cgo，两个条件都不满足 —— 那个包的 init
// 根本不会跑（表现为 .so 里连一条 androidbin 符号都没有）。所以这里自己注册。
//
// 两个数据来源，按优先级：
//  1. Kotlin 侧推送的真实网卡快照（名称/序号/MTU/标志/地址都准确，首选）
//  2. 还没推送时用合成接口兜底：向 8.8.8.8 拨一个 UDP socket 让内核选路，
//     读出本机源地址（只选路不发包，App 沙箱允许）

import (
	"encoding/json"
	"errors"
	"net"
	"net/netip"
	"sync"
	"time"

	"tailscale.com/net/netmon"
)

// ifaceSnapshot 是 Kotlin 侧推送的 JSON 结构，一次一份完整快照。
type ifaceSnapshot struct {
	Name  string   `json:"name"`
	Index int      `json:"index"`
	MTU   int      `json:"mtu"`
	Flags []string `json:"flags"`
	Addrs []string `json:"addrs"` // CIDR，例如 192.168.1.5/24
}

var (
	ifMu    sync.RWMutex
	ifList  []netmon.Interface
	ifReady bool
)

func init() {
	// 必须早于任何 netmon 使用；这里注册后，netmon 不会再走 net.Interfaces。
	netmon.RegisterInterfaceGetter(currentInterfaces)
}

func currentInterfaces() ([]netmon.Interface, error) {
	ifMu.RLock()
	list, ready := ifList, ifReady
	ifMu.RUnlock()

	// 注意：这里不能返回错误。netmon 的 netInterfaces() 在 getter 返回错误时
	// 会直接把错误抛给调用方（不会再走别的回退），netmon 就起不来了。
	if ready && len(list) > 0 {
		return list, nil
	}
	return syntheticInterfaces()
}

// SetInterfaces 由 Kotlin 侧推送真实网卡清单（JSON 数组）。可重复调用，
// 网络切换时重新推一次即可，netmon 下次取值就会用新的。
func SetInterfaces(snapshotJSON string) error {
	if snapshotJSON == "" {
		return errors.New("空的网卡清单")
	}
	var raw []ifaceSnapshot
	if err := json.Unmarshal([]byte(snapshotJSON), &raw); err != nil {
		return err
	}

	out := make([]netmon.Interface, 0, len(raw))
	for _, r := range raw {
		if r.Name == "" {
			continue
		}
		ni := &net.Interface{
			Index: r.Index,
			MTU:   r.MTU,
			Name:  r.Name,
			Flags: parseFlags(r.Flags),
		}
		addrs := make([]net.Addr, 0, len(r.Addrs))
		for _, cidr := range r.Addrs {
			ip, ipnet, err := net.ParseCIDR(cidr)
			if err != nil || ipnet == nil {
				continue
			}
			ipnet.IP = ip
			addrs = append(addrs, ipnet)
		}
		out = append(out, netmon.Interface{Interface: ni, AltAddrs: addrs})
	}
	if len(out) == 0 {
		return errors.New("网卡清单解析后为空")
	}

	ifMu.Lock()
	ifList, ifReady = out, true
	ifMu.Unlock()

	logf("收到 Java 网卡清单：%d 个网卡", len(out))
	return nil
}

func parseFlags(names []string) net.Flags {
	var f net.Flags
	for _, n := range names {
		switch n {
		case "up":
			f |= net.FlagUp
		case "broadcast":
			f |= net.FlagBroadcast
		case "loopback":
			f |= net.FlagLoopback
		case "pointtopoint":
			f |= net.FlagPointToPoint
		case "multicast":
			f |= net.FlagMulticast
		case "running":
			f |= net.FlagRunning
		}
	}
	return f
}

// syntheticInterfaces 在 Java 还没推快照时兜底：报告一个合成网卡，
// 地址用「让内核为一条出站 UDP 路由选源地址」的方式得到。
func syntheticInterfaces() ([]netmon.Interface, error) {
	var addrs []net.Addr
	if ip, ok := outboundIP("udp4", "8.8.8.8:53"); ok {
		addrs = append(addrs, &net.IPNet{IP: ip.AsSlice(), Mask: net.CIDRMask(32, 32)})
	}
	if ip, ok := outboundIP("udp6", "[2001:4860:4860::8888]:53"); ok {
		addrs = append(addrs, &net.IPNet{IP: ip.AsSlice(), Mask: net.CIDRMask(128, 128)})
	}
	if len(addrs) == 0 {
		return nil, errors.New("androidbridge: 没有可用的出站地址")
	}
	return []netmon.Interface{{
		Interface: &net.Interface{
			MTU:   1500,
			Name:  "android",
			Flags: net.FlagUp | net.FlagRunning,
		},
		AltAddrs: addrs,
	}}, nil
}

func outboundIP(network, addr string) (netip.Addr, bool) {
	d := net.Dialer{Timeout: 2 * time.Second}
	c, err := d.Dial(network, addr)
	if err != nil {
		return netip.Addr{}, false
	}
	defer c.Close()
	ua, ok := c.LocalAddr().(*net.UDPAddr)
	if !ok {
		return netip.Addr{}, false
	}
	ip, ok := netip.AddrFromSlice(ua.IP)
	if !ok {
		return netip.Addr{}, false
	}
	ip = ip.Unmap()
	if ip.IsLoopback() || ip.IsUnspecified() {
		return netip.Addr{}, false
	}
	return ip, true
}
