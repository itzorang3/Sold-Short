#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Sold Short — End-to-End Connection Test
# Simulates two players connecting, creating/joining a league, drafting picks,
# and completing a round evaluation.
#
# Usage:  bash test_connection.sh [SERVER_URL]
# Default server: http://localhost:8080
# ─────────────────────────────────────────────────────────────────────────────

SERVER="${1:-http://localhost:8080}"
PASS=0
FAIL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "${GREEN}  ✓  $1${NC}"; ((PASS++)); }
fail() { echo -e "${RED}  ✗  $1${NC}"; ((FAIL++)); }
info() { echo -e "${YELLOW}  ▸  $1${NC}"; }

# ── helpers ───────────────────────────────────────────────────────────────────

get()  { curl -s -o /tmp/ss_resp -w "%{http_code}" "$SERVER$1"; }
post() { curl -s -o /tmp/ss_resp -w "%{http_code}" -X POST -H "Content-Type: application/json" -d "$2" "$SERVER$1"; }
body() { cat /tmp/ss_resp; }
jq_get() { body | python3 -c "import sys,json; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }

echo ""
echo "═══════════════════════════════════════════════════"
echo "  Sold Short — Connection Test  →  $SERVER"
echo "═══════════════════════════════════════════════════"
echo ""

# ── 1. Health check ───────────────────────────────────────────────────────────
info "1/9  Health check — GET /api/market/stocks"
CODE=$(get "/api/market/stocks")
if [ "$CODE" = "200" ]; then
    COUNT=$(body | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
    ok "Server reachable — $COUNT tickers in stock pool"
else
    fail "Server unreachable (HTTP $CODE) — is it running?"
    echo ""
    echo "Start the server with:"
    echo "  cd server && mvn spring-boot:run"
    exit 1
fi

# ── 2. Register Player 1 (host) ───────────────────────────────────────────────
info "2/9  Register host — POST /api/auth/register"
TS=$(date +%s)
P1_USER="host_test_$TS"
P2_USER="player_test_$TS"

CODE=$(post "/api/auth/register" "{\"username\":\"$P1_USER\",\"password\":\"pass123\",\"email\":\"\"}")
if [ "$CODE" = "200" ]; then
    P1_ID=$(jq_get "['id']")
    ok "Player 1 registered: $P1_USER (id=$P1_ID)"
else
    fail "Player 1 registration failed (HTTP $CODE): $(body)"
fi

# ── 3. Register Player 2 ──────────────────────────────────────────────────────
info "3/9  Register player — POST /api/auth/register"
CODE=$(post "/api/auth/register" "{\"username\":\"$P2_USER\",\"password\":\"pass123\",\"email\":\"\"}")
if [ "$CODE" = "200" ]; then
    P2_ID=$(jq_get "['id']")
    ok "Player 2 registered: $P2_USER (id=$P2_ID)"
else
    fail "Player 2 registration failed (HTTP $CODE): $(body)"
fi

# ── 4. Login both players ─────────────────────────────────────────────────────
info "4/9  Login both players"
CODE=$(post "/api/auth/login" "{\"username\":\"$P1_USER\",\"password\":\"pass123\"}")
[ "$CODE" = "200" ] && ok "Player 1 login OK" || fail "Player 1 login failed (HTTP $CODE)"

CODE=$(post "/api/auth/login" "{\"username\":\"$P2_USER\",\"password\":\"pass123\"}")
[ "$CODE" = "200" ] && ok "Player 2 login OK" || fail "Player 2 login failed (HTTP $CODE)"

# ── 5. Create league ──────────────────────────────────────────────────────────
info "5/9  Create league — POST /api/leagues"
CODE=$(post "/api/leagues" "{\"hostId\":$P1_ID,\"name\":\"Test League $TS\",\"maxPlayers\":4,\"startingLives\":3}")
if [ "$CODE" = "200" ]; then
    LEAGUE_ID=$(jq_get "['id']")
    JOIN_CODE=$(jq_get "['joinCode']")
    ok "League created (id=$LEAGUE_ID, joinCode=$JOIN_CODE)"
else
    fail "Create league failed (HTTP $CODE): $(body)"
fi

# ── 6. Player 2 joins ─────────────────────────────────────────────────────────
info "6/9  Join league — POST /api/leagues/0/join"
CODE=$(post "/api/leagues/0/join" "{\"userId\":$P2_ID,\"joinCode\":\"$JOIN_CODE\"}")
if [ "$CODE" = "200" ]; then
    ok "Player 2 joined league successfully"
else
    fail "Join league failed (HTTP $CODE): $(body)"
fi

# ── 7. Start draft ────────────────────────────────────────────────────────────
info "7/9  Start draft — POST /api/leagues/$LEAGUE_ID/draft/start"
CODE=$(post "/api/leagues/$LEAGUE_ID/draft/start" "{\"userId\":$P1_ID}")
if [ "$CODE" = "200" ]; then
    ROUND=$(jq_get "['currentRound']")
    ok "Draft started (round=$ROUND)"
else
    fail "Start draft failed (HTTP $CODE): $(body)"
fi

# ── 8. Both players submit picks ──────────────────────────────────────────────
info "8/9  Submit picks"
CODE=$(post "/api/leagues/$LEAGUE_ID/draft/pick" "{\"userId\":$P1_ID,\"ticker\":\"AAPL\"}")
[ "$CODE" = "200" ] && ok "Player 1 picked AAPL" || fail "Player 1 pick failed (HTTP $CODE): $(body)"

CODE=$(post "/api/leagues/$LEAGUE_ID/draft/pick" "{\"userId\":$P2_ID,\"ticker\":\"TSLA\"}")
[ "$CODE" = "200" ] && ok "Player 2 picked TSLA" || fail "Player 2 pick failed (HTTP $CODE): $(body)"

# verify all-submitted
CODE=$(get "/api/leagues/$LEAGUE_ID/draft/all-submitted?round=1")
ALL=$(body)
[ "$ALL" = "true" ] && ok "All picks confirmed submitted" || fail "All-submitted check failed (got '$ALL')"

# ── 9. Evaluate round ─────────────────────────────────────────────────────────
info "9/9  Evaluate round — POST /api/leagues/$LEAGUE_ID/evaluate"
CODE=$(post "/api/leagues/$LEAGUE_ID/evaluate" \
  "{\"hostId\":$P1_ID,\"prices\":{\"AAPL\":195.50,\"TSLA\":245.00}}")
if [ "$CODE" = "200" ]; then
    ENTRIES=$(body | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null)
    ok "Round evaluated — $ENTRIES leaderboard entries returned"

    echo ""
    echo "  Leaderboard:"
    body | python3 -c "
import sys, json
entries = json.load(sys.stdin)
for e in entries:
    hearts = '♥' * e.get('livesRemaining', 0)
    print(f\"    #{e['rank']}  {e['username']:<20} {e['ticker']:<6} {e['percentChange']:+.2f}%  {hearts}\")
" 2>/dev/null
else
    fail "Evaluate round failed (HTTP $CODE): $(body)"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════"
TOTAL=$((PASS + FAIL))
if [ "$FAIL" = "0" ]; then
    echo -e "${GREEN}  All $TOTAL tests passed ✓${NC}"
else
    echo -e "${RED}  $FAIL / $TOTAL tests FAILED${NC}"
fi
echo "═══════════════════════════════════════════════════"
echo ""
