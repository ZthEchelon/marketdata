# marketdata

This repository contains:

- A Spring Boot implementation under `src/main/java/...` (good for JVM hosts like Render/Railway/Fly.io)
- A Vercel-compatible Node.js serverless adapter under `api/...` (used when deploying to Vercel)

## Why you got `404 NOT_FOUND` on Vercel

Your previous deployment had no Vercel-routable output/functions, so Vercel completed the build and served an empty deployment.

## What was added for Vercel

- `public/index.html` (root route, so `/` is no longer empty)
- `api/health.js` (`GET /api/health`)
- `api/v1/admin/ingest.js` (`POST /api/v1/admin/ingest`)
- `vercel.json` (sets `maxDuration` for ingestion function)
- `package.json` (`pg` dependency for Postgres access)

## Required Vercel environment variables

- `DATABASE_URL` (Postgres connection string)
- `FINNHUB_API_KEY` (Finnhub API key)
- Optional: `MARKETDATA_SYMBOLS` (comma-separated list)
- Optional: `PGSSL_DISABLE=true` (only if your DB does not require SSL)

## Deploy

1. Link repo (first time): `vercel link`
2. Set env vars in Vercel project settings
3. Deploy: `vercel --prod`

## Recommended long-term architecture

For production, the cleaner option is to run the Spring Boot app on a JVM-native platform and keep Vercel for frontend/static workloads.
