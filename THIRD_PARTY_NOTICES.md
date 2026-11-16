# 第三方组件声明

本应用以静态链接方式内嵌了以下组件，按各自的许可证要求在此声明。

## Tailscale

- 组件：`tailscale.com`（`tsnet`、`wgengine`、`net/netstack` 等）
- 主页：https://github.com/tailscale/tailscale
- 许可证：BSD 3-Clause

```
Copyright (c) Tailscale Inc & AUTHORS. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
3. Neither the name of the copyright holder nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
```

## WireGuard（Go 实现，随 Tailscale 一起内嵌）

- 仓库：https://github.com/WireGuard/wireguard-go
- 许可证：MIT

WireGuard 是 Jason A. Donenfeld 的注册商标。本项目只是使用其协议实现，
与本项目名称无关，也未获得其背书。

## 其他

- `golang.org/x/mobile`（BSD 3-Clause）
- AndroidX / Jetpack Compose / OkHttp / Coil / Media3（各自 Apache-2.0 或 BSD）
