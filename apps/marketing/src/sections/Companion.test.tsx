import { render, screen, within, act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Companion } from "./Companion";
import { SpeechBubble } from "./SpeechBubble";
import { COMPANION_MAGICAL_LINES } from "./Hero";

/*
 * Companion section tests. Covers the brief items:
 *   - The section renders
 *   - All five magical-moment lines appear as vignettes
 *   - The "what makes it alive" capsule renders with four bullets
 *   - The opinionated take renders verbatim
 *   - The speech bubble (sibling primitive) animates and respects
 *     prefers-reduced-motion
 */

describe("<Companion />", () => {
  it("renders the section with eyebrow + H2", () => {
    render(<Companion />);
    const section = screen.getByTestId("companion");
    expect(section).toBeInTheDocument();
    expect(within(section).getByText(/the magical moment/i)).toBeInTheDocument();
    expect(
      within(section).getByRole("heading", { level: 2 }),
    ).toHaveTextContent(/your OSRS friend who actually knows/i);
  });

  it("renders all five magical-moment vignettes verbatim", () => {
    render(<Companion />);
    const vignettes = screen.getAllByTestId("companion-vignette");
    expect(vignettes).toHaveLength(5);

    const lineEls = screen.getAllByTestId("companion-vignette-line");
    expect(lineEls).toHaveLength(5);

    // Every magical-moment line from EMBODIED_COMPANION lands on the
    // page. If one is missing, the vignette regressed.
    for (const line of COMPANION_MAGICAL_LINES) {
      const present = lineEls.some((el) => el.textContent?.includes(line));
      expect(present).toBe(true);
    }
  });

  it("alternates vignette sides so the layout reads as varied", () => {
    render(<Companion />);
    const sides = screen
      .getAllByTestId("companion-vignette")
      .map((el) => el.getAttribute("data-side"));
    // At least one of each side present (asymmetric, not five-in-a-row).
    expect(sides).toContain("left");
    expect(sides).toContain("right");
  });

  it("renders the 'what makes it alive' capsule with four bullets", () => {
    render(<Companion />);
    const capsule = screen.getByTestId("companion-alive-capsule");
    expect(capsule).toBeInTheDocument();
    const bullets = within(capsule).getAllByRole("listitem");
    expect(bullets).toHaveLength(4);
    expect(within(capsule).getByText(/grounded in real state/i)).toBeInTheDocument();
    expect(within(capsule).getByText(/remembers across sessions/i)).toBeInTheDocument();
    expect(within(capsule).getByText(/has dead air/i)).toBeInTheDocument();
    expect(within(capsule).getByText(/voice changes over time/i)).toBeInTheDocument();
  });

  it("renders the opinionated take verbatim", () => {
    render(<Companion />);
    const opinion = screen.getByTestId("companion-opinion");
    expect(opinion).toHaveTextContent(
      /tibbly is the only osrs plugin you'd say goodbye to\./i,
    );
  });
});

describe("<SpeechBubble />", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function mockMatchMedia(reduced: boolean): void {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      configurable: true,
      value: (query: string) => ({
        matches:
          reduced && query.includes("prefers-reduced-motion"),
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
        onchange: null,
      }),
    });
  }

  it("types in the first line and shows the cursor while typing", () => {
    mockMatchMedia(false);
    render(<SpeechBubble lines={["hello world"]} typeMs={10} dwellMs={9_999} />);
    // Initially empty (mounting state). After a few ticks the line types in.
    act(() => {
      vi.advanceTimersByTime(50);
    });
    const line = screen.getByTestId("speech-bubble-line");
    expect(line.textContent ?? "").not.toBe("");
    // Fully type the rest of the line.
    act(() => {
      vi.advanceTimersByTime(200);
    });
    expect(line.textContent).toContain("hello world");
  });

  it("rotates to the next line after the dwell window", () => {
    mockMatchMedia(false);
    render(
      <SpeechBubble
        lines={["alpha line", "beta line"]}
        typeMs={5}
        dwellMs={500}
      />,
    );
    // Type out alpha fully + dwell + start typing beta.
    act(() => {
      vi.advanceTimersByTime(50 + 500 + 50);
    });
    const bubble = screen.getByTestId("speech-bubble");
    expect(bubble.getAttribute("data-line-index")).toBe("1");
  });

  it("renders the active line statically when prefers-reduced-motion is on", () => {
    mockMatchMedia(true);
    render(<SpeechBubble lines={["calm static line"]} typeMs={10} />);
    const bubble = screen.getByTestId("speech-bubble");
    expect(bubble.getAttribute("data-reduced-motion")).toBe("true");
    const line = screen.getByTestId("speech-bubble-line");
    expect(line).toHaveTextContent("calm static line");
  });
});
