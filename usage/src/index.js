/**
 * vtop-usage: anonymous "is the app alive" counter for VTOP++ Android.
 *
 * The app sends one ping per UTC day with { v, d, h } where
 * h = sha256(installId + ":" + d). Nothing else: no IP, no User-Agent,
 * no persistent id server-side beyond the daily hash, which cannot be
 * linked to any other day. Raw rows are purged after 31 days; a
 * daily roll-up keeps all-time counts in agg_daily.
 */

const DAY_RE = /^\d{4}-\d{2}-\d{2}$/;
const HEX_RE = /^[0-9a-f]{64}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return new Response("vtop usage service\n", { status: 200 });
    }

    if (request.method === "POST" && url.pathname === "/ping") {
      let body;
      try {
        body = await request.json();
      } catch {
        return new Response("bad json\n", { status: 400 });
      }

      const version = typeof body?.v === "string" ? body.v.trim().slice(0, 32) : "";
      const day = typeof body?.d === "string" ? body.d : "";
      const hash = typeof body?.h === "string" ? body.h.trim().toLowerCase() : "";

      if (!version || !DAY_RE.test(day) || !HEX_RE.test(hash)) {
        return new Response("bad request\n", { status: 400 });
      }

      // Day must be today or yesterday (UTC); tomorrow tolerates clock skew.
      const now = Date.now();
      const shifted = [-86400000, 0, 86400000].map((ms) => new Date(now + ms).toISOString().slice(0, 10));
      if (!shifted.includes(day)) {
        return new Response("day out of range\n", { status: 400 });
      }

      await env.DB.prepare("INSERT OR IGNORE INTO pings (day, h, version) VALUES (?, ?, ?)")
        .bind(day, hash, version)
        .run();
      return new Response(null, { status: 204 });
    }

    return new Response("not found\n", { status: 404 });
  },

  async scheduled(_event, env, ctx) {
    // Re-roll the aggregate from whatever raw rows remain, then purge rows
    // older than 31 days. Runs daily; idempotent.
    ctx.waitUntil(
      env.DB.batch([
        env.DB.prepare(
          `INSERT INTO agg_daily (day, version, devices)
           SELECT day, version, COUNT(*) FROM pings
           GROUP BY day, version
           ON CONFLICT (day, version) DO UPDATE SET devices = excluded.devices`,
        ),
        env.DB.prepare("DELETE FROM pings WHERE day < date('now', '-31 days')"),
      ]),
    );
  },
};
