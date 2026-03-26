module.exports = function handler(_req, res) {
  return res.status(200).json({
    status: "ok",
    service: "marketdata",
    runtime: "vercel-node",
    timestamp: new Date().toISOString()
  });
};
