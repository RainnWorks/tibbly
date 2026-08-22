/**
 * Magic-link email sender.
 *
 * Two implementations:
 *  - `ResendEmailSender` — production path, via the `resend` SDK.
 *  - `ConsoleEmailSender` — dev / test fallback that just logs the
 *    rendered link to stdout so a developer can click it directly out
 *    of the terminal.
 *
 * The interface is intentionally narrow (`send({ to, link, ttlMinutes })`)
 * so route handlers don't know which one is wired. Tests use a custom
 * implementation that records calls.
 */
import { Resend } from "resend";

import { log } from "../lib/log";

export interface MagicLinkEmailParams {
  to: string;
  /** The verify URL the user clicks. */
  link: string;
  /** TTL of the link in human-readable minutes, for the email copy. */
  ttlMinutes: number;
  /**
   * Short visual code the user can match against the page that asked
   * for the link — phishing defense. Optional.
   */
  visualCode?: string;
}

export interface EmailSender {
  send(params: MagicLinkEmailParams): Promise<void>;
}

/* ------------------------------------------------------------------ */
/* Resend implementation                                               */
/* ------------------------------------------------------------------ */

export interface ResendEmailSenderOptions {
  apiKey: string;
  /** Validated `From: …` header. e.g. `"Tibbly <noreply@tibbly.io>"`. */
  from: string;
}

export class ResendEmailSender implements EmailSender {
  private readonly client: Resend;
  private readonly from: string;

  constructor(options: ResendEmailSenderOptions) {
    this.client = new Resend(options.apiKey);
    this.from = options.from;
  }

  async send(params: MagicLinkEmailParams): Promise<void> {
    const subject = "Your Tibbly sign-in link";
    const { html, text } = renderTemplate(params);
    const res = await this.client.emails.send({
      from: this.from,
      to: params.to,
      subject,
      html,
      text,
    });
    if (res.error) {
      throw new Error(`resend send failed: ${res.error.message}`);
    }
  }
}

/* ------------------------------------------------------------------ */
/* Dev / test fallback                                                 */
/* ------------------------------------------------------------------ */

export class ConsoleEmailSender implements EmailSender {
  async send(params: MagicLinkEmailParams): Promise<void> {
    log.warn(
      {
        to: params.to,
        link: params.link,
        ttlMinutes: params.ttlMinutes,
        visualCode: params.visualCode,
      },
      "magic-link: console fallback — click this link to sign in",
    );
  }
}

/* ------------------------------------------------------------------ */
/* Template                                                            */
/* ------------------------------------------------------------------ */

/**
 * Plain HTML — no template engine. The Resend onboarding guidance is
 * specifically: keep first sends image-free, link domain matching
 * sender domain, single CTA. We follow that to the letter.
 */
export function renderTemplate(params: MagicLinkEmailParams): { html: string; text: string } {
  const safeLink = escapeHtml(params.link);
  const codeLine = params.visualCode
    ? `<p style="margin:0 0 16px;color:#888;font-size:13px">If you're signing in to bind RuneLite, the code on the page should read <strong style="color:#444;letter-spacing:2px">${escapeHtml(params.visualCode)}</strong> — don't click if it doesn't match.</p>`
    : "";
  const html = `<!doctype html>
<html><body style="margin:0;padding:0;background:#0d0f12;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,sans-serif">
<div style="max-width:520px;margin:0 auto;padding:32px 28px;color:#e6e1cf">
  <p style="margin:0 0 24px;font-size:14px;letter-spacing:1px;color:#e8c47a;text-transform:uppercase">Tibbly</p>
  <h1 style="margin:0 0 16px;font-size:22px;font-weight:600;color:#fff">Sign in to Tibbly</h1>
  <p style="margin:0 0 24px;font-size:15px;line-height:1.5;color:#cfd6df">Click the button below to finish signing in. The link expires in ${params.ttlMinutes} minutes and only works once.</p>
  <p style="margin:0 0 24px"><a href="${safeLink}" style="display:inline-block;background:#e8c47a;color:#0d0f12;padding:12px 22px;font-weight:600;text-decoration:none;border-radius:4px">Sign in</a></p>
  ${codeLine}
  <p style="margin:0 0 8px;font-size:12px;color:#666">If the button doesn't work, paste this URL into your browser:</p>
  <p style="margin:0;font-size:12px;color:#888;word-break:break-all">${safeLink}</p>
  <hr style="border:0;border-top:1px solid #222;margin:32px 0">
  <p style="margin:0;font-size:11px;color:#555">Tibbly — RuneLite plugin and chat. If you didn't request this, ignore this email.</p>
</div>
</body></html>`;

  const text = [
    "Sign in to Tibbly",
    "",
    `Click this link to finish signing in (expires in ${params.ttlMinutes} minutes, single use):`,
    params.link,
    params.visualCode
      ? `\nThe code on the page should read ${params.visualCode}. Don't click if it doesn't match.`
      : "",
    "",
    "If you didn't request this, ignore this email.",
  ].join("\n");

  return { html, text };
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
