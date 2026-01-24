# MarketLens (marketdata)

MarketLens is a Spring Boot 4 (Java 21) market data pipeline and analytics service with a modern UX, API key auth, rate limiting, OpenAPI docs, data quality checks, and operational observability.

## Highlights

- **Market data ingestion** with idempotent runs, retries, and backfills.
- **Technical indicators** (RSI, MACD) and cursor-style pagination.
- **Corporate actions** and **split-adjusted prices**.
- **Market calendar** (NYSE trading days + holidays).
- **Data quality reports** (missing days, duplicates, outliers).
- **Rate limiting** and **daily quota tracking**.
- **OpenAPI/Swagger** docs and **Prometheus** metrics.
- **Structured JSON logging**.
- **Caching** for calendar and adjusted prices.
- **Distributed tracing** via OTLP.
- **Partitioned price candles** for scale.

## Tech Stack

- Java 21, Spring Boot 4.0.1
- PostgreSQL + Flyway
- Spring Data JPA
- Spring Security (API key auth)
- Springdoc OpenAPI
- Micrometer Prometheus
- Micrometer Tracing (OTLP)
- Caffeine cache
- Bucket4j rate limiting

## Quick Start

### Prerequisites

- Java 21
- Maven (or use `./mvnw`)
- PostgreSQL 13+

### Configure environment

Set the following environment variables (or update `src/main/resources/application.yml`):

- `MARKETDATA_DB_URL` (default: `jdbc:postgresql://localhost:5433/marketdata`)
- `MARKETDATA_DB_USER` (default: `marketdata`)
- `MARKETDATA_DB_PASSWORD` (default: `marketdata`)
- `MARKETDATA_ADMIN_KEY` (admin API key)
- `MARKETDATA_USER_KEY` (user API key)
- `OTEL_EXPORTER_OTLP_ENDPOINT` (optional tracing endpoint)

### Run the app

```bash
./mvnw spring-boot:run
```

The app will start on `http://localhost:8080`.

## UX Pages

- `http://localhost:8080/` – MarketLens dashboard
- `http://localhost:8080/watchlist.html`
- `http://localhost:8080/indicators.html`
- `http://localhost:8080/runs.html`
- `http://localhost:8080/quality.html`
- `http://localhost:8080/actions.html`
- `http://localhost:8080/calendar.html`
- `http://localhost:8080/keys.html`
- `http://localhost:8080/status.html`

> The UI uses the **MarketLens API key** (default `change-me-user`) and sends it as `X-API-Key` for all API calls.

## API Documentation

- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Documentation

- Project docs live in [docs/README.md](docs/README.md)

## Authentication & Authorization

All API calls under `/api/**` require an API key header:

```
X-API-Key: <your-key>
```

Roles are derived from the configured key list:

- **ADMIN**: access to `/api/v1/admin/**` and `/actuator/prometheus`
- **USER**: access to `/api/**`

Default local keys (update for production):

- `change-me-user`
- `change-me-admin`

Public endpoints include `/`, `/index.html`, `/watchlist.html`, `/indicators.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/api/v1/health`, `/actuator/health/**`, `/actuator/info/**`.

## Rate Limiting & Quotas

Rate limiting is enforced on `/api/**` and returns standard headers:

- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`

Daily quota headers are also included:

- `X-Quota-Limit`
- `X-Quota-Remaining`

## Core API Endpoints

### Health

- `GET /api/v1/health`

### Ingestion Runs

- `POST /api/v1/ingestion/run`
- `POST /api/v1/ingestion/backfill`
- `GET /api/v1/ingestion/runs`
- `GET /api/v1/ingestion/runs/latest`
- `GET /api/v1/ingestion/runs/{id}`

### Watchlist

- `GET /api/v1/watchlist`
- `POST /api/v1/watchlist`
- `DELETE /api/v1/watchlist/{symbol}`

### Indicators

- `GET /api/v1/indicators/{symbol}`
- `GET /api/v1/indicators/{symbol}/{type}`

### Market Data

- `GET /api/v1/market/adjusted?symbol=MSFT&from=2024-01-01&to=2024-12-31`

### Corporate Actions

- `GET /api/v1/corporate-actions/{symbol}`
- `POST /api/v1/corporate-actions`

### Market Calendar

- `GET /api/v1/calendar/nyse?from=2025-01-01&to=2025-12-31`

### Data Quality

- `GET /api/v1/quality/report?symbol=MSFT&from=2024-01-01&to=2024-12-31`

### API Key Admin

- `POST /api/v1/admin/api-key`
- `GET /api/v1/admin/keys`
- `POST /api/v1/admin/keys`
- `POST /api/v1/admin/keys/{keyId}/rotate`
- `DELETE /api/v1/admin/keys/{keyId}`

## Example Requests

### Ingest latest data

```bash
curl -X POST http://localhost:8080/api/v1/ingestion/run \
  -H 'X-API-Key: YOUR_KEY'
```

### Fetch indicators

```bash
curl http://localhost:8080/api/v1/indicators/MSFT \
  -H 'X-API-Key: YOUR_KEY'
```

### Adjusted prices

```bash
curl "http://localhost:8080/api/v1/market/adjusted?symbol=MSFT&from=2024-01-01&to=2024-12-31" \
  -H 'X-API-Key: YOUR_KEY'
```

## Database & Migrations

Flyway migrations are located in `src/main/resources/db/migration`.

Recent changes include:

- Pipeline run metadata and idempotency key
- Corporate actions table
- Yearly partitioning of `price_candle`

## Observability

- JSON structured logs via `logback-spring.xml`
- Prometheus metrics at `/actuator/prometheus` (ADMIN only)
- OTLP tracing via `OTEL_EXPORTER_OTLP_ENDPOINT`

## Caching

- Calendar and adjusted prices are cached in-memory (Caffeine)

## Configuration Notes

All settings live in `src/main/resources/application.yml`, including:

- API key list and roles
- Rate limits & quotas
- Retention settings
- NYSE holidays
- OpenAPI paths

## Development Tips

- If you change migrations locally, reset your database or adjust Flyway history.
- Use the UI “Set API Key” action to validate and save a provider API key.

## CI

- GitHub Actions workflow in [.github/workflows/ci.yml](.github/workflows/ci.yml)

## License

MIT (add or update as needed).
