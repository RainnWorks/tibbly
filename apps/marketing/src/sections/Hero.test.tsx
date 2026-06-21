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
    expect(COMPANION_MAGICAL_LINES).toHaveLength(5);
    expect(COMPANION_MAGICAL_LINES[0]).toBe(
      "Protect magic. She nukes 50s the second the orb spawns.",
    );
    expect(COMPANION_MAGICAL_LINES[4]).toBe(
      "You hit 99 Slayer ten minutes ago. I noticed.",
    );
  });
});
