/*
 * CompanionSprite: the Tibbly Probe drawn as inline SVG.
 *
 * Tibbly is a small floating robot ("Probe") whose in-plugin sprite
 * atlas is baked from a CC0 3D mesh by Quaternius (see
 * docs/product/COMPANION_3D_SOURCE.md and THIRD_PARTY_LICENSES.md).
 * This component renders an SVG profile of the same silhouette so
 * the marketing page and the in-game art read as the same character.
 *
 * Props are unchanged from the original hooded-humanoid placeholder
 * so any existing call site keeps working after the pivot.
 */

type CompanionSpriteProps = {
  readonly size?: number;
  /** Tint applied to the LED accent. Defaults to OSRS gold. */
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
  label = "Tibbly, the floating Probe",
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
      {/* Soft ground shadow under the hover line */}
      <ellipse cx="32" cy="58" rx="13" ry="2.2" fill="rgba(0,0,0,0.45)" />

      {/* Small stub antenna on top of the lens housing */}
      <line
        x1="32"
        y1="14"
        x2="32"
        y2="9"
        stroke="#5a3e1a"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
      <circle cx="32" cy="8" r="1.4" fill={accent} />

      {/* Chassis sphere (the Probe body) */}
      <circle
        cx="32"
        cy="30"
        r="16"
        fill="#3a2d1a"
        stroke="#5a3e1a"
        strokeWidth="1.5"
      />

      {/* Highlight band so the sphere reads 3D at small sizes */}
      <path
        d="M19 24 Q 24 18, 36 19"
        fill="none"
        stroke="#7a5824"
        strokeWidth="1.2"
        strokeLinecap="round"
        opacity="0.7"
      />

      {/* Lens housing rim */}
      <circle
        cx="32"
        cy="32"
        r="9"
        fill="#0b0703"
        stroke="#5a3e1a"
        strokeWidth="1.2"
      />

      {/* The bright front-facing lens (player-tintable accent) */}
      <circle cx="32" cy="32" r="6" fill={accent} opacity="0.95" />

      {/* Inner glint on the lens to suggest "alive, watching" */}
      <circle cx="29.5" cy="29.5" r="1.6" fill="#fff8d6" opacity="0.85" />

      {/* Belly screen panel (the display_on / speak surface) */}
      <rect
        x="26"
        y="40"
        width="12"
        height="4"
        rx="1"
        fill="#0b0703"
        stroke="#5a3e1a"
        strokeWidth="0.8"
      />
      <rect x="28" y="41.5" width="2" height="1" fill={accent} opacity="0.9" />
      <rect x="32" y="41.5" width="2" height="1" fill={accent} opacity="0.6" />
      <rect x="36" y="41.5" width="0.8" height="1" fill={accent} opacity="0.9" />

      {/* Side thruster nozzles flanking the chassis */}
      <ellipse cx="17" cy="33" rx="2.2" ry="3" fill="#0b0703" />
      <ellipse cx="47" cy="33" rx="2.2" ry="3" fill="#0b0703" />
      <ellipse cx="17" cy="34.5" rx="1.4" ry="1.4" fill={accent} opacity="0.55" />
      <ellipse cx="47" cy="34.5" rx="1.4" ry="1.4" fill={accent} opacity="0.55" />
    </svg>
  );
}
