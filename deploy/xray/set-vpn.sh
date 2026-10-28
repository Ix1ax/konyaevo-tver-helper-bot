#!/bin/bash
# Смена VPN для бота: ссылка vless:// из .env (VPN_URL) превращается в конфиг xray.
# Использование (из каталога бота, от root):
#   ./deploy/xray/set-vpn.sh                  — взять VPN_URL из .env
#   ./deploy/xray/set-vpn.sh 'vless://...'    — записать ссылку в .env и применить
#   ./deploy/xray/set-vpn.sh --check          — только проверить текущий VPN
# Старый конфиг сохраняется рядом; если xray не принял новый или Telegram через него недоступен, он возвращается.
set -euo pipefail
cd "$(dirname "$0")/../.."

CONFIG="${XRAY_CONFIG:-/usr/local/etc/xray/config.json}"
SOCKS_PORT="${XRAY_SOCKS_PORT:-10808}"
HTTP_PORT="${XRAY_HTTP_PORT:-10809}"

check() {
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 --socks5-hostname "127.0.0.1:$SOCKS_PORT" https://api.telegram.org || true)
    if [[ "$code" =~ ^(200|302)$ ]]; then
        echo "OK: Telegram доступен через VPN (HTTP $code)"
        return 0
    fi
    echo "FAIL: Telegram недоступен через VPN (HTTP ${code:-000})"
    return 1
}

if [ "${1:-}" = "--check" ]; then
    check
    exit $?
fi

if [ -n "${1:-}" ]; then
    LINK="$1"
    touch .env
    if grep -q '^VPN_URL=' .env; then
        python3 -c 'import sys,re; p=".env"; s=open(p).read(); open(p,"w").write(re.sub(r"(?m)^VPN_URL=.*$", lambda m: "VPN_URL=\x27" + sys.argv[1] + "\x27", s))' "$LINK"
    else
        # Кавычки обязательны: start.sh загружает .env через source, а в ссылке есть & и #.
        printf "\nVPN_URL='%s'\n" "$LINK" >> .env
    fi
    echo "Ссылка записана в .env (VPN_URL)"
else
    LINK=$(grep -E '^VPN_URL=' .env 2>/dev/null | tail -1 | cut -d= -f2- || true)
    LINK="${LINK%\"}"; LINK="${LINK#\"}"; LINK="${LINK%\'}"; LINK="${LINK#\'}"
fi

if [ -z "$LINK" ]; then
    echo "Нет ссылки: добавьте VPN_URL=vless://... в .env или передайте её аргументом" >&2
    exit 1
fi

NEW=$(mktemp)
trap 'rm -f "$NEW"' EXIT
python3 - "$LINK" "$SOCKS_PORT" "$HTTP_PORT" > "$NEW" <<'PY'
import json, sys
from urllib.parse import urlsplit, parse_qs, unquote

link, socks, http = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
url = urlsplit(link.strip())
if url.scheme != "vless" or not url.username or not url.hostname:
    sys.exit("Поддерживаются ссылки вида vless://id@host:port?...")
q = {k: v[0] for k, v in parse_qs(url.query).items()}
network = q.get("type", "tcp")
security = q.get("security", "none")

user = {"id": unquote(url.username), "encryption": q.get("encryption", "none")}
if q.get("flow"):
    user["flow"] = q["flow"]
stream = {"network": network, "security": security}
if security == "reality":
    stream["realitySettings"] = {k: v for k, v in {
        "serverName": q.get("sni", url.hostname), "fingerprint": q.get("fp", "chrome"),
        "publicKey": q.get("pbk"), "shortId": q.get("sid", ""), "spiderX": q.get("spx")}.items() if v}
elif security == "tls":
    stream["tlsSettings"] = {"serverName": q.get("sni", url.hostname), "fingerprint": q.get("fp", "chrome")}
    if q.get("alpn"):
        stream["tlsSettings"]["alpn"] = q["alpn"].split(",")
if network == "ws":
    stream["wsSettings"] = {"path": q.get("path", "/"), "headers": {"Host": q.get("host", url.hostname)}}
elif network == "grpc":
    stream["grpcSettings"] = {"serviceName": q.get("serviceName", "")}
elif network in ("xhttp", "splithttp"):
    stream["xhttpSettings"] = {"path": q.get("path", "/"), "host": q.get("host", url.hostname), "mode": q.get("mode", "auto")}

sniffing = {"enabled": True, "destOverride": ["http", "tls", "quic"], "routeOnly": False}
config = {
    "log": {"loglevel": "warning"},
    "dns": {"queryStrategy": "UseIPv4", "servers": ["1.1.1.1", "8.8.8.8"]},
    "inbounds": [
        {"tag": "socks", "listen": "127.0.0.1", "port": socks, "protocol": "socks",
         "settings": {"auth": "noauth", "udp": True}, "sniffing": sniffing},
        {"tag": "http", "listen": "127.0.0.1", "port": http, "protocol": "http",
         "settings": {"allowTransparent": False}, "sniffing": sniffing},
    ],
    "outbounds": [
        {"tag": "proxy", "protocol": "vless",
         "settings": {"vnext": [{"address": url.hostname, "port": url.port or 443, "users": [user]}]},
         "streamSettings": stream},
        {"tag": "direct", "protocol": "freedom"},
        {"tag": "block", "protocol": "blackhole"},
    ],
    # Локальные адреса напрямую, всё остальное через VPN. Без geosite/geoip: им не нужны файлы баз.
    "routing": {"domainStrategy": "IPIfNonMatch", "rules": [
        {"type": "field", "ip": ["127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "::1/128"], "outboundTag": "direct"},
        {"type": "field", "network": "tcp,udp", "outboundTag": "proxy"},
    ]},
}
print(json.dumps(config, ensure_ascii=False, indent=2))
name = unquote(url.fragment) if url.fragment else url.hostname
print(f"VPN: {name} ({url.hostname}:{url.port or 443}, {network}/{security})", file=sys.stderr)
PY

xray run -test -config "$NEW" > /dev/null || { echo "xray не принял конфиг, ничего не изменено" >&2; exit 1; }

BACKUP=""
if [ -f "$CONFIG" ]; then
    BACKUP="$CONFIG.bak-$(date +%Y%m%d-%H%M%S)"
    cp "$CONFIG" "$BACKUP"
fi
mkdir -p "$(dirname "$CONFIG")"
cp "$NEW" "$CONFIG"
chmod 644 "$CONFIG"
systemctl restart xray
sleep 3

if check; then
    echo "Готово. Перезапускать бота не нужно: он сам перейдёт на рабочий маршрут."
    exit 0
fi

if [ -n "$BACKUP" ]; then
    cp "$BACKUP" "$CONFIG"
    systemctl restart xray
    echo "Новый VPN не работает — вернул прежний конфиг ($BACKUP)" >&2
fi
exit 1
