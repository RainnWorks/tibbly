import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Tailwind-aware className composer. Standard shadcn-style helper —
 * lets variant systems and ad-hoc class strings merge without
 * duplicate-utility conflicts.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
