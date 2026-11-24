import subprocess

nginx_conf = """# nas.205002.xyz 专用：80 端口直接反代到 nasdrop-server，不强制跳转 HTTPS
# 同时保留 443 端口 HTTPS 访问能力（在 zz-ssl-sites.conf 中定义）
server {
    listen 80;
    listen [::]:80;
    server_name nas.205002.xyz;

    server_tokens off;
    client_max_body_size 0;

    location / {
        proxy_pass http://127.0.0.1:8881;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade           $http_upgrade;
        proxy_set_header Connection        $conn_upgrade;
        proxy_buffering    off;
        proxy_connect_timeout 5s;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
"""

proc = subprocess.Popen(
    ['ssh', '-o', 'StrictHostKeyChecking=no', 'router', 'cat > /mnt/sata1-4/Configs/nginx-proxy/conf.d/01-nas-http.conf && docker exec istore-nginx nginx -t && docker exec istore-nginx nginx -s reload'],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True
)
stdout, stderr = proc.communicate(nginx_conf)
print("STDOUT:", stdout)
print("STDERR:", stderr)
print("Return code:", proc.returncode)
