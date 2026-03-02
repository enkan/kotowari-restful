#!/usr/bin/env bash
# demo.sh — Demonstrates all kotowari-restful example API operations
#
# Prerequisites: start the server first
#   mvn compile -Pdev && mvn exec:java -Pdev
#
# Usage:
#   chmod +x demo.sh && ./demo.sh

BASE_URL="http://localhost:3000"
BOLD='\033[1m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[0;33m'
RESET='\033[0m'

step() {
    echo
    echo -e "${BOLD}${CYAN}━━━ $1 ━━━${RESET}"
}

note() {
    echo -e "${YELLOW}# $1${RESET}"
}

# Run curl and pretty-print body + status on separate lines.
# Usage: run <description> <curl args...>
run() {
    local desc="$1"; shift
    echo -e "${GREEN}\$ curl $*${RESET}"
    local tmpfile
    tmpfile=$(mktemp)
    local status
    status=$(curl -s -o "$tmpfile" -w '%{http_code}' "$@")
    local body
    body=$(cat "$tmpfile"); rm -f "$tmpfile"
    if [ -n "$body" ]; then
        echo "$body" | jq . 2>/dev/null || echo "$body"
    fi
    echo "HTTP $status"
    echo
}

# ─────────────────────────────────────────────
# 1. GET /addresses  — list seeded records
# ─────────────────────────────────────────────
step "GET /addresses — list all addresses (seeded by Flyway migration)"
note "Returns the two records inserted by V1__CreateAddress (Tokyo + Osaka)"
run "GET /addresses" \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses"

# ─────────────────────────────────────────────
# 2. GET /addresses  — pagination (limit/offset)
# ─────────────────────────────────────────────
step "GET /addresses?limit=1&offset=0 — pagination via AddressSearchParams"
note "AddressSearchParams.limit/offset are bound from query parameters by beansConverter"
run "GET /addresses?limit=1&offset=0" \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses?limit=1&offset=0"

step "GET /addresses?limit=1&offset=1 — next page"
run "GET /addresses?limit=1&offset=1" \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses?limit=1&offset=1"

# ─────────────────────────────────────────────
# 3. POST /addresses — create a valid address
# ─────────────────────────────────────────────
step "POST /addresses — create a new address (201 Created)"
note "SerDesMiddleware deserializes the JSON body; MALFORMED validates it before POST action"
run "POST /addresses (DE)" \
  -X POST \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{"countryCode":"DE","zip":"10115","city":"Berlin","street":"Unter den Linden 1"}' \
  "${BASE_URL}/addresses"

# Capture ID for subsequent single-item tests
NEW_ID=$(curl -s \
  -X POST \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{"countryCode":"US","zip":"10001","city":"New York","street":"5th Avenue 350"}' \
  -o - \
  "${BASE_URL}/addresses" | jq -r '.id')

echo -e "${YELLOW}# Captured new address id = ${NEW_ID}${RESET}"

# ─────────────────────────────────────────────
# 4. POST /addresses — 400 validation failure
# ─────────────────────────────────────────────
step "POST /addresses — 400 Bad Request (validation failure: missing required fields)"
note "BeansValidator returns ConstraintViolations → Problem.fromViolations() → 400"
run "POST /addresses (invalid)" \
  -X POST \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{"city":"Incomplete"}' \
  "${BASE_URL}/addresses"

# ─────────────────────────────────────────────
# 5. POST /addresses — 405 Method Not Allowed
# ─────────────────────────────────────────────
step "PATCH /addresses — 405 Method Not Allowed"
note "@AllowedMethods({\"GET\",\"POST\"}) on AddressesResource rejects PATCH"
run "PATCH /addresses" \
  -X PATCH \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses"

# ─────────────────────────────────────────────
# 6. GET /addresses/:id — single item
# ─────────────────────────────────────────────
step "GET /addresses/1 — fetch a single address (200 OK)"
note "EXISTS decision point: em.find() loads entity and stores it in RestContext via putValue()"
run "GET /addresses/1" \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses/1"

# ─────────────────────────────────────────────
# 7. GET /addresses/:id — 404 Not Found
# ─────────────────────────────────────────────
step "GET /addresses/9999 — 404 Not Found"
note "EXISTS returns false → decision graph short-circuits to HANDLE_NOT_FOUND"
run "GET /addresses/9999" \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses/9999"

# ─────────────────────────────────────────────
# 8. PUT /addresses/:id — update
# ─────────────────────────────────────────────
step "PUT /addresses/${NEW_ID} — update the address (200 OK)"
note "MALFORMED(PUT) validates body; PUT action copies body onto existing entity via beansConverter.copy()"
run "PUT /addresses/${NEW_ID}" \
  -X PUT \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{"countryCode":"US","zip":"10001","city":"New York","street":"Broadway 1450"}' \
  "${BASE_URL}/addresses/${NEW_ID}"

# ─────────────────────────────────────────────
# 9. PUT /addresses/:id — 400 validation failure
# ─────────────────────────────────────────────
step "PUT /addresses/${NEW_ID} — 400 Bad Request (countryCode exceeds 2 chars)"
note "MALFORMED(PUT) fires only for PUT; @Size(min=2,max=2) on countryCode triggers violation"
run "PUT /addresses/${NEW_ID} (invalid countryCode)" \
  -X PUT \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{"countryCode":"USA","zip":"10001","city":"New York","street":"Broadway 1450"}' \
  "${BASE_URL}/addresses/${NEW_ID}"

# ─────────────────────────────────────────────
# 10. DELETE /addresses/:id
# ─────────────────────────────────────────────
step "DELETE /addresses/${NEW_ID} — delete the address (204 No Content)"
note "DELETE action calls em.remove(); transaction committed by NonJtaTransactionMiddleware"
run "DELETE /addresses/${NEW_ID}" \
  -X DELETE \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses/${NEW_ID}"

# ─────────────────────────────────────────────
# 11. GET after DELETE — confirms 404
# ─────────────────────────────────────────────
step "GET /addresses/${NEW_ID} — 404 after deletion"
note "Confirms the record was removed; EXISTS returns false"
run "GET /addresses/${NEW_ID} (after delete)" \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses/${NEW_ID}"

# ─────────────────────────────────────────────
# 12. GET /addresses/:id — 500 via HANDLE_EXCEPTION
# ─────────────────────────────────────────────
step "GET /addresses/notanumber — 500 Internal Server Error (HANDLE_EXCEPTION)"
note "Long.valueOf(\"notanumber\") throws NumberFormatException in EXISTS → routed through HANDLE_EXCEPTION handler"
run "GET /addresses/notanumber" \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses/notanumber"

# ─────────────────────────────────────────────
# 13. Final state — list all remaining addresses
# ─────────────────────────────────────────────
step "GET /addresses — final list after all mutations"
run "GET /addresses (final)" \
  -H 'Accept: application/json' \
  "${BASE_URL}/addresses"

echo -e "${BOLD}${GREEN}Demo complete.${RESET}"
