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

	// 注意：这里刻意没有 _ "tailscale.com/feature/androidbin"。
	// 那个包的注册文件构建标签是 (linux && !android) || (android && !cgo)，
	// 而 gomobile bind 是 android + cgo，导入它等于什么都没做（.so 里不会有任何
	// androidbin 符号）。网卡枚举的绕行方案在本包的 interfaces.go 里自己注册。
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

	// 「节点已就绪」信号：Up 成功或任何一次 Status 看到 Running 都会关掉它。
	// 这样即使 Up 先超时返回，节点后来才连上，等待方也能被放行。
	readyMu sync.Mutex
	readyCh chan struct{}

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

// newReadyChan 为一次新的 Start 创建就绪信号。
func newReadyChan() chan struct{} {
	readyMu.Lock()
	defer readyMu.Unlock()
	readyCh = make(chan struct{})
	return readyCh
}

// markReady 关闭就绪信号（幂等，可被 Up 和状态轮询重复调用）。
func markReady() {
	readyMu.Lock()
	defer readyMu.Unlock()
	if readyCh == nil {
		return
	}
	select {
	case <-readyCh:
	default:
		close(readyCh)
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

	// 节点是异步上线的（冷启动要几秒，首次还要等授权）。这个 channel 让代理在拨号前
	// 稍等一下，App 第一次打开目录就不会因为“节点还没起来”直接吃一个 502。
	ready := newReadyChan()

	rp := newReverseProxy(u, s, ready)

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
			// Up 在 NeedsLogin 时会一直阻塞（这是 tsnet 的已知行为），所以超时往往
			// 只是“用户还没去浏览器授权”，不能当成致命错误盖住登录入口。
			if errors.Is(err, context.DeadlineExceeded) {
				logf("等待上线超时，节点仍在后台尝试；如需登录请点“去登录”")
				return
			}
			mu.Lock()
			upErr = err
			mu.Unlock()
			if strings.Contains(err.Error(), "netlinkrib") {
				logf("tsnet 上线失败: %v（Android 拒绝 netlink，检查 Java 侧是否推送了网卡清单）", err)
			} else {
				logf("tsnet 上线失败: %v", err)
			}
			return
		}
		logf("tsnet 已上线: %v", st.TailscaleIPs)
		markReady()
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
		// Up 可能已经超时返回了，这里兜底：只要看到 Running 就放行拨号等待。
		markReady()
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
	p, err := Start(d, h, k, t)
	if err != nil {
		logf("重连失败: %v", err)
		return 0
	}
	return p
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

// waitReady 等节点上线，最多等 d。超时也继续往下走，让真正的 Dial 把错误暴露出来
// （比如需要用户去浏览器授权，这时等待没意义，不如直接报错，由 UI 引导去登录）。
func waitReady(ctx context.Context, ready <-chan struct{}, d time.Duration) {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ready:
	case <-t.C:
	case <-ctx.Done():
	}
}

func newReverseProxy(u *url.URL, s *tsnet.Server, ready <-chan struct{}) *httputil.ReverseProxy {
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
		// 拨号前先等节点就绪：节点没起来时 Dial 只会返回一个难懂的错误，
		// 与其让用户看到 502，不如在这里多等一会儿（最多 30 秒）。
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			waitReady(ctx, ready, 20*time.Second)
			// tsnet 的 Dial 内部会一直等节点 Running，这里给个上限，
			// 免得界面无限转圈（按 OkHttp 的读超时算，最多也就等这么久）。
			dctx, cancel := context.WithTimeout(ctx, 40*time.Second)
			defer cancel()
			return s.Dial(dctx, network, addr)
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
