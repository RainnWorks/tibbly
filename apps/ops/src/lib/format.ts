/**
 * Number formatters for the ops surfaces. All formatters are pure
 * functions; they assume no locale negotiation (the operator team is
 * en-US for now).
 */

export function formatTokens(n: number): string {
  if (!Number.isFinite(n)) return "0";
  return n.toLocaleString("en-US");
}

/** Micro-USD (1e-6 USD) -> "$0.0123". Up to 4 decimals. */
export function formatMicroUsd(microUsd: number): string {
  const usd = microUsd / 1_000_000;
  if (!Number.isFinite(usd)) return "$0";
  if (Math.abs(usd) >= 100) {
    return `$${usd.toLocaleString("en-US", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  }
  return `$${usd.toLocaleString("en-US", {
    minimumFractionDigits: 4,
    maximumFractionDigits: 4,
  })}`;
}

/** USD cents -> "$19.00". */
export function formatUsdCents(cents: number): string {
  const usd = cents / 100;
  if (!Number.isFinite(usd)) return "$0.00";
  return `$${usd.toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export function compactInteger(n: number): string {
  if (!Number.isFinite(n)) return "0";
  if (Math.abs(n) < 1000) return String(n);
  if (Math.abs(n) < 1_000_000) return `${(n / 1000).toFixed(1)}K`;
  if (Math.abs(n) < 1_000_000_000) return `${(n / 1_000_000).toFixed(2)}M`;
  return `${(n / 1_000_000_000).toFixed(2)}B`;
}
