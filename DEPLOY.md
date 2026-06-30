# Deployment Guide — share a live URL for your assignment

You need **two things** for submission:
1. **GitHub repo** with source code (required by assignment)
2. **Live demo URL** (optional but impressive) — this guide

---

## Option A: Railway (recommended — ~15 minutes)

Railway runs your Docker app + MySQL with a persistent public URL like  
`https://erp-demo-production.up.railway.app`

### Prerequisites
- [GitHub](https://github.com) account
- [Railway](https://railway.app) account (sign in with GitHub)
- Code pushed to a GitHub repo

### Steps

**1. Push code to GitHub**
```bash
cd /Users/sameer/code/erp_demo
git init
git add .
git commit -m "ERP AR module assessment submission"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/erp-demo.git
git push -u origin main
```

**2. Create Railway project**
1. Go to [railway.app/new](https://railway.app/new)
2. **Deploy from GitHub repo** → select `erp-demo`
3. Railway detects the `Dockerfile` and creates a service

**3. Add MySQL database**
1. In the project, click **+ New** → **Database** → **MySQL**
2. Wait for MySQL to provision

**4. Link database to API service**
1. Click your **API service** (not MySQL)
2. Go to **Variables** tab
3. Click **+ New Variable** → **Add Reference** (or use raw vars):

| Variable | Value |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `docker` |
| `JWT_SECRET` | any random string 32+ chars |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://${{MYSQL.MYSQLHOST}}:${{MYSQL.MYSQLPORT}}/${{MYSQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `SPRING_DATASOURCE_USERNAME` | `${{MYSQL.MYSQLUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | `${{MYSQL.MYSQLPASSWORD}}` |

> Replace `MYSQL` with your MySQL service name if Railway named it differently (e.g. `MySQL` → use `${{MySQL.MYSQLHOST}}`).

**5. Expose public URL**
1. API service → **Settings** → **Networking** → **Generate Domain**
2. Copy the URL, e.g. `https://erp-demo-production-xxxx.up.railway.app`

**6. Verify**
```bash
curl https://YOUR-RAILWAY-URL/health
curl https://YOUR-RAILWAY-URL/api/v1/bootstrap
```

**7. Share with reviewers**
- **Swagger UI:** `https://YOUR-RAILWAY-URL/swagger-ui/index.html`
- **Bootstrap (get tenant ID):** `https://YOUR-RAILWAY-URL/api/v1/bootstrap`
- **Health:** `https://YOUR-RAILWAY-URL/health`

### Demo users (same as local)
| Email | Password |
|-------|----------|
| creator@acme.com | creator123 |
| approver@acme.com | approver123 |
| finance@acme.com | finance123 |

---

## Option B: Quick temporary URL (ngrok — ~5 minutes)

Good for a live demo call, **not** for a permanent assignment link (URL changes each session).

```bash
# Terminal 1 — run app locally
cd /Users/sameer/code/erp_demo
docker compose up

# Terminal 2 — expose port 8080
brew install ngrok   # if needed
ngrok http 8080
```

Copy the `https://xxxx.ngrok-free.app` URL and share it.  
Swagger: `https://xxxx.ngrok-free.app/swagger-ui/index.html`

---

## Option C: Render.com

1. Push to GitHub (same as step 1 above)
2. [render.com](https://render.com) → **New** → **Web Service** → connect repo
3. **Runtime:** Docker
4. Add a MySQL instance (Render MySQL is paid; use [Railway MySQL](https://railway.app) or [PlanetScale](https://planetscale.com) externally and set `SPRING_DATASOURCE_*` env vars)
5. Set env vars from `render.yaml`
6. Deploy → get `https://erp-demo.onrender.com`

**Caveat:** Free Render services **sleep after 15 min** — first request takes ~30s to wake up.

---

## What to put in your assignment submission

```markdown
## Live Demo
- **Swagger UI:** https://YOUR-URL/swagger-ui/index.html
- **API bootstrap:** https://YOUR-URL/api/v1/bootstrap
- **GitHub:** https://github.com/YOUR_USERNAME/erp-demo

## How to test
1. Open Swagger UI
2. GET /api/v1/bootstrap → copy tenant_id
3. POST /api/v1/auth/login with X-Tenant-Id header (or tenant_id query param)
4. Authorize with JWT, then run invoice/payment endpoints
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| App crashes on start | Check Railway logs; verify `SPRING_DATASOURCE_URL` points to MySQL |
| 502 / not responding | Wait 1–2 min after deploy; check `/health` |
| Empty bootstrap | DB not seeded — restart deploy; seeder runs on first empty DB |
| Swagger auth fails | Use `tenant_id` query param + Authorize with JWT |

---

## Cost

- **Railway:** ~$5 free credit/month (enough for assignment demo for weeks)
- **ngrok:** Free tier works for short demos
- **Render free:** $0 but sleeps when idle
