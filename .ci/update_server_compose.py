import subprocess

compose_content = """services:
  nasdrop-server:
    image: ghcr.io/anansite/nasdrop-server:latest
    container_name: nasdrop-server
    restart: unless-stopped
    mem_limit: 768m
    memswap_limit: 768m
    cpus: 1.0
    ports:
      - "8881:8881"
    volumes:
      - /mnt/merged/nas:/data
      - ./data:/config
    environment:
      - PORT=8881
      - DATA_DIR=/data
      - DB_PATH=/config/nasdrop.db
      - THUMB_DIR=/config/thumbnails
      - NASDROP_USER=admin
      - NASDROP_PASS=admin
"""

cmd = "cat > /mnt/sata1-4/apps/nasdrop/docker-compose.yml && cd /mnt/sata1-4/apps/nasdrop && docker compose up -d"
proc = subprocess.Popen(
    ['ssh', '-o', 'StrictHostKeyChecking=no', 'router', cmd],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True
)
stdout, stderr = proc.communicate(compose_content)
print("STDOUT:", stdout)
print("STDERR:", stderr)
print("Return code:", proc.returncode)
