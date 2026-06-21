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

/** USD cents -> "$19.00". Retained for spend formatting; revenue uses pence. */
export function formatUsdCents(cents: number): string {
  const usd = cents / 100;
  if (!Number.isFinite(usd)) return "$0.00";
  return `$${usd.toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

/** Pence GBP -> "£19.00". Revenue + tier price formatter per D-11. */
export function formatPence(pence: number): string {
  const gbp = pence / 100;
  if (!Number.isFinite(gbp)) return "£0.00";
  return `£${gbp.toLocaleString("en-GB", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

/**
 * Stripe invoice amounts: smallest-unit integer in an arbitrary currency
 * carried on the invoice itself. Renders with the right symbol.
 */
export function formatStripeMinor(amount: number, currency: string): string {
  const code = (currency ?? "gbp").toLowerCase();
  if (code === "gbp") return formatPence(amount);
  if (code === "usd") return formatUsdCents(amount);
  if (!Number.isFinite(amount)) return `${code.toUpperCase()} 0.00`;
  const major = amount / 100;
  return `${code.toUpperCase()} ${major.toLocaleString("en-GB", {
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
