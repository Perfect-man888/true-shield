# 真信盾生产部署（Ubuntu + Docker Compose）

这套配置只对公网开放 Caddy 的 80/443 端口。PostgreSQL、Redis、MinIO 和 API 原始端口均不直接暴露公网。

## 1. 云控制台放行端口

- TCP 22：仅放行自己的公网出口 IP（部署期间若无法确定，可临时放行，登录后立即收紧）。
- TCP 80、443：来源 `0.0.0.0/0`。
- UDP 443：可选，用于 HTTP/3。
- 不要放行 5432、6379、8000、9000、9001。

若 22 端口仍不可达，请检查实例是否开机、轻量应用服务器防火墙、系统 UFW 和 SSH 服务状态。

## 2. 安装 Docker

登录服务器后确认：

```bash
docker --version
docker compose version
```

若尚未安装，按 Docker 官方 Ubuntu 安装说明安装 Docker Engine 和 Compose plugin，并把 `ubuntu` 用户加入 `docker` 组，重新登录后再继续。

## 3. 获取代码

```bash
git clone https://github.com/Perfect-man888/true-shield.git
cd true-shield
```

若仓库为私有仓库，请使用 GitHub deploy key 或只读 token，不要把 token 写进仓库文件。

## 4. 生成生产密钥

```bash
cp deploy/production.env.example .env.production
openssl rand -hex 32
openssl rand -hex 32
openssl rand -hex 16
```

把三条结果分别填入 `.env.production` 的 `JWT_SECRET_KEY`、`POSTGRES_PASSWORD` 和 `MINIO_SECRET_KEY`。该文件已被 Git 忽略，不得提交。

没有域名时先保留：

```dotenv
PUBLIC_HOST=http://192.144.143.48
FRONTEND_URL=http://192.144.143.48
```

有域名时，把域名 A 记录解析到服务器，并改为：

```dotenv
PUBLIC_HOST=api.example.com
FRONTEND_URL=https://api.example.com
```

Caddy 会自动申请和续期 HTTPS 证书。

## 5. 启动与验证

```bash
chmod +x deploy/deploy.sh
./deploy/deploy.sh
curl -fsS "http://127.0.0.1/api/v1/health"
```

公网验证：

```bash
curl -fsS "http://192.144.143.48/api/v1/health"
```

若已配置域名，则使用 `https://api.example.com/api/v1/health`。接口文档位于 `/docs`。

常用运维命令：

```bash
docker compose --env-file .env.production -f deploy/compose.production.yml ps
docker compose --env-file .env.production -f deploy/compose.production.yml logs -f --tail=200 api gateway
docker compose --env-file .env.production -f deploy/compose.production.yml pull
./deploy/deploy.sh
```

## 6. Android 公网地址

服务器验证成功后，再把 Android 的 API 基础地址改成公网地址并重新打包：

```text
http://192.144.143.48/
```

有域名和 HTTPS 时应使用：

```text
https://api.example.com/
```

正式发布建议只使用 HTTPS，并将 `android:usesCleartextTraffic` 改为 `false`。

## 7. 资源提醒

API 镜像包含 OCR、FunASR、PyTorch 等依赖，首次构建和模型加载较重。当前配置保留全部功能并启用语音转写，同时限制为单线程运行。2 GB 内存加 Swap 可以尝试启动，但首次模型加载和语音分析可能较慢；稳定演示建议升级到至少 4 GB RAM，8 GB 更稳妥。
