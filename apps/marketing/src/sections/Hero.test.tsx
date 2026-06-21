import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { Hero, COMPANION_MAGICAL_LINES } from "./Hero";

beforeEach(() => {
  // jsdom does not implement matchMedia by default; SpeechBubble reads it
  // on mount to detect prefers-reduced-motion.
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
      onchange: null,
    }),
  });
});

describe("<Hero />", () => {
  it("renders the headline and primary CTAs", () => {
    render(<Hero />);
    expect(screen.getByTestId("hero")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1 }),
    ).toHaveTextContent(/stop alt-tabbing/i);
    expect(
      screen.getByRole("link", { name: /install the runelite plugin/i }),
    ).toHaveAttribute("href", "#pricing");
    expect(screen.getByRole("link", { name: /see the demo/i })).toHaveAttribute(
      "href",
      "#demo",
    );
  });

  it("uses the no-automation guarantee as the sub-hero copy", () => {
    render(<Hero />);
    expect(
      screen.getByText(/never moves your character/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/never clicks a tile/i),
    ).toBeInTheDocument();
  });

  it("mounts the companion sticker with the desktop + mobile slots", () => {
    render(<Hero />);
    expect(screen.getByTestId("hero-companion-sticker")).toBeInTheDocument();
    expect(screen.getByTestId("hero-companion-mobile")).toBeInTheDocument();
    expect(screen.getAllByTestId("companion-sprite").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByTestId("speech-bubble").length).toBeGreaterThanOrEqual(2);
  });

  it("exports the five magical-moment lines verbatim", () => {
    // Lines re-authored 2026-06-21 under the cold-OSRS-player ear test
    // (RAI-72 audit → RAI-73 fix). Each one names a real OSRS mechanic:
    //   0. Vorkath zombified-spawn fireball + male pronoun + memory beat
    //   1. Fight Caves "call waves" (real slang, not "wave timer")
    //   2. Sins of the Father → Vanstrom phase 4 prayer order
    //   3. Vorkath revisit, "spec mage" = special-attack-magic-pot
    //   4. 99 Slayer dry rib in brand voice
    expect(COMPANION_MAGICAL_LINES).toHaveLength(5);
    expect(COMPANION_MAGICAL_LINES[0]).toBe(
      "Spec the spawn. He caught you with that fireball last week.",
    );
    expect(COMPANION_MAGICAL_LINES[1]).toBe(
      "Forty-seven minutes in. Want me to call waves?",
    );
    expect(COMPANION_MAGICAL_LINES[2]).toBe(
      "Vanstrom phase 4. Want the prayer order this time?",
    );
    expect(COMPANION_MAGICAL_LINES[3]).toBe(
      "You're cleaner than last week. Skip the spec mage.",
    );
    expect(COMPANION_MAGICAL_LINES[4]).toBe(
      "99 Slayer. Took your time.",
    );
  });

  it("never uses 'she' for Vorkath or 'wave timer' for Fight Caves (audit RAI-72)", () => {
    // Regression guards from the cold-OSRS-player review. If either
    // string returns, the lines drifted back to the original
    // engineer-paraphrased copy.
    for (const line of COMPANION_MAGICAL_LINES) {
      expect(line.toLowerCase()).not.toContain("she ");
      expect(line.toLowerCase()).not.toContain("wave timer");
      expect(line.toLowerCase()).not.toContain("nukes 50");
    }
  });
});
