#!/usr/bin/env python3
"""
update_seed_prices.py — Sold Short seed price updater
======================================================
Fetches the latest closing price for every ticker listed in
ManualMarketSimulator.java and rewrites the seedData() block in-place.

Run this before committing / deploying to keep the fallback prices accurate.

Usage:
    python update_seed_prices.py                  # dry-run: prints prices only
    python update_seed_prices.py --write          # rewrites ManualMarketSimulator.java
    python update_seed_prices.py --write --quiet  # silent rewrite

Requirements:
    pip install yfinance
"""

import argparse
import re
import sys
import time
from pathlib import Path

try:
    import yfinance as yf
except ImportError:
    print("ERROR: yfinance not installed. Run:  pip install yfinance")
    sys.exit(1)

# ── Configuration ─────────────────────────────────────────────────────────────

SIMULATOR_PATH = Path(__file__).parent / (
    "server/src/main/java/com/soldshort/market/ManualMarketSimulator.java"
)

# Must match the order in ManualMarketSimulator.java so the comment structure
# (sector headers) is preserved correctly.
TICKERS = [
    # Technology
    "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "AMD", "INTC",
    "ORCL", "IBM", "CSCO", "QCOM", "AVGO", "TXN", "TSLA",
    # Finance
    "JPM", "BAC", "GS", "WFC", "C", "V", "MA", "PYPL", "AXP", "MS",
    # Consumer / Retail
    "WMT", "TGT", "KO", "PEP", "MCD", "SBUX", "NKE", "COST", "HD",
    # Industrial / Auto
    "BA", "CAT", "GE", "F", "GM",
    # Healthcare
    "JNJ", "PFE", "MRK", "UNH", "ABBV", "LLY", "TMO",
    # Energy
    "XOM", "CVX", "COP", "OXY", "SLB",
    # Media / Telecom
    "NFLX", "DIS", "CMCSA", "T", "VZ",
    # Emerging / High-Growth Tech
    "UBER", "SPOT", "PLTR", "COIN",
    # Asset Management / Brokerage / Other
    "BLK", "SCHW", "DE", "LOW",
]

# Batch size to avoid Yahoo Finance rate-limiting
BATCH_SIZE = 10
BATCH_DELAY = 1.5  # seconds between batches

# ── Price Fetching ────────────────────────────────────────────────────────────

def fetch_prices(quiet: bool = False) -> dict[str, float]:
    """Download the latest close for every ticker, in batches."""
    prices: dict[str, float] = {}
    failed: list[str] = []

    batches = [TICKERS[i:i + BATCH_SIZE] for i in range(0, len(TICKERS), BATCH_SIZE)]
    total_batches = len(batches)

    for batch_num, batch in enumerate(batches, 1):
        if not quiet:
            print(f"  Fetching batch {batch_num}/{total_batches}: {' '.join(batch)}")
        try:
            data = yf.download(
                batch,
                period="5d",          # last 5 trading days; handles weekends/holidays
                auto_adjust=True,
                progress=False,
                threads=True,
            )
            closes = data["Close"].dropna(how="all")
            if closes.empty:
                if not quiet:
                    print(f"    WARNING: no data returned for batch {batch_num}")
                failed.extend(batch)
                continue

            latest = closes.iloc[-1]
            for ticker in batch:
                val = latest.get(ticker)
                if val is not None and not (isinstance(val, float) and val != val):  # NaN check
                    prices[ticker] = round(float(val), 2)
                else:
                    if not quiet:
                        print(f"    WARNING: no price for {ticker} — keeping existing seed")
                    failed.append(ticker)

        except Exception as exc:
            if not quiet:
                print(f"    ERROR on batch {batch_num}: {exc}")
            failed.extend(batch)

        if batch_num < total_batches:
            time.sleep(BATCH_DELAY)

    if not quiet:
        print(f"\n  Fetched {len(prices)}/{len(TICKERS)} tickers"
              + (f"  ({len(failed)} failed: {', '.join(failed)})" if failed else " — all OK"))

    return prices

# ── Java Source Rewriter ──────────────────────────────────────────────────────

# Matches the entire seedData Object[][] literal, from the opening brace of the
# array literal to the closing semicolon, so we can replace it wholesale.
SEED_BLOCK_RE = re.compile(
    r"(Object\[\]\[\] data = \{)(.*?)(^\s*\};)",
    re.DOTALL | re.MULTILINE,
)

# Company names from the existing source (we preserve them rather than
# re-deriving, so no rename happens accidentally).
COMPANY_NAMES = {
    "AAPL":  "Apple Inc.",
    "MSFT":  "Microsoft Corp.",
    "GOOGL": "Alphabet Inc.",
    "AMZN":  "Amazon.com Inc.",
    "META":  "Meta Platforms Inc.",
    "NVDA":  "NVIDIA Corp.",
    "AMD":   "Advanced Micro Devices",
    "INTC":  "Intel Corp.",
    "ORCL":  "Oracle Corp.",
    "IBM":   "IBM Corp.",
    "CSCO":  "Cisco Systems Inc.",
    "QCOM":  "Qualcomm Inc.",
    "AVGO":  "Broadcom Inc.",
    "TXN":   "Texas Instruments Inc.",
    "TSLA":  "Tesla Inc.",
    "JPM":   "JPMorgan Chase & Co.",
    "BAC":   "Bank of America Corp.",
    "GS":    "Goldman Sachs Group Inc.",
    "WFC":   "Wells Fargo & Co.",
    "C":     "Citigroup Inc.",
    "V":     "Visa Inc.",
    "MA":    "Mastercard Inc.",
    "PYPL":  "PayPal Holdings Inc.",
    "AXP":   "American Express Co.",
    "MS":    "Morgan Stanley",
    "WMT":   "Walmart Inc.",
    "TGT":   "Target Corp.",
    "KO":    "The Coca-Cola Co.",
    "PEP":   "PepsiCo Inc.",
    "MCD":   "McDonald's Corp.",
    "SBUX":  "Starbucks Corp.",
    "NKE":   "Nike Inc.",
    "COST":  "Costco Wholesale Corp.",
    "HD":    "The Home Depot Inc.",
    "BA":    "Boeing Co.",
    "CAT":   "Caterpillar Inc.",
    "GE":    "GE Aerospace",
    "F":     "Ford Motor Co.",
    "GM":    "General Motors Co.",
    "JNJ":   "Johnson & Johnson",
    "PFE":   "Pfizer Inc.",
    "MRK":   "Merck & Co. Inc.",
    "UNH":   "UnitedHealth Group Inc.",
    "ABBV":  "AbbVie Inc.",
    "LLY":   "Eli Lilly and Co.",
    "TMO":   "Thermo Fisher Scientific",
    "XOM":   "Exxon Mobil Corp.",
    "CVX":   "Chevron Corp.",
    "COP":   "ConocoPhillips",
    "OXY":   "Occidental Petroleum Corp.",
    "SLB":   "SLB (Schlumberger)",
    "NFLX":  "Netflix Inc.",
    "DIS":   "The Walt Disney Co.",
    "CMCSA": "Comcast Corp.",
    "T":     "AT&T Inc.",
    "VZ":    "Verizon Communications Inc.",
    "UBER":  "Uber Technologies Inc.",
    "SPOT":  "Spotify Technology S.A.",
    "PLTR":  "Palantir Technologies Inc.",
    "COIN":  "Coinbase Global Inc.",
    "BLK":   "BlackRock Inc.",
    "SCHW":  "Charles Schwab Corp.",
    "DE":    "Deere & Company",
    "LOW":   "Lowe's Companies Inc.",
}

SECTOR_COMMENTS = {
    "AAPL":  "// ── Technology ─────────────────────────────────────────────────────────────",
    "JPM":   "// ── Finance ────────────────────────────────────────────────────────────────",
    "WMT":   "// ── Consumer / Retail ──────────────────────────────────────────────────────",
    "BA":    "// ── Industrial / Auto ──────────────────────────────────────────────────────",
    "JNJ":   "// ── Healthcare ─────────────────────────────────────────────────────────────",
    "XOM":   "// ── Energy ─────────────────────────────────────────────────────────────────",
    "NFLX":  "// ── Media / Telecom ─────────────────────────────────────────────────────────",
    "UBER":  "// ── Emerging / High-Growth Tech ─────────────────────────────────────────────",
    "BLK":   "// ── Asset Management / Brokerage ───────────────────────────────────────────",
}


def build_seed_block(prices: dict[str, float], existing_prices: dict[str, float]) -> str:
    """Build the replacement Object[][] literal body (the part inside { ... })."""
    import datetime
    today = datetime.date.today().isoformat()

    lines = [
        "",
        f"            // {{ ticker, companyName, seedPrice }}",
        f"            // Seed prices — updated {today} by update_seed_prices.py",
        "",
    ]

    for ticker in TICKERS:
        if ticker in SECTOR_COMMENTS:
            lines.append(f"            {SECTOR_COMMENTS[ticker]}")

        price = prices.get(ticker, existing_prices.get(ticker, 0.0))
        name  = COMPANY_NAMES.get(ticker, ticker)

        # Right-align the price column for readability
        ticker_pad = f'"{ticker}"'.ljust(8)
        name_pad   = f'"{name}"'.ljust(38)
        price_str  = f"{price:.2f}"

        lines.append(f"            {{ {ticker_pad}, {name_pad}, {price_str} }},")

    lines.append("        ")
    return "\n".join(lines)


def parse_existing_prices(source: str) -> dict[str, float]:
    """Extract current seed prices from the Java source as a fallback."""
    existing: dict[str, float] = {}
    # Match lines like: { "AAPL"  , "Apple Inc.",  260.48 },
    for m in re.finditer(r'\{\s*"([A-Z]+)"\s*,\s*"[^"]+"\s*,\s*([\d.]+)\s*\}', source):
        existing[m.group(1)] = float(m.group(2))
    return existing


def rewrite_java(new_prices: dict[str, float], quiet: bool = False) -> bool:
    """Rewrite ManualMarketSimulator.java with updated seed prices."""
    source = SIMULATOR_PATH.read_text(encoding="utf-8")
    existing = parse_existing_prices(source)

    new_body = build_seed_block(new_prices, existing)

    def replacer(m: re.Match) -> str:
        return m.group(1) + new_body + "\n        " + m.group(3)

    new_source, count = SEED_BLOCK_RE.subn(replacer, source)
    if count == 0:
        print("ERROR: Could not find Object[][] data block in ManualMarketSimulator.java")
        print("       Has the file structure changed? Check SEED_BLOCK_RE pattern.")
        return False

    SIMULATOR_PATH.write_text(new_source, encoding="utf-8")
    if not quiet:
        print(f"\n  Wrote updated seed prices to:\n  {SIMULATOR_PATH}")
    return True

# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Update Sold Short seed prices from Yahoo Finance."
    )
    parser.add_argument(
        "--write", action="store_true",
        help="Rewrite ManualMarketSimulator.java with fetched prices (default: dry-run)"
    )
    parser.add_argument(
        "--quiet", action="store_true",
        help="Suppress progress output"
    )
    args = parser.parse_args()

    if not args.quiet:
        print("Sold Short — Seed Price Updater")
        print("=" * 48)
        print(f"Target file: {SIMULATOR_PATH.relative_to(Path.cwd()) if SIMULATOR_PATH.is_absolute() else SIMULATOR_PATH}")
        print()

    if not SIMULATOR_PATH.exists():
        print(f"ERROR: File not found:\n  {SIMULATOR_PATH}")
        print("Run this script from the project root (CSC3380 Project - Sold Short/).")
        sys.exit(1)

    if not args.quiet:
        print("Fetching prices from Yahoo Finance...")

    prices = fetch_prices(quiet=args.quiet)

    if not args.quiet:
        print("\nFetched prices:")
        print(f"  {'Ticker':<8}  {'Fetched':>10}  {'Old Seed':>10}  {'Δ':>8}")
        print(f"  {'-'*8}  {'-'*10}  {'-'*10}  {'-'*8}")
        source = SIMULATOR_PATH.read_text(encoding="utf-8")
        existing = parse_existing_prices(source)
        for ticker in TICKERS:
            fetched  = prices.get(ticker)
            old      = existing.get(ticker, 0.0)
            if fetched is not None:
                delta = fetched - old
                flag  = "  ← LARGE CHANGE" if abs(delta) > old * 0.15 else ""
                print(f"  {ticker:<8}  {fetched:>10.2f}  {old:>10.2f}  {delta:>+8.2f}{flag}")
            else:
                print(f"  {ticker:<8}  {'NO DATA':>10}  {old:>10.2f}  {'—':>8}")
        print()

    if args.write:
        ok = rewrite_java(prices, quiet=args.quiet)
        if ok and not args.quiet:
            print("Done. Commit the updated ManualMarketSimulator.java and redeploy.")
    else:
        if not args.quiet:
            print("Dry-run complete (prices not written). Pass --write to update the file.")

if __name__ == "__main__":
    main()
