const { Pool } = require("pg");

const NY_TIMEZONE = "America/New_York";
const DEFAULT_SYMBOLS =
  "AAPL,MSFT,AMZN,GOOGL,NVDA,TSLA,META,BRK.B,JPM,V,MA,UNH,HD,COST,AVGO,LLY,XOM,KO,PEP,JNJ";

let pool;

function getPool() {
  if (pool) {
    return pool;
  }

  if (!process.env.DATABASE_URL) {
    throw new Error("DATABASE_URL is not set");
  }

  pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: process.env.PGSSL_DISABLE === "true" ? false : { rejectUnauthorized: false }
  });

  return pool;
}

function toNyDateString(dateLike) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: NY_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(dateLike);
}

function toUpperSymbols(raw) {
  return raw
    .split(",")
    .map((s) => s.trim().toUpperCase())
    .filter(Boolean);
}

function getSymbols() {
  return toUpperSymbols(process.env.MARKETDATA_SYMBOLS || DEFAULT_SYMBOLS);
}

async function fetchDailyCandles(symbol, fromEpoch, toEpoch, apiKey) {
  const url = new URL("https://finnhub.io/api/v1/stock/candle");
  url.searchParams.set("symbol", symbol);
  url.searchParams.set("resolution", "D");
  url.searchParams.set("from", String(fromEpoch));
  url.searchParams.set("to", String(toEpoch));

  const response = await fetch(url, {
    headers: {
      "X-Finnhub-Token": apiKey
    }
  });

  if (!response.ok) {
    throw new Error(`Finnhub error: HTTP ${response.status}`);
  }

  return response.json();
}

async function ensureSchema(client) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS price_candle (
      id           BIGSERIAL PRIMARY KEY,
      symbol       VARCHAR(10)   NOT NULL,
      trade_date   DATE          NOT NULL,
      open         NUMERIC(19,6) NOT NULL,
      high         NUMERIC(19,6) NOT NULL,
      low          NUMERIC(19,6) NOT NULL,
      close        NUMERIC(19,6) NOT NULL,
      volume       BIGINT        NOT NULL,
      adjusted     BOOLEAN       NOT NULL DEFAULT FALSE,
      source       VARCHAR(20)   NOT NULL DEFAULT 'FINNHUB',
      created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
      CONSTRAINT uq_price_candle_symbol_date UNIQUE (symbol, trade_date)
    );
  `);

  await client.query(`
    CREATE TABLE IF NOT EXISTS pipeline_run (
      id                BIGSERIAL PRIMARY KEY,
      run_date          DATE         NOT NULL,
      status            VARCHAR(20)  NOT NULL,
      symbols_expected  INTEGER      NOT NULL,
      symbols_processed INTEGER      NOT NULL,
      error_message     TEXT,
      started_at        TIMESTAMPTZ  NOT NULL,
      finished_at       TIMESTAMPTZ
    );
  `);
}

function setCors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
}

module.exports = async function handler(req, res) {
  setCors(res);

  if (req.method === "OPTIONS") {
    return res.status(204).end();
  }

  if (req.method !== "POST") {
    res.setHeader("Allow", "POST, OPTIONS");
    return res.status(405).json({ error: "Method Not Allowed" });
  }

  if (!process.env.FINNHUB_API_KEY || !process.env.FINNHUB_API_KEY.trim()) {
    return res.status(500).json({ error: "FINNHUB_API_KEY is missing/blank" });
  }

  let client;
  const startedAt = new Date();
  const runDate = toNyDateString(startedAt);
  const symbols = getSymbols();
  const expected = symbols.length;
  let processed = 0;
  let status = "SUCCESS";
  let errorMessage = null;

  const toEpoch = Math.floor(Date.now() / 1000);
  const fromEpoch = toEpoch - 60 * 24 * 60 * 60;

  try {
    client = await getPool().connect();
    await client.query("BEGIN");
    await ensureSchema(client);

    for (const symbol of symbols) {
      try {
        const payload = await fetchDailyCandles(
          symbol,
          fromEpoch,
          toEpoch,
          process.env.FINNHUB_API_KEY.trim()
        );

        if (!payload || !payload.s || payload.s.toLowerCase() !== "ok") {
          continue;
        }

        const timestamps = Array.isArray(payload.t) ? payload.t : [];
        const opens = Array.isArray(payload.o) ? payload.o : [];
        const highs = Array.isArray(payload.h) ? payload.h : [];
        const lows = Array.isArray(payload.l) ? payload.l : [];
        const closes = Array.isArray(payload.c) ? payload.c : [];
        const volumes = Array.isArray(payload.v) ? payload.v : [];

        for (let i = 0; i < timestamps.length; i += 1) {
          const tradeDate = toNyDateString(new Date(Number(timestamps[i]) * 1000));

          await client.query(
            `
              INSERT INTO price_candle
                (symbol, trade_date, open, high, low, close, volume, adjusted, source)
              VALUES
                ($1, $2, $3, $4, $5, $6, $7, false, 'FINNHUB')
              ON CONFLICT (symbol, trade_date) DO NOTHING
            `,
            [
              symbol,
              tradeDate,
              Number(opens[i]),
              Number(highs[i]),
              Number(lows[i]),
              Number(closes[i]),
              Number(volumes[i])
            ]
          );
        }

        processed += 1;
      } catch (error) {
        status = "PARTIAL";
        errorMessage = `At least one symbol failed. Last error: ${error.message}`;
      }
    }

    if (processed === 0 && expected > 0) {
      status = "FAILED";
    }

    const finishedAt = new Date();
    const runInsert = await client.query(
      `
        INSERT INTO pipeline_run
          (run_date, status, symbols_expected, symbols_processed, error_message, started_at, finished_at)
        VALUES
          ($1, $2, $3, $4, $5, $6, $7)
        RETURNING
          id,
          run_date AS "runDate",
          status,
          symbols_expected AS "symbolsExpected",
          symbols_processed AS "symbolsProcessed",
          error_message AS "errorMessage",
          started_at AS "startedAt",
          finished_at AS "finishedAt"
      `,
      [runDate, status, expected, processed, errorMessage, startedAt, finishedAt]
    );

    await client.query("COMMIT");
    return res.status(200).json(runInsert.rows[0]);
  } catch (error) {
    if (client) {
      try {
        await client.query("ROLLBACK");
      } catch {
        // Ignore rollback errors.
      }
    }

    return res.status(500).json({
      error: "Ingestion failed",
      details: error.message
    });
  } finally {
    if (client) {
      client.release();
    }
  }
};
