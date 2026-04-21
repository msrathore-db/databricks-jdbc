#!/bin/bash
# Simba ODBC v2.10.0 — Full SPOG Matrix Test
# Usage: source ~/.zshrc && bash src/test/resources/odbc_full_matrix.sh
set -uo pipefail
source ~/.zshrc 2>/dev/null

ODBC_DRIVER="/Library/databricks/databricksodbc/lib/libdatabricksodbc.dylib"

# Staging
STG_SPOG="dogfood-spog.staging.azuredatabricks.net"
STG_LEGACY="adb-7064161269814046.2.staging.azuredatabricks.net"
STG_WH="/sql/1.0/warehouses/e256699345d1ac74"
STG_SPOG_PATH="${STG_WH}?o=7064161269814046"

# Prod
PROD_SPOG="peco.azuredatabricks.net"
PROD_LEGACY="adb-6436897454825492.12.azuredatabricks.net"
PROD_WH="/sql/1.0/warehouses/00adc7b6c00429b8"
PROD_SPOG_PATH="${PROD_WH}?o=6436897454825492"

# Credentials
STG_PAT="${DATABRICKS_DOGFOOD_WESTUS_STAGING_TOKEN:-}"
STG_M2M_ID="${DATABRICKS_DOGFOOD_AZURE_CLIENT_ID:-}"
STG_M2M_SEC="${DATABRICKS_DOGFOOD_AZURE_CLIENT_SECRET:-}"
STG_ENTRA_ID="${DATABRICKS_SPOG_ENTRA_TEST_CLIENT_ID:-}"
STG_ENTRA_SEC="${DATABRICKS_SPOG_ENTRA_TEST_CLIENT_SECRET:-}"

PROD_PAT="${DATABRICKS_PECOTESTING_TOKEN_PERSONAL:-}"
PROD_M2M_ID="${DATABRICKS_PECOTESTING_DATABRICKS_CLIENT_ID_MSR_SPN:-}"
PROD_M2M_SEC="${DATABRICKS_PECOTESTING_DATABRICKS_CLIENT_SECRET_MSR_SPN:-}"
PROD_ENTRA_ID="${DATABRICKS_AAD_CLIENT_ID:-}"
PROD_ENTRA_SEC="${DATABRICKS_AAD_CLIENT_SECRET:-}"

PASS=0; FAIL=0; SKIP=0; RESULTS=()
INI="/tmp/odbc_full_matrix.ini"

run_test() {
    local name="$1" dsn="$2"
    printf "  %-60s" "$name"
    local output
    # Use a unique sentinel value (12345678) so the PASS grep can't be fooled by stray "| 1" text.
    output=$(ODBCINI="$INI" DYLD_LIBRARY_PATH="/opt/homebrew/lib:/Library/databricks/databricksodbc/lib" isql -v "$dsn" -b <<< "SELECT 12345678 AS v" 2>&1)
    if echo "$output" | grep -q "| 12345678"; then
        echo "PASS"; PASS=$((PASS+1)); RESULTS+=("PASS  $name")
    else
        local err=$(echo "$output" | grep -i "error\|fail\|invalid\|unauthorized\|permission" | head -1 | cut -c1-180)
        echo "FAIL"; [ -n "$err" ] && echo "    -> $err"
        FAIL=$((FAIL+1)); RESULTS+=("FAIL  $name")
    fi
}
skip_test() { printf "  %-60s%s\n" "$1" "SKIP"; SKIP=$((SKIP+1)); RESULTS+=("SKIP  $1"); }

# Build DSN file
cat > "$INI" <<EOF
[StgSpogPAT]
Driver=$ODBC_DRIVER
Host=$STG_SPOG
Port=443
HTTPPath=$STG_SPOG_PATH
SSL=1
ThriftTransport=2
AuthMech=3
UID=token
PWD=$STG_PAT

[StgLegPAT]
Driver=$ODBC_DRIVER
Host=$STG_LEGACY
Port=443
HTTPPath=$STG_WH
SSL=1
ThriftTransport=2
AuthMech=3
UID=token
PWD=$STG_PAT

[StgSpogM2M]
Driver=$ODBC_DRIVER
Host=$STG_SPOG
Port=443
HTTPPath=$STG_SPOG_PATH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$STG_M2M_ID
Auth_Client_Secret=$STG_M2M_SEC

[StgLegM2M]
Driver=$ODBC_DRIVER
Host=$STG_LEGACY
Port=443
HTTPPath=$STG_WH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$STG_M2M_ID
Auth_Client_Secret=$STG_M2M_SEC

[StgLegEntra]
Driver=$ODBC_DRIVER
Host=$STG_LEGACY
Port=443
HTTPPath=$STG_WH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$STG_ENTRA_ID
Auth_Client_Secret=$STG_ENTRA_SEC

[StgLegEntraScope]
Driver=$ODBC_DRIVER
Host=$STG_LEGACY
Port=443
HTTPPath=$STG_WH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$STG_ENTRA_ID
Auth_Client_Secret=$STG_ENTRA_SEC
Auth_Scope=2ff814a6-3304-4ab8-85cb-cd0e6f879c1d/.default

[ProdLegPAT]
Driver=$ODBC_DRIVER
Host=$PROD_LEGACY
Port=443
HTTPPath=$PROD_WH
SSL=1
ThriftTransport=2
AuthMech=3
UID=token
PWD=$PROD_PAT

[ProdLegM2M]
Driver=$ODBC_DRIVER
Host=$PROD_LEGACY
Port=443
HTTPPath=$PROD_WH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$PROD_M2M_ID
Auth_Client_Secret=$PROD_M2M_SEC

[ProdLegEntra]
Driver=$ODBC_DRIVER
Host=$PROD_LEGACY
Port=443
HTTPPath=$PROD_WH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$PROD_ENTRA_ID
Auth_Client_Secret=$PROD_ENTRA_SEC

[ProdLegEntraScope]
Driver=$ODBC_DRIVER
Host=$PROD_LEGACY
Port=443
HTTPPath=$PROD_WH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$PROD_ENTRA_ID
Auth_Client_Secret=$PROD_ENTRA_SEC
Auth_Scope=2ff814a6-3304-4ab8-85cb-cd0e6f879c1d/.default

[StgSpogEntra]
Driver=$ODBC_DRIVER
Host=$STG_SPOG
Port=443
HTTPPath=$STG_SPOG_PATH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$STG_ENTRA_ID
Auth_Client_Secret=$STG_ENTRA_SEC

[ProdLegEntraDB]
Driver=$ODBC_DRIVER
Host=$PROD_LEGACY
Port=443
HTTPPath=$PROD_WH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=${DATABRICKS_PECOTESTING_MSR_ENTRA_SPN_CLIENT_ID:-}
Auth_Client_Secret=${DATABRICKS_PECOTESTING_MSR_ENTRA_SPN_CLIENT_SECRET:-}

[ProdSpogPAT]
Driver=$ODBC_DRIVER
Host=$PROD_SPOG
Port=443
HTTPPath=$PROD_SPOG_PATH
SSL=1
ThriftTransport=2
AuthMech=3
UID=token
PWD=$PROD_PAT

[ProdSpogEntra]
Driver=$ODBC_DRIVER
Host=$PROD_SPOG
Port=443
HTTPPath=$PROD_SPOG_PATH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$PROD_ENTRA_ID
Auth_Client_Secret=$PROD_ENTRA_SEC

[ProdSpogEntraScope]
Driver=$ODBC_DRIVER
Host=$PROD_SPOG
Port=443
HTTPPath=$PROD_SPOG_PATH
SSL=1
ThriftTransport=2
AuthMech=11
Auth_Flow=1
Auth_Client_ID=$PROD_ENTRA_ID
Auth_Client_Secret=$PROD_ENTRA_SEC
Auth_Scope=2ff814a6-3304-4ab8-85cb-cd0e6f879c1d/.default
EOF

echo ""
echo "================================================================"
echo "  Simba ODBC v2.10.0 — Full SPOG Matrix Test"
echo "================================================================"

echo ""
echo "--- STAGING ---"
[ -n "$STG_PAT" ] && run_test "Stg | PAT | SPOG" StgSpogPAT || skip_test "Stg | PAT | SPOG"
[ -n "$STG_PAT" ] && run_test "Stg | PAT | Legacy" StgLegPAT || skip_test "Stg | PAT | Legacy"
[ -n "$STG_M2M_ID" ] && run_test "Stg | DB M2M (dc8dd813) | SPOG" StgSpogM2M || skip_test "Stg | DB M2M | SPOG"
[ -n "$STG_M2M_ID" ] && run_test "Stg | DB M2M (dc8dd813) | Legacy" StgLegM2M || skip_test "Stg | DB M2M | Legacy"
[ -n "$STG_ENTRA_ID" ] && run_test "Stg | Azure AD (d7f11108, no scope) | SPOG" StgSpogEntra || skip_test "Stg | Azure AD | SPOG"
[ -n "$STG_ENTRA_ID" ] && run_test "Stg | Azure AD (d7f11108, no scope) | Legacy" StgLegEntra || skip_test "Stg | Azure AD | Legacy"
[ -n "$STG_ENTRA_ID" ] && run_test "Stg | Azure AD (d7f11108, Azure scope) | Legacy" StgLegEntraScope || skip_test "Stg | Azure AD scope | Legacy"

echo ""
echo "--- PROD ---"
[ -n "$PROD_PAT" ] && run_test "Prod | PAT | Legacy" ProdLegPAT || skip_test "Prod | PAT"
[ -n "$PROD_M2M_ID" ] && run_test "Prod | Entra SP (a6f72159, dose) | Legacy" ProdLegM2M || skip_test "Prod | Entra SP"
[ -n "$PROD_ENTRA_ID" ] && run_test "Prod | Azure AD (d154b9ed, no scope) | Legacy" ProdLegEntra || skip_test "Prod | Azure AD"
[ -n "$PROD_ENTRA_ID" ] && run_test "Prod | Azure AD (d154b9ed, Azure scope) | Legacy" ProdLegEntraScope || skip_test "Prod | Azure AD scope"
run_test "Prod | Same SP d154b9ed (dose secret) | Legacy" ProdLegEntraDB

echo ""
echo "--- PROD SPOG ---"
[ -n "$PROD_PAT" ] && run_test "Prod | PAT | SPOG" ProdSpogPAT || skip_test "Prod | PAT | SPOG"
[ -n "$PROD_ENTRA_ID" ] && run_test "Prod | Azure AD (d154b9ed, no scope) | SPOG" ProdSpogEntra || skip_test "Prod | Azure AD | SPOG"
[ -n "$PROD_ENTRA_ID" ] && run_test "Prod | Azure AD (d154b9ed, Azure scope) | SPOG" ProdSpogEntraScope || skip_test "Prod | Azure AD scope | SPOG"

echo ""
echo "================================================================"
echo "  SUMMARY: PASS=$PASS  FAIL=$FAIL  SKIP=$SKIP"
echo "================================================================"
[ $FAIL -gt 0 ] && exit 1 || exit 0
