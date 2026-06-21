/*
 * CompanionSprite: a small hooded humanoid drawn as inline SVG.
 *
 * This is the WEB-side placeholder for the companion. The real artwork
 * is being commissioned (four starter forms, per
 * docs/product/EMBODIED_COMPANION.md §6 "Asset pipeline"). The plugin
 * uses a sprite atlas at runtime; on the marketing site we render a
 * vector stand-in so the page is shippable today and the visual swap is
 * a one-file change when the commission lands.
 *
 * TODO: swap to commissioned sprite when art lands. The component API
 * (size + accent + label) is shaped so the replacement art can be a
 * direct drop-in with the same props.
 */

type CompanionSpriteProps = {
  readonly size?: number;
  /** Tint applied to the cloak accent stripe. Defaults to OSRS gold. */
  readonly accent?: string;
  /** Accessible label. Set to "" when the sprite is decorative. */
  readonly label?: string;
  readonly className?: string;
  /** Subtle idle bob animation. Honors prefers-reduced-motion globally. */
  readonly idle?: boolean;
};

export function CompanionSprite({
  size = 96,
  accent = "var(--color-osrs-gold)",
  label = "Tibbly, the hooded companion",
  className,
  idle = true,
}: CompanionSpriteProps) {
  const decorative = label === "";
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      role={decorative ? undefined : "img"}
      aria-label={decorative ? undefined : label}
      aria-hidden={decorative ? "true" : undefined}
      className={[
        "companion-sprite",
        idle ? "sprite-bob" : "",
        className ?? "",
      ]
        .filter(Boolean)
        .join(" ")}
      data-testid="companion-sprite"
    >
      {/* Soft ground shadow under the boots */}
      <ellipse cx="32" cy="58" rx="14" ry="2.4" fill="rgba(0,0,0,0.45)" />

      {/* Cloak silhouette */}
      <path
        d="M16 30 C 16 22, 22 14, 32 14 C 42 14, 48 22, 48 30 L 50 56 L 14 56 Z"
        fill="#2b1d0d"
        stroke="#5a3e1a"
        strokeWidth="1.5"
      />

      {/* Cloak hem highlight */}
      <path
        d="M14 56 L 50 56"
        fill="none"
        stroke="#7a5824"
        strokeWidth="1"
        opacity="0.75"
      />

      {/* Accent stripe down the cloak (player-tintable) */}
      <path
        d="M32 18 L 32 54"
        stroke={accent}
        strokeWidth="1.2"
        strokeLinecap="round"
        opacity="0.85"
      />

      {/* Hood opening shadow */}
      <ellipse cx="32" cy="26" rx="9" ry="8" fill="#0b0703" />

      {/* Two glowing eyes. Calm, level, not cute. */}
      <circle cx="28.5" cy="26" r="1.6" fill={accent} />
      <circle cx="35.5" cy="26" r="1.6" fill={accent} />

      {/* Tiny hood point */}
      <path
        d="M32 14 Q 38 8, 40 14"
        fill="none"
        stroke="#5a3e1a"
        strokeWidth="1.5"
        strokeLinecap="round"
      />

      {/* Single button at the throat. Small specificity beat. */}
      <circle cx="32" cy="36" r="1.2" fill={accent} opacity="0.9" />
    </svg>
  );
}
