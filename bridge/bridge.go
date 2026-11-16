// Package bridge 是 Kotlin 与 Tailscale 之间唯一的接触面。
//
// 职责边界：
//   - 起一个 tsnet 用户态节点（不开 TUN、不需要 VpnService、不占系统 VPN 槽位）
//   - 在 127.0.0.1 上开一个反向代理，把请求经由 tsnet 送到目标 WebDAV 服务器
//   - 通过 gomobile 暴露 4 个函数给 Kotlin，Kotlin 侧只看到一个本地端口
//
// 这样设计的目的是把 Tailscale 的复杂度完全关在 Go 里：
// 升级 Tailscale 只需 `go get tailscale.com@latest && ./build-aar.sh`，
// 上面的业务代码（WebDAV、UI、播放器）一行都不用动。
package bridge

import (
	"context"
	"crypto/rand"
	"crypto/tls"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	// 这一行是 Android 上能跑起来的前提，缺了它 tsnet.Up 会直接失败：
	//
	//	tsnet: route ip+net: netlinkrib: permission denied
	//
	// 原因是 Android SDK 30 起对 app UID（untrusted_app）关掉了 NETLINK_ROUTE 套接字
	// 和 /proc/net，而 Go 标准库的 net.Interfaces() 在 linux/android 上一律走 netlink，
	// 于是 netmon 无法启动。官方 Android App 靠 Java 侧 RegisterInterfaceGetter 绕过；
	// 我们这种「AAR 嵌进 App、Go 侧没有 Java 可依赖」的形态，官方留的口子就是这个包：
	// 它注册一个 netmon 回退（合成一个 android 接口，地址靠让内核给一条出站 UDP 路由
	// 反查源地址，不发包），顺带注入 androiddns（Android 没有 /etc/resolv.conf）
	// 并把 CA 根证书指到 /system/etc/security/cacerts。
	// 官方包文档原话：It is not linked into tsnet by default; tsnet apps opt in
	// with a blank import of this package.
	_ "tailscale.com/feature/androidbin"

	"tailscale.com/tsnet"
)

const (
	maxLogLines = 300
	upTimeout   = 5 * time.Minute
)

var (
	mu     sync.Mutex
	srv    *tsnet.Server
	ln     net.Listener
	hs     *http.Server
	port   int
	token  string
	upErr  error
	target string
	logBuf []string

	// 上次 Start 的参数，供 Retry 复用
	lastDir, lastHost, lastKey, lastTarget string

	// 是否放行自签/私有 CA 证书。必须在 Start 之前设置。
	insecureTLS bool
)

// prepareRuntimeDirs 给 Go 运行时和 logpolicy 准备一个可写的家目录。
//
// Android 上既没有 HOME 也没有 TMPDIR，而 Tailscale 的日志/状态代码会一路回退到
// 「当前目录（/，被排除）→ 临时目录（不存在）」，最后直接 panic
// （no safe place to store log state）。在 Up 之前把这些环境变量指到 App 私有目录即可。
func prepareRuntimeDirs(stateDir string) {
	dirs := map[string]string{
		"HOME":            stateDir,
		"TMPDIR":          filepath.Join(stateDir, "tmp"),
		"XDG_CACHE_HOME":  filepath.Join(stateDir, "cache"),
		"XDG_CONFIG_HOME": filepath.Join(stateDir, "config"),
	}
	for k, v := range dirs {
		_ = os.MkdirAll(v, 0o700)
		_ = os.Setenv(k, v)
	}
}

// SetInsecureTLS 控制下游 TLS 校验，必须在 Start 之前调用。
//
// 适用场景：局域网 NAS 用的是自签证书，链在 Android 系统信任库里找不到，
// 而这段流量本来就已经跑在 WireGuard（tsnet）加密隧道里，且对端是自己 tailnet 内的设备。
// 拿到正规证书后应立刻关掉。
func SetInsecureTLS(enable bool) {
	mu.Lock()
	insecureTLS = enable
	mu.Unlock()
}

// logf 既喂给 tsnet 的 Logf，也留一份环形缓冲给 App 的“诊断日志”页面。
func logf(format string, args ...interface{}) {
	mu.Lock()
	defer mu.Unlock()
	if len(logBuf) >= maxLogLines {
		logBuf = append(logBuf[:0], logBuf[len(logBuf)-maxLogLines/2:]...)
	}
	logBuf = append(logBuf, time.Now().Format("15:04:05")+" "+fmt.Sprintf(format, args...))
}

func newToken() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// Start 启动 tsnet 节点和本地代理，立刻返回本地端口（不阻塞等待登录）。
//
// 参数：
//
//	stateDir  - 节点状态目录，建议 App 私有目录下的 files/tsnet（务必持久化，
//	            否则每次冷启动都是新设备，需要重新登录）
//	hostname  - 在 tailnet 管理后台里显示的设备名，例如 "nasdrop-android"
//	authKey   - 可选。填了就零交互上线（推荐用 tag 化的 reusable key）；
//	            留空则走交互式登录，Kotlin 侧轮询 AuthURL() 打开浏览器
//	targetURL - WebDAV 根地址，例如 https://nas.205002.xyz
func Start(stateDir, hostname, authKey, targetURL string) (int32, error) {
	u, err := url.Parse(targetURL)
	if err != nil || u.Host == "" {
		return 0, fmt.Errorf("非法的目标地址: %q", targetURL)
	}

	// 必须在 tsnet 起来之前：给它一个可写的 HOME/TMPDIR，否则日志状态无处可存。
	prepareRuntimeDirs(stateDir)

	mu.Lock()
	if hs != nil {
		p := port
		mu.Unlock()
		return int32(p), nil
	}
	mu.Unlock()

	// 先绑本地端口：这样 Start 可以立刻返回，UI 不用等 Tailscale 起来。
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0, fmt.Errorf("本地端口绑定失败: %w", err)
	}
	p := l.Addr().(*net.TCPAddr).Port

	tk, err := newToken()
	if err != nil {
		l.Close()
		return 0, err
	}

	s := &tsnet.Server{
		Dir:      stateDir,
		Hostname: hostname,
		AuthKey:  authKey,
		Logf:     logf,
	}

	rp := newReverseProxy(u, s)

	mux := http.NewServeMux()
	mux.Handle("/", tokenGuard(tk, rp))
	server := &http.Server{
		Handler:           mux,
		ReadHeaderTimeout: 15 * time.Second,
	}

	mu.Lock()
	srv, ln, hs, port, token, upErr, target = s, l, server, p, tk, nil, targetURL
	lastDir, lastHost, lastKey, lastTarget = stateDir, hostname, authKey, targetURL
	mu.Unlock()

	go func() {
		if err := server.Serve(l); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logf("本地代理退出: %v", err)
		}
	}()

	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), upTimeout)
		defer cancel()
		st, err := s.Up(ctx)
		if err != nil {
			mu.Lock()
			upErr = err
			mu.Unlock()
			if strings.Contains(err.Error(), "netlinkrib") {
				logf("tsnet 上线失败: %v（Android 拒绝了 netlink，检查是否漏了 androidbin 空导入）", err)
			} else {
				logf("tsnet 上线失败: %v", err)
			}
			return
		}
		logf("tsnet 已上线: %v", st.TailscaleIPs)
	}()

	return int32(p), nil
}

// Status 返回一行状态，取值形如：
//
//	idle / starting / needs-login / running:100.x.x.x / error: xxx
func Status() string {
	mu.Lock()
	s, e := srv, upErr
	mu.Unlock()

	if s == nil {
		return "idle"
	}
	if e != nil {
		return "error: " + e.Error()
	}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	cl, err := s.LocalClient()
	if err != nil {
		return "starting"
	}
	st, err := cl.Status(ctx)
	if err != nil {
		return "starting"
	}

	switch st.BackendState {
	case "Running":
		if st.Self != nil && len(st.Self.TailscaleIPs) > 0 {
			return "running:" + st.Self.TailscaleIPs[0].String()
		}
		return "running"
	case "NeedsLogin":
		return "needs-login"
	case "Starting", "NoState":
		return "starting"
	default:
		return strings.ToLower(string(st.BackendState))
	}
}

// AuthURL 返回交互式登录地址；已登录或尚未就绪时返回空串。
// Kotlin 侧在 Status() == "needs-login" 时轮询这个值并打开浏览器。
func AuthURL() string {
	mu.Lock()
	s := srv
	mu.Unlock()
	if s == nil {
		return ""
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	cl, err := s.LocalClient()
	if err != nil {
		return ""
	}
	st, err := cl.Status(ctx)
	if err != nil {
		return ""
	}
	if st.BackendState != "NeedsLogin" {
		return ""
	}
	return st.AuthURL
}

// Token 返回本地代理的访问令牌。只有带着这个头的请求才会被转发，
// 避免同机其他 App 把 127.0.0.1 上的端口当成进入 tailnet 的跳板。
func Token() string {
	mu.Lock()
	defer mu.Unlock()
	return token
}

// Logs 返回最近的日志，供 App 内诊断页展示。
func Logs() string {
	mu.Lock()
	defer mu.Unlock()
	return strings.Join(logBuf, "\n")
}

// Retry 用上次的参数重新拉一次节点，返回新的本地代理端口（参数缺失时返回 0）。
//
// 用途：首次 Up 失败（例如网络还没就绪）后不用杀进程重开 App。
// 注意端口会变，Kotlin 侧要重新拿 baseUrl —— Repository.invalidate() + client() 即为此。
func Retry() int32 {
	mu.Lock()
	d, h, k, t := lastDir, lastHost, lastKey, lastTarget
	mu.Unlock()
	if d == "" || t == "" {
		return 0
	}
	Stop()
	return Start(d, h, k, t)
}

// Stop 关闭代理并下线节点。可重复调用。
func Stop() {
	mu.Lock()
	s, server, l := srv, hs, ln
	srv, hs, ln, port, token = nil, nil, nil, 0, ""
	mu.Unlock()

	if server != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		server.Shutdown(ctx)
		cancel()
	}
	if l != nil {
		l.Close()
	}
	if s != nil {
		s.Close()
	}
}

func newReverseProxy(u *url.URL, s *tsnet.Server) *httputil.ReverseProxy {
	rp := httputil.NewSingleHostReverseProxy(u)

	// 默认 Director 只改 URL.Host，不会改 Host 头，结果后端收到的是 127.0.0.1:port，
	// nginx 按 server_name 分流时就会落到默认站点。这里必须显式改写。
	inner := rp.Director
	rp.Director = func(r *http.Request) {
		inner(r)
		r.Host = u.Host
		r.Header.Del("X-Bridge-Token")

		// MOVE/COPY 的 Destination 是绝对 URL，客户端填的是 127.0.0.1:port，
		// 部分 WebDAV 实现会校验同源，这里改写成真实主机。
		if d := r.Header.Get("Destination"); d != "" {
			if parsed, err := url.Parse(d); err == nil {
				parsed.Scheme = u.Scheme
				parsed.Host = u.Host
				r.Header.Set("Destination", parsed.String())
			}
		}
	}

	mu.Lock()
	skipVerify := insecureTLS
	mu.Unlock()

	transport := &http.Transport{
		// 关键的一行：把所有出站 TCP 交给 tsnet 的用户态网络栈。
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			return s.Dial(ctx, network, addr)
		},
		TLSHandshakeTimeout:    20 * time.Second,
		ResponseHeaderTimeout:  60 * time.Second,
		IdleConnTimeout:        90 * time.Second,
		MaxIdleConns:           16,
		MaxIdleConnsPerHost:    8,
		// 关掉自动解压：否则 Content-Length 和 Range 会失真，视频没法 seek。
		DisableCompression: true,
	}
	if skipVerify {
		// 只在用户显式打开“放行自签证书”时生效
		transport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	}
	rp.Transport = transport

	// -1 表示每次写入立刻 flush，大文件下载和视频流才不会卡住。
	rp.FlushInterval = -1

	rp.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		logf("代理失败 %s %s: %v", r.Method, r.URL.Path, err)
		w.WriteHeader(http.StatusBadGateway)
		fmt.Fprintf(w, "nasdrop bridge: %v", err)
	}
	return rp
}

func tokenGuard(tk string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Bridge-Token") != tk {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		r.Header.Del("X-Bridge-Token")
		next.ServeHTTP(w, r)
	})
}
