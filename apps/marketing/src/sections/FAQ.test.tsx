import { render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { FAQ } from "./FAQ";

describe("<FAQ />", () => {
  it("renders multiple FAQ entries", () => {
    render(<FAQ />);
    const list = screen.getByTestId("faq-list");
    const items = within(list).getAllByRole("listitem");
    expect(items.length).toBeGreaterThanOrEqual(5);
  });

  it("includes the three companion-themed questions", () => {
    render(<FAQ />);
    const list = screen.getByTestId("faq-list");
    expect(within(list).getByText(/is tibbly the companion or the chat\?/i)).toBeInTheDocument();
    expect(within(list).getByText(/will tibbly say things i didn't tell it to\?/i)).toBeInTheDocument();
    expect(within(list).getByText(/does it remember me across sessions\?/i)).toBeInTheDocument();
  });

  it("keeps the bot-question first (IA §2.8 order)", () => {
    render(<FAQ />);
    const items = within(screen.getByTestId("faq-list")).getAllByRole("listitem");
    expect(items[0]).toHaveTextContent(/is this a bot/i);
  });
});
