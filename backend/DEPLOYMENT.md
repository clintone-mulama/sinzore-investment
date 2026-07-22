# Sinzore Investment Platform Backend - Deployment Guide (Google Cloud Run)

This guide details the prerequisites, configuration steps, deployment commands, verification procedures, and troubleshooting instructions for deploying the production-grade Sinzore Investment backend service to Google Cloud Run.

---

## 1. Prerequisites

Before deploying, ensure you have:
1. **Google Cloud Platform (GCP) Account** with an active project and billing enabled.
2. **Google Cloud SDK (`gcloud` CLI)** installed and authenticated:
   ```bash
   gcloud auth login
   gcloud config set project YOUR_GCP_PROJECT_ID
   ```
3. **Required GCP APIs Enabled**:
   ```bash
   gcloud services enable \
     run.googleapis.com \
     containerregistry.googleapis.com \
     cloudbuild.googleapis.com \
     secretmanager.googleapis.com
   ```
4. **Firebase Admin Credentials** or Cloud Run Service Account permissions (`Firebase Admin` / `Cloud Datastore User`).

---

## 2. Environment Variables & Configuration

Cloud Run injects `PORT=8080` automatically. Configure the remaining required variables either via Google Secret Manager (recommended for credentials) or direct environment flags (`--set-env-vars`).

### Required Production Environment Variables

| Variable Name | Required | Default / Format | Description |
|---|---|---|---|
| `NODE_ENV` | Yes | `production` | Enables production mode optimizations |
| `PORT` | Yes (Auto) | `8080` | Port injected automatically by Cloud Run |
| `FIREBASE_PROJECT_ID` | Yes | `sinzore-investment` | Firebase GCP Project ID |
| `FIREBASE_CLIENT_EMAIL` | Recommended | `...` | Firebase Service Account Email |
| `FIREBASE_PRIVATE_KEY` | Recommended | `-----BEGIN PRIVATE KEY-----...` | Firebase Service Account Private Key |
| `MP_ENVIRONMENT` | Yes | `production` | `sandbox` or `production` for Daraja |
| `MP_CONSUMER_KEY` | Yes | `...` | Safaricom Daraja Consumer Key |
| `MP_CONSUMER_SECRET` | Yes | `...` | Safaricom Daraja Consumer Secret |
| `MP_SHORTCODE` | Yes | `174379` | Safaricom Business Shortcode |
| `MP_PASSKEY` | Yes | `...` | Safaricom Lipa na M-Pesa Online Passkey |
| `MP_CALLBACK_URL` | Yes | `https://<YOUR_CLOUD_RUN_URL>/api/mpesa/callback` | Public webhook callback URL |
| `CORS_ORIGIN` | Yes | `*` | Allowed CORS origins for frontend client |
| `LOG_LEVEL` | No | `info` | Logging verbosity (`error`, `warn`, `info`, `debug`) |

---

## 3. Deployment Commands

### Option A: Direct gcloud CLI Deploy (Recommended for Fast Deployment)

Execute from the repository root:

```bash
gcloud run deploy sinzore-investment-backend \
  --source ./backend \
  --region us-central1 \
  --platform managed \
  --allow-unauthenticated \
  --min-instances 0 \
  --max-instances 10 \
  --cpu 1 \
  --memory 512Mi \
  --set-env-vars "NODE_ENV=production,FIREBASE_PROJECT_ID=sinzore-investment,MP_ENVIRONMENT=production,CORS_ORIGIN=*"
```

### Option B: Automated Cloud Build Deployment

Trigger automated build and deployment via Cloud Build:

```bash
gcloud builds submit --config ./backend/cloudbuild.yaml .
```

---

## 4. Post-Deployment Verification

Once deployed, Google Cloud Run returns a service URL (e.g. `https://sinzore-investment-backend-xyz-uc.a.run.app`).

Verify backend operation using cURL or HTTP client:

### 1. Root Endpoint Health Check
```bash
curl -X GET https://<YOUR_CLOUD_RUN_URL>/api/health
```
**Expected Output:**
```json
{
  "success": true,
  "status": "HEALTHY",
  "data": {
    "serverStatus": "HEALTHY",
    "timestamp": "2026-07-22T05:35:00.000Z",
    "version": "1.0.0-production",
    "environment": "production"
  }
}
```

### 2. Comprehensive System Health Check
```bash
curl -X GET https://<YOUR_CLOUD_RUN_URL>/api/system/health
```

---

## 5. Troubleshooting & Operations

### Viewing Logs in Real-Time
```bash
gcloud logs tail --project YOUR_GCP_PROJECT_ID "resource.type=cloud_run_revision AND resource.labels.service_name=sinzore-investment-backend"
```

### Service Account Permissions
Ensure the default Cloud Run service account or assigned service account has the following IAM roles:
- **Firebase Admin** (`roles/firebase.admin`) or **Cloud Datastore User** (`roles/datastore.user`)
- **Cloud Logs Writer** (`roles/logging.logWriter`)

### Cold Start Mitigation
If response latency on the initial request is sensitive, set `--min-instances 1` on Cloud Run to maintain an active instance.
