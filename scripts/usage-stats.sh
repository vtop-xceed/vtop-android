#!/bin/sh
# Fast usage stats for the vtop-usage Worker (D1, remote).
# Requires: wrangler + jq on PATH, wrangler logged in.
set -eu

DB=vtop-usage
q() { out=$(wrangler d1 execute "$DB" --remote --json --command="$1" | jq -r "$2"); [ -n "$out" ] && printf "%s\n" "$out" || echo "  (none)"; }

today_sql='SELECT day, version, COUNT(*) AS devices FROM pings WHERE day >= date('"'"'now'"'"') GROUP BY day, version'
daily_sql='SELECT day, version, SUM(devices) AS devices FROM (SELECT day, version, devices FROM agg_daily WHERE day >= date('"'"'now'"'"', '"'"'-30 days'"'"') UNION ALL SELECT day, version, COUNT(*) FROM pings GROUP BY day, version) GROUP BY day, version ORDER BY day DESC'
total_sql='SELECT version, SUM(devices) AS devices FROM (SELECT day, version, devices FROM agg_daily WHERE day < date('"'"'now'"'"') UNION ALL SELECT day, version, COUNT(*) FROM pings GROUP BY day, version) GROUP BY version ORDER BY devices DESC'

echo "== today =="
q "$today_sql" '.[0].results[] | "  \(.day)  v\(.version)  \(.devices) dev"'
echo "== last 30 days =="
q "$daily_sql" '.[0].results[] | "  \(.day)  v\(.version)  \(.devices) dev"'
echo "== all-time =="
q "$total_sql" '.[0].results[] | "  v\(.version)  \(.devices) dev"'
