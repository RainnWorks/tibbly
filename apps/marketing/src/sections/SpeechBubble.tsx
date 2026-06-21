import { useEffect, useRef, useState } from "react";

/*
 * SpeechBubble: a small parchment-style bubble whose contents either type
 * in once on mount, or rotate through a list of lines on a slow timer.
 *
 * Animation discipline:
 * - Honors prefers-reduced-motion at the component level. When reduced
 *   motion is on, the bubble renders each line in full, no typing, no
 *   cycling. The viewer sees the first line as the static snapshot.
 * - Default per-line dwell is 12s, slow enough to read once and breathe
 *   before the next line types in. Tom's "rotate every 10 to 15s" floor.
 * - Typing speed is per-character, capped to avoid jitter at small sizes.
 */

type SpeechBubbleProps = {
  readonly lines: readonly string[];
  /** Milliseconds each completed line stays visible before re-typing. */
  readonly dwellMs?: number;
  /** Milliseconds per character while typing. */
  readonly typeMs?: number;
  /** Optional tail direction. "left" points down-and-left, "down" sits flat. */
  readonly tail?: "left" | "down" | "none";
  readonly className?: string;
  /** Override the speaker eyebrow shown above the bubble copy. */
  readonly speaker?: string;
};

function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) return;
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = (): void => setReduced(mq.matches);
    update();
    mq.addEventListener?.("change", update);
    return () => mq.removeEventListener?.("change", update);
  }, []);
  return reduced;
}

export function SpeechBubble({
  lines,
  dwellMs = 12_000,
  typeMs = 28,
  tail = "left",
  className,
  speaker = "tibbly",
}: SpeechBubbleProps) {
  const reduced = usePrefersReducedMotion();
  const [lineIndex, setLineIndex] = useState(0);
  const [typed, setTyped] = useState<string>(lines[0] ?? "");
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const dwellTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // When motion is reduced, render the active line statically and never
  // cycle. The viewer still sees the brand moment without any flicker.
  useEffect(() => {
    if (reduced) {
      setTyped(lines[lineIndex] ?? "");
      return;
    }
    const target = lines[lineIndex] ?? "";
    setTyped("");
    let i = 0;
    const typeStep = (): void => {
      i += 1;
      setTyped(target.slice(0, i));
      if (i < target.length) {
        typingTimerRef.current = setTimeout(typeStep, typeMs);
      } else {
        dwellTimerRef.current = setTimeout(() => {
          setLineIndex((idx) => (idx + 1) % lines.length);
        }, dwellMs);
      }
    };
    typingTimerRef.current = setTimeout(typeStep, typeMs);
    return () => {
      if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
      if (dwellTimerRef.current) clearTimeout(dwellTimerRef.current);
    };
  }, [lineIndex, lines, dwellMs, typeMs, reduced]);

  return (
    <div
      data-testid="speech-bubble"
      data-line-index={lineIndex}
      data-reduced-motion={reduced ? "true" : "false"}
      className={[
        "relative inline-block max-w-sm border border-osrs-border bg-osrs-parchment/95 px-4 py-3 text-osrs-text shadow-[0_8px_28px_rgba(0,0,0,0.45)]",
        "before:absolute before:inset-px before:border before:border-osrs-gold-dim/30 before:content-['']",
        className ?? "",
      ]
        .filter(Boolean)
        .join(" ")}
      role="status"
      aria-live="polite"
    >
      <p
        data-testid="speech-bubble-speaker"
        className="mb-1 font-mono text-[10px] uppercase tracking-[0.32em] text-osrs-gold-dim"
      >
        {speaker} · your OSRS companion
      </p>
      <p
        data-testid="speech-bubble-line"
        className="relative font-body text-sm leading-snug text-osrs-text/95"
      >
        {typed}
        {!reduced && typed.length < (lines[lineIndex]?.length ?? 0) && (
          <span aria-hidden="true" className="demo-cursor ml-0.5" />
        )}
      </p>
      {/* Tail shapes: a small notch leaning out from the bubble. */}
      {tail !== "none" && (
        <span
          aria-hidden="true"
          className={[
            "absolute h-3 w-3 rotate-45 border border-osrs-border bg-osrs-parchment/95",
            tail === "left" ? "-bottom-1.5 left-6" : "-bottom-1.5 left-1/2 -translate-x-1/2",
          ].join(" ")}
        />
      )}
    </div>
  );
}
