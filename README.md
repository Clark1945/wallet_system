# Wallet System

[繁體中文](#繁體中文) | [English](#english) | [日本語](#日本語)

---

### Architecture
https://viewer.diagrams.net/?tags=%7B%7D&lightbox=1&highlight=0000ff&edit=_blank&layers=1&nav=1&title=wallet-system.drawio&dark=auto#Uhttps%3A%2F%2Fdrive.google.com%2Fuc%3Fid%3D1_xx2Ctc7uOBeswrEKeFwKal1kBo35N3y%26export%3Ddownload

## 繁體中文

一個以 Spring Boot 建構的數位錢包微服務平台，支援多種支付管道、非同步出金流程、RabbitMQ 解偶 Email 通知，以及完整的身分驗證機制。

### 服務架構

| 服務 | 說明 | Port |
|------|------|------|
| [`nginx/`](nginx/) | 反向代理 — 瀏覽器單一進入點 | 80 |
| [`wallet_system/`](wallet_system/) | 主應用程式 — 身分驗證、錢包、交易 | 8080 |
| [`mock-bank/`](mock-bank/) | 模擬銀行 API，用於出金 Webhook 測試 | 8081 |
| [`payment-service/`](payment-service/) | 支付閘道服務 — Stripe、SoftBank Payment | 8082 |
| [`email-service/`](email-service/) | 非同步 Email 發送服務 — 消費 RabbitMQ 訊息 | 8083 |
| [`audit-service/`](audit-service/) | 非同步稽核日誌服務 — 消費 RabbitMQ 訊息並寫入 MongoDB | 8084 |

```
瀏覽器 ──► nginx (80) ──► wallet_system (8080)
                            │  支付 token
                            ▼
                     payment-service (8082) ──► Stripe / SBPS
                            │  internal API
                            ▼
                     wallet_system (8080)
                            ├─► RabbitMQ ──► email-service (8083) ──► SMTP
                            ├─► RabbitMQ ──► audit-service (8084) ──► MongoDB
                            │
                            ▼
                     mock-bank (8081) ── webhook ──► wallet_system
```

### 功能特色

**身分驗證**
- Email + 密碼登入、Google OAuth2
- Email OTP 驗證（登入 / 註冊兩階段驗證）
- 密碼重設（單次使用 Token，15 分鐘有效）
- 暴力破解防護：5 次失敗 → 帳號鎖定 15 分鐘

**錢包與交易**
- 每位用戶一個獨立錢包，12 碼英數字代碼
- 入金：Stripe（信用卡 / 3DS）、SoftBank Payment Service（SBPS 連結型）
- 出金：非同步銀行出金，HMAC-SHA256 簽名 Webhook 確認
- 轉帳：透過錢包代碼進行 P2P 轉帳，防死鎖雙重悲觀鎖
- 交易記錄：依類型 / 日期篩選、分頁顯示
- 逾時保護：5 分鐘內未收到 Webhook 自動退款
- Email 通知：入金 / 出金成功後發送確認信

**安全性**
- 支付 Token 單次使用（Redis，15 分鐘 TTL）
- 服務間 Internal API 以 `X-Internal-Secret` 標頭驗證
- 所有 Webhook 皆驗簽（Stripe SDK、SBPS SHA-1、HMAC-SHA256）
- Redis 速率限制（入金 10 次/分、出金 5 次/分、轉帳 10 次/分）
- 頭像上傳路徑穿越防護
- 網路隔離：mock-bank 被視為外部銀行，僅位於獨立的 `external` Docker 網路，無法存取內部資料層（PostgreSQL / Redis / RabbitMQ / MongoDB）

**可觀測性**
- TraceId 跨非同步邊界傳播
- 稽核日誌：認證與金流事件的 append-only 軌跡，經 RabbitMQ 寫入 MongoDB（`audit-service`）
- Prometheus metrics + Grafana 儀表板（[http://localhost:3000](http://localhost:3000)）
- Prometheus 告警規則 + Alertmanager（[http://localhost:9093](http://localhost:9093)）：DLQ 堆積、佇列積壓、RabbitMQ 失聯時觸發告警
- Loki + Promtail 集中化日誌
- RabbitMQ Management UI（[http://localhost:15672](http://localhost:15672)）
- Swagger UI：[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

**其他**
- 個人資料編輯與頭像上傳
- 三語支援：繁體中文 / 英文 / 日文

### 快速啟動（Docker Compose）

```bash
# 1. 複製環境變數範本並填入憑證（放在 repo 根目錄）
cp .env.example .env

# 2. 建置並啟動所有服務（從 repo 根目錄執行）
docker compose up --build

# （可選）改用 scripts/dev-up.sh，會自動依 git 為每個服務標上版號
#   bash scripts/dev-up.sh -d      # Git Bash / macOS / Linux
#   .\scripts\dev-up.ps1 -d        # Windows PowerShell
```

啟動的服務：nginx、PostgreSQL、Redis、MongoDB、RabbitMQ、mock-bank、wallet app、payment-service、email-service、audit-service、Prometheus、Alertmanager、postgres-exporter、redis-exporter、Loki、Promtail、Grafana

| 入口 | URL |
|------|-----|
| 進入點（nginx） | [http://localhost](http://localhost) |
| 錢包應用（直連） | [http://localhost:8080](http://localhost:8080) |
| Grafana | [http://localhost:3000](http://localhost:3000) |
| Prometheus | [http://localhost:9090](http://localhost:9090) |
| Alertmanager | [http://localhost:9093](http://localhost:9093) |
| RabbitMQ UI | [http://localhost:15672](http://localhost:15672) |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |

### 告警 Email 通知

Alertmanager 透過 Gmail SMTP 寄送告警信。**SMTP app password 不進 git**，Alertmanager 於執行時從
`auth_password_file`(容器內 `/etc/alertmanager/smtp_password`)讀取,各環境注入方式不同:

- **本地/開發**:`docker-compose.yml` 會把被 gitignore 的
  `wallet_system/observability/alertmanager-smtp-password` 掛載進去。第一次先複製範本並填入 Gmail
  app password(非登入密碼,於 Google 帳號 → 安全性 → 應用程式密碼產生):

  ```bash
  cp wallet_system/observability/alertmanager-smtp-password.example \
     wallet_system/observability/alertmanager-smtp-password
  # 編輯該檔貼上 app password,然後:
  docker compose up -d alertmanager
  ```

- **正式環境(手動 SSH 部署到 VPS)**:密碼是 VPS 上一個 **repo 之外**、你手動建立的檔案,完全不經過
  git/CI(與 `.env` 裡的其他秘密同理)。

  **首次設定(每台主機只做一次)**:

  ```bash
  # 1. 在 repo 外建立秘密檔,貼上 Gmail app password(整個檔案只有那串)
  sudo mkdir -p /etc/wallet-system/secrets
  sudo nano /etc/wallet-system/secrets/alertmanager-smtp-password
  # 2. 讓容器內的 alertmanager(以 nobody / uid 65534 執行)讀得到
  sudo chown 65534:65534 /etc/wallet-system/secrets/alertmanager-smtp-password
  sudo chmod 600 /etc/wallet-system/secrets/alertmanager-smtp-password
  ```

  **每次部署**(用 prod override 啟動):

  ```bash
  docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
  ```

  `docker-compose.prod.yml` 把容器路徑 `/etc/alertmanager/smtp_password` 指向上面那個 host 檔
  (compose 以容器路徑為鍵合併 volume,故乾淨取代開發用的掛載);`alertmanager.yml` 不需改動。
  ⚠️ **密碼檔必須先存在再 `up`**,否則 Docker 會在該路徑建一個空目錄、寄信會默默失敗。
  (走 CI/CD 部署時,改由 pipeline 從 secret store(GitHub Actions secret、Vault、SSM…)把同一個檔寫出來。)

### 測試帳號（Demo）

無需填寫憑證即可體驗功能：

| 欄位 | 值 |
|------|----|
| Email | `test1234@gmail.com` |
| 密碼 | `test1234` |
| OTP | `123456` |

> 測試帳號在非 prod 環境啟動時自動建立，帳號餘額可自由操作。

### 必要環境變數（`.env`）

| 變數 | 說明 |
|------|------|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 憑證 |
| `STRIPE_SECRET_KEY` / `STRIPE_PUBLISHABLE_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe 測試金鑰 |
| `PAYMENT_MERCHANT_ID` / `PAYMENT_SERVICE_ID` / `PAYMENT_HASH_KEY` | SBPS 沙盒憑證 |
| `SP_PAYMENT_REDIRECT_URL` | SBPS 回呼的公開網址（例如 ngrok URL） |
| `WITHDRAW_WEBHOOK_SECRET` | wallet app 與 mock-bank 共用的 HMAC 金鑰 |
| `INTERNAL_SERVICE_SECRET` | wallet-service 與 payment-service 間的 API 驗證金鑰 |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | RabbitMQ 帳密 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 憑證（OTP / 密碼重設 / 入出金通知信件） |

完整範本請參考 [`.env.example`](.env.example)。

### 入金流程

```
瀏覽器          wallet_system       payment-service        Stripe / SBPS
  │                  │                    │                      │
  │─ POST /deposit ─►│                    │                      │
  │                  │─ redirect ?token ─►│                      │
  │◄─────────────────────────────────────│                      │
  │                  │◄─ GET /internal/token/{token}            │
  │                  │   (驗證並消耗 token)                       │
  │                  │◄─ POST /internal/deposit/initiate        │
  │                  │   (建立 PENDING 交易)                      │
  │                  │                    │── 支付流程 ──────────►│
  │                  │                    │◄── Webhook ──────────│
  │                  │◄─ POST /internal/deposit/complete        │
  │                  │   (餘額入帳)                               │
  │                  │─► RabbitMQ ─────────────────────────────── email-service ─► SMTP
```

### 出金流程

```
瀏覽器           wallet_system              mock-bank
  │                   │                        │
  │─ POST /withdraw ─►│                        │
  │                   │─── POST /api/withdraw ─►│
  │◄─ redirect ───────│◄── 200 OK ─────────────│
  │                   │                   （3–8 秒後）
  │                   │◄── POST /withdraw/webhook ──│
  │                   │    { result: "SUCCESS" }    │
  │                   │    X-Webhook-Signature: sha256=...
  │                   │─► RabbitMQ ──── email-service ─► SMTP
```

若 5 分鐘內未收到 Webhook，`TransactionTimeoutJob` 自動退款並將交易標記為 `FAILED`。

### 技術棧

Spring Boot 4.0.5 · Java 17 · Spring Security · PostgreSQL 14 · Redis · MongoDB · RabbitMQ · Flyway · Thymeleaf · Stripe · SBPS · nginx · Docker Compose · Loki · Grafana · Prometheus

---

## English

A digital wallet microservices platform built with Spring Boot, featuring multiple payment gateways, asynchronous withdrawal flow, RabbitMQ-decoupled email notifications, and a comprehensive authentication system.

### Services

| Service | Description | Port |
|---------|-------------|------|
| [`nginx/`](nginx/) | Reverse proxy — single browser-facing entry point | 80 |
| [`wallet_system/`](wallet_system/) | Core app — auth, wallet, transactions | 8080 |
| [`mock-bank/`](mock-bank/) | Mock bank API for withdrawal webhook simulation | 8081 |
| [`payment-service/`](payment-service/) | Payment gateway handler — Stripe, SoftBank Payment | 8082 |
| [`email-service/`](email-service/) | Async email dispatcher — consumes RabbitMQ messages | 8083 |
| [`audit-service/`](audit-service/) | Async audit-log consumer — consumes RabbitMQ messages, persists to MongoDB | 8084 |

```
Browser ──► nginx (80) ──► wallet_system (8080)
                             │  payment token
                             ▼
                      payment-service (8082) ──► Stripe / SBPS
                             │  internal API
                             ▼
                      wallet_system (8080)
                             ├─► RabbitMQ ──► email-service (8083) ──► SMTP
                             ├─► RabbitMQ ──► audit-service (8084) ──► MongoDB
                             │
                             ▼
                      mock-bank (8081) ── webhook ──► wallet_system
```

### Features

**Authentication**
- Email + password login, Google OAuth2
- Email OTP verification (two-step login / registration)
- Password reset (single-use token, 15-minute TTL)
- Brute-force protection: 5 failures → account locked for 15 minutes

**Wallet & Transactions**
- One wallet per user with a unique 12-character alphanumeric code
- Deposit via Stripe (credit card / 3DS) or SoftBank Payment Service (link-type)
- Async bank withdrawal with HMAC-SHA256 signed webhook confirmation
- P2P transfer by wallet code with deadlock-safe dual pessimistic locking
- Transaction history with type / date-range filtering and pagination
- Timeout safety: auto-refund if no webhook arrives within 5 minutes
- Email notification on successful deposit / withdrawal

**Security**
- Single-use payment tokens (Redis, 15-minute TTL)
- Service-to-service internal API authenticated via `X-Internal-Secret` header
- Webhook signature verification for all gateways (Stripe SDK, SBPS SHA-1, HMAC-SHA256)
- Redis-backed rate limiting (deposit 10/min, withdrawal 5/min, transfer 10/min)
- Path-traversal protection on avatar uploads
- Network isolation: mock-bank is treated as an external bank, living only on a separate `external` Docker network with no access to the internal datastores (PostgreSQL / Redis / RabbitMQ / MongoDB)

**Observability**
- TraceId propagation across async boundaries
- Audit log: append-only trail of auth & money events, persisted to MongoDB via RabbitMQ (`audit-service`)
- Prometheus metrics + Grafana dashboard ([http://localhost:3000](http://localhost:3000))
- Prometheus alert rules + Alertmanager ([http://localhost:9093](http://localhost:9093)): fires on dead-letter queue buildup, queue backlog, and RabbitMQ outage
- Centralized logging with Loki + Promtail
- RabbitMQ Management UI ([http://localhost:15672](http://localhost:15672))
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

**Other**
- Profile editing and avatar upload
- i18n: Traditional Chinese / English / Japanese

### Quick Start (Docker Compose)

```bash
# 1. Copy the env template and fill in your credentials (place in repo root)
cp .env.example .env

# 2. Build and start all services (run from repo root)
docker compose up --build

# (optional) use scripts/dev-up.sh instead — it auto-tags each service's version from git
#   bash scripts/dev-up.sh -d      # Git Bash / macOS / Linux
#   .\scripts\dev-up.ps1 -d        # Windows PowerShell
```

Starts: nginx, PostgreSQL, Redis, MongoDB, RabbitMQ, mock-bank, wallet app, payment-service, email-service, audit-service, Prometheus, Alertmanager, postgres-exporter, redis-exporter, Loki, Promtail, Grafana

| Endpoint | URL |
|----------|-----|
| Entry point (nginx) | [http://localhost](http://localhost) |
| Wallet app (direct) | [http://localhost:8080](http://localhost:8080) |
| Grafana | [http://localhost:3000](http://localhost:3000) |
| Prometheus | [http://localhost:9090](http://localhost:9090) |
| Alertmanager | [http://localhost:9093](http://localhost:9093) |
| RabbitMQ UI | [http://localhost:15672](http://localhost:15672) |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |

### Alert email notifications

Alertmanager sends alert email via Gmail SMTP. The **SMTP app password never enters git** —
Alertmanager reads it at runtime from `auth_password_file` (`/etc/alertmanager/smtp_password` in the
container), supplied differently per environment:

- **Local/dev** — `docker-compose.yml` bind-mounts the gitignored
  `wallet_system/observability/alertmanager-smtp-password`. Create it once from the template and paste
  your Gmail app password (not your login password — generate one under Google Account → Security →
  App passwords):

  ```bash
  cp wallet_system/observability/alertmanager-smtp-password.example \
     wallet_system/observability/alertmanager-smtp-password
  # edit the file, paste the app password, then:
  docker compose up -d alertmanager
  ```

- **Production (manual SSH deploy to a VPS)** — the password is a file **outside the repo** that you
  create by hand on the host; it never goes through git/CI (same as your other `.env` secrets).

  **First-time setup (once per host):**

  ```bash
  # 1. Create the secret file outside the repo; paste your Gmail app password (only that string)
  sudo mkdir -p /etc/wallet-system/secrets
  sudo nano /etc/wallet-system/secrets/alertmanager-smtp-password
  # 2. Make it readable by the alertmanager container (runs as nobody / uid 65534)
  sudo chown 65534:65534 /etc/wallet-system/secrets/alertmanager-smtp-password
  sudo chmod 600 /etc/wallet-system/secrets/alertmanager-smtp-password
  ```

  **Each deploy** (bring the stack up with the prod override):

  ```bash
  docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
  ```

  `docker-compose.prod.yml` points the container path `/etc/alertmanager/smtp_password` at that host
  file (Compose merges volumes by container path, so it cleanly replaces the dev bind-mount);
  `alertmanager.yml` is unchanged. ⚠️ **The secret file must exist before `up`**, otherwise Docker
  creates an empty directory at that path and email sends fail silently. (For a CI/CD deploy, the
  pipeline writes the same file from a secret store — GitHub Actions secret, Vault, SSM, …)

### Demo Account

Try the app without configuring any credentials:

| Field | Value |
|-------|-------|
| Email | `test1234@gmail.com` |
| Password | `test1234` |
| OTP | `123456` |

> The demo account is created automatically on startup in non-prod environments.

### Required Environment Variables (`.env`)

| Variable | Description |
|----------|-------------|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 credentials |
| `STRIPE_SECRET_KEY` / `STRIPE_PUBLISHABLE_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe test keys |
| `PAYMENT_MERCHANT_ID` / `PAYMENT_SERVICE_ID` / `PAYMENT_HASH_KEY` | SBPS sandbox credentials |
| `SP_PAYMENT_REDIRECT_URL` | Public base URL for SBPS callbacks (e.g. your ngrok URL) |
| `WITHDRAW_WEBHOOK_SECRET` | Shared HMAC secret between wallet app and mock-bank |
| `INTERNAL_SERVICE_SECRET` | API authentication key between wallet-service and payment-service |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | RabbitMQ credentials |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials for OTP, password reset, and transaction notification emails |

See [`.env.example`](.env.example) for a complete template.

### Deposit Flow

```
Browser         wallet_system       payment-service          Stripe / SBPS
  │                  │                    │                       │
  │─ POST /deposit ─►│                    │                       │
  │                  │─ redirect ?token ─►│                       │
  │◄─────────────────────────────────────│                       │
  │                  │◄─ GET /internal/token/{token}             │
  │                  │   (validate & consume token)               │
  │                  │◄─ POST /internal/deposit/initiate         │
  │                  │   (create PENDING transaction)             │
  │                  │                    │── payment flow ──────►│
  │                  │                    │◄── webhook ───────────│
  │                  │◄─ POST /internal/deposit/complete         │
  │                  │   (credit wallet)                          │
  │                  │─► RabbitMQ ──────────── email-service ─► SMTP
```

### Withdrawal Flow

```
Browser          wallet_system              mock-bank
  │                   │                        │
  │─ POST /withdraw ─►│                        │
  │                   │─── POST /api/withdraw ─►│
  │◄─ redirect ───────│◄── 200 OK ─────────────│
  │                   │                   (3–8 s delay)
  │                   │◄── POST /withdraw/webhook ──│
  │                   │    { result: "SUCCESS" }    │
  │                   │    X-Webhook-Signature: sha256=...
  │                   │─► RabbitMQ ──── email-service ─► SMTP
```

If no webhook arrives within 5 minutes, `TransactionTimeoutJob` automatically refunds the balance and marks the transaction as `FAILED`.

### Tech Stack

Spring Boot 4.0.5 · Java 17 · Spring Security · PostgreSQL 14 · Redis · MongoDB · RabbitMQ · Flyway · Thymeleaf · Stripe · SBPS · nginx · Docker Compose · Loki · Grafana · Prometheus

---

## 日本語

Spring Boot で構築されたデジタルウォレットのマイクロサービスプラットフォームです。複数の決済ゲートウェイ、非同期出金フロー、RabbitMQ による Email 通知の疎結合化、充実した認証機能を備えています。

### サービス構成

| サービス | 説明 | Port |
|---------|------|------|
| [`nginx/`](nginx/) | リバースプロキシ — ブラウザ単一エントリポイント | 80 |
| [`wallet_system/`](wallet_system/) | メインアプリ — 認証・ウォレット・取引 | 8080 |
| [`mock-bank/`](mock-bank/) | 出金 Webhook テスト用モック銀行 API | 8081 |
| [`payment-service/`](payment-service/) | 決済ゲートウェイサービス — Stripe・SoftBank Payment | 8082 |
| [`email-service/`](email-service/) | 非同期 Email 送信サービス — RabbitMQ メッセージを消費 | 8083 |
| [`audit-service/`](audit-service/) | 非同期監査ログサービス — RabbitMQ メッセージを消費し MongoDB に永続化 | 8084 |

```
ブラウザ ──► nginx (80) ──► wallet_system (8080)
                             │  支払いトークン
                             ▼
                      payment-service (8082) ──► Stripe / SBPS
                             │  internal API
                             ▼
                      wallet_system (8080)
                             ├─► RabbitMQ ──► email-service (8083) ──► SMTP
                             ├─► RabbitMQ ──► audit-service (8084) ──► MongoDB
                             │
                             ▼
                      mock-bank (8081) ── webhook ──► wallet_system
```

### 機能一覧

**認証**
- メール＋パスワードログイン、Google OAuth2
- メール OTP 認証（ログイン / 会員登録の二段階認証）
- パスワードリセット（ワンタイムトークン、有効期限 15 分）
- ブルートフォース対策：5 回失敗 → アカウント 15 分ロック

**ウォレット・取引**
- ユーザーごとに 1 つのウォレット（12 桁英数字コード）
- 入金：Stripe（クレジットカード / 3DS）、SoftBank Payment Service（リンク型）
- 出金：非同期銀行振込、HMAC-SHA256 署名付き Webhook 確認
- 送金：ウォレットコードによる P2P 送金（デッドロック防止の二重悲観的ロック）
- 取引履歴：種別 / 日付フィルタリング、ページネーション
- タイムアウト保護：5 分以内に Webhook が届かない場合は自動返金
- Email 通知：入金 / 出金完了時に確認メールを送信

**セキュリティ**
- 支払いトークンのワンタイム使用（Redis、15 分 TTL）
- サービス間 Internal API を `X-Internal-Secret` ヘッダーで認証
- 全 Webhook の署名検証（Stripe SDK・SBPS SHA-1・HMAC-SHA256）
- Redis レートリミット（入金 10 回/分・出金 5 回/分・送金 10 回/分）
- アバターアップロードのパストラバーサル防止
- ネットワーク分離：mock-bank は外部銀行として扱われ、独立した `external` Docker ネットワークにのみ配置され、内部データストア（PostgreSQL / Redis / RabbitMQ / MongoDB）にはアクセスできません

**可観測性**
- 非同期処理を跨ぐ TraceId 伝播
- 監査ログ：認証・金銭イベントの追記専用トレイルを RabbitMQ 経由で MongoDB に永続化（`audit-service`）
- Prometheus メトリクス + Grafana ダッシュボード（[http://localhost:3000](http://localhost:3000)）
- Prometheus アラートルール + Alertmanager（[http://localhost:9093](http://localhost:9093)）：DLQ 滞留・キュー滞積・RabbitMQ 不通時にアラート発火
- Loki + Promtail による集中ログ管理
- RabbitMQ Management UI（[http://localhost:15672](http://localhost:15672)）
- Swagger UI：[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

**その他**
- プロフィール編集・アバターアップロード
- 多言語対応：繁体字中国語 / 英語 / 日本語

### クイックスタート（Docker Compose）

```bash
# 1. 環境変数テンプレートをコピーして認証情報を入力（リポジトリルートに配置）
cp .env.example .env

# 2. 全サービスをビルド・起動（リポジトリルートで実行）
docker compose up --build

# （任意）scripts/dev-up.sh を使うと git からサービスごとにバージョンを自動付与します
#   bash scripts/dev-up.sh -d      # Git Bash / macOS / Linux
#   .\scripts\dev-up.ps1 -d        # Windows PowerShell
```

起動されるサービス：nginx、PostgreSQL、Redis、MongoDB、RabbitMQ、mock-bank、wallet app、payment-service、email-service、audit-service、Prometheus、Alertmanager、postgres-exporter、redis-exporter、Loki、Promtail、Grafana

| 入口 | URL |
|------|-----|
| エントリポイント（nginx） | [http://localhost](http://localhost) |
| ウォレットアプリ（直接） | [http://localhost:8080](http://localhost:8080) |
| Grafana | [http://localhost:3000](http://localhost:3000) |
| Prometheus | [http://localhost:9090](http://localhost:9090) |
| Alertmanager | [http://localhost:9093](http://localhost:9093) |
| RabbitMQ UI | [http://localhost:15672](http://localhost:15672) |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |

### アラートメール通知

Alertmanager は Gmail SMTP でアラートメールを送信します。**SMTP アプリパスワードは git に入れません**。
Alertmanager は実行時に `auth_password_file`(コンテナ内 `/etc/alertmanager/smtp_password`)から読み込み、
環境ごとに注入方法が異なります:

- **ローカル/開発** — `docker-compose.yml` が gitignore 済みの
  `wallet_system/observability/alertmanager-smtp-password` をマウントします。初回はテンプレートを複製し、
  Gmail アプリパスワード(ログインパスワードではなく、Google アカウント → セキュリティ → アプリパスワード
  で生成)を貼り付けます:

  ```bash
  cp wallet_system/observability/alertmanager-smtp-password.example \
     wallet_system/observability/alertmanager-smtp-password
  # ファイルを編集してアプリパスワードを貼り付け、その後:
  docker compose up -d alertmanager
  ```

- **本番環境(VPS への手動 SSH デプロイ)** — パスワードはホスト上で手動作成する **リポジトリ外** の
  ファイルです。git/CI を一切経由しません(`.env` の他のシークレットと同様)。

  **初回セットアップ(ホストごとに一度だけ):**

  ```bash
  # 1. リポジトリ外にシークレットファイルを作成し、Gmail アプリパスワード(その文字列のみ)を貼り付け
  sudo mkdir -p /etc/wallet-system/secrets
  sudo nano /etc/wallet-system/secrets/alertmanager-smtp-password
  # 2. alertmanager コンテナ(nobody / uid 65534 で実行)が読めるようにする
  sudo chown 65534:65534 /etc/wallet-system/secrets/alertmanager-smtp-password
  sudo chmod 600 /etc/wallet-system/secrets/alertmanager-smtp-password
  ```

  **毎回のデプロイ**(prod オーバーライドで起動):

  ```bash
  docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
  ```

  `docker-compose.prod.yml` はコンテナパス `/etc/alertmanager/smtp_password` を上記のホストファイルへ
  向けます(Compose はコンテナパスをキーに volume をマージするため、開発用マウントをきれいに置き換えます)。
  `alertmanager.yml` は変更不要です。⚠️ **シークレットファイルは `up` の前に存在している必要があります**。
  なければ Docker がそのパスに空ディレクトリを作成し、メール送信が静かに失敗します。(CI/CD デプロイの場合は
  パイプラインが secret store(GitHub Actions secret、Vault、SSM…)から同じファイルを書き出します。)

### デモアカウント

認証情報の設定なしでアプリを試せます：

| 項目 | 値 |
|------|----|
| Email | `test1234@gmail.com` |
| パスワード | `test1234` |
| OTP | `123456` |

> デモアカウントは非 prod 環境の起動時に自動作成されます。

### 必須環境変数（`.env`）

| 変数 | 説明 |
|------|------|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 認証情報 |
| `STRIPE_SECRET_KEY` / `STRIPE_PUBLISHABLE_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe テストキー |
| `PAYMENT_MERCHANT_ID` / `PAYMENT_SERVICE_ID` / `PAYMENT_HASH_KEY` | SBPS サンドボックス認証情報 |
| `SP_PAYMENT_REDIRECT_URL` | SBPS コールバック用の公開 URL（例：ngrok URL） |
| `WITHDRAW_WEBHOOK_SECRET` | wallet app と mock-bank の共有 HMAC シークレット |
| `INTERNAL_SERVICE_SECRET` | wallet-service と payment-service 間の API 認証キー |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | RabbitMQ 認証情報 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 認証情報（OTP・パスワードリセット・入出金通知メール） |

完全なテンプレートは [`.env.example`](.env.example) を参照してください。

### 入金フロー

```
ブラウザ         wallet_system       payment-service          Stripe / SBPS
  │                  │                    │                       │
  │─ POST /deposit ─►│                    │                       │
  │                  │─ redirect ?token ─►│                       │
  │◄─────────────────────────────────────│                       │
  │                  │◄─ GET /internal/token/{token}             │
  │                  │   (トークン検証・消費)                       │
  │                  │◄─ POST /internal/deposit/initiate         │
  │                  │   (PENDING 取引作成)                        │
  │                  │                    │── 決済フロー ─────────►│
  │                  │                    │◄── Webhook ───────────│
  │                  │◄─ POST /internal/deposit/complete         │
  │                  │   (残高入金)                                │
  │                  │─► RabbitMQ ──────────── email-service ─► SMTP
```

### 出金フロー

```
ブラウザ          wallet_system              mock-bank
  │                   │                        │
  │─ POST /withdraw ─►│                        │
  │                   │─── POST /api/withdraw ─►│
  │◄─ redirect ───────│◄── 200 OK ─────────────│
  │                   │                   （3〜8 秒後）
  │                   │◄── POST /withdraw/webhook ──│
  │                   │    { result: "SUCCESS" }    │
  │                   │    X-Webhook-Signature: sha256=...
  │                   │─► RabbitMQ ──── email-service ─► SMTP
```

5 分以内に Webhook が届かない場合、`TransactionTimeoutJob` が自動的に返金し、取引を `FAILED` としてマークします。

### 技術スタック

Spring Boot 4.0.5 · Java 17 · Spring Security · PostgreSQL 14 · Redis · MongoDB · RabbitMQ · Flyway · Thymeleaf · Stripe · SBPS · nginx · Docker Compose · Loki · Grafana · Prometheus