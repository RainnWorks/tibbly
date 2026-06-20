# OSRS visual asset licensing — risk matrix

> Owner: R3 · Issue: RAI-7 · Updated: 2026-06-21

> WARNING. Every OSRS-themed sprite/icon ultimately depicts Jagex IP. Code
> licenses (MIT, BSD, CC-BY-SA) only cover the code or packaging — they do
> NOT grant Jagex IP rights. Jagex's Fan Content Policy is the binding
> constraint, not the upstream repo's LICENSE file.

## TL;DR — go/no-go per source

| Source | License | Jagex-IP cleared? | Verdict for paid SaaS |
|---|---|---|---|
| RuneLite repo (icons/fonts/UI) | BSD-2-Clause | NO — but tolerated by Jagex for ~10y | YES. Primary source. Attribute. |
| RuneStar/fonts (RS TTFs) | CC0 1.0 | NO — re-drawn by community | YES. Display font. Lowest risk. |
| OSRS Wiki sprites | CC-BY-NC-SA 3.0 | NO + explicitly NonCommercial | NO. NC clause fatal for paid SaaS. |
| Wise Old Man img | MIT (code) | NO | Reference only. Don't re-host. |
| OldSchoolBot data | MIT | NO | Reference only. |
| osrsbox-db | GPL-3.0 | NO | Avoid. GPL virality risk. |
| Jagex press kit | All rights reserved | press-kit terms | Limited promo only. |
| Our commissioned art | We own | YES if transformative | Highest safety. |

## RISK A — OSRS Wiki licence trap

OSRS Wiki text is CC-BY-NC-SA 3.0. NC = NonCommercial. Our product is paid SaaS.
Embedding wiki sprites in marketing/dashboard violates the licence.

From meta.weirdgloop.org/wiki/Licensing:
> "Non-text media on our wikis should not be assumed to be available under the
> same license as the text."

Most game sprites on the wiki are uploaded under Jagex fair-use rationale, not
CC-licensed at all. Fair use does not transfer to commercial use.

Mandatory: marketing site and dashboard MUST NOT hot-link any URL under
oldschool.runescape.wiki/images/ or .../w/File:. Enforced in code by
isForbiddenAssetUrl() in @osrs-llm-helper/osrs-assets.

## RISK B — Jagex Fan Content Policy (legal.jagex.com)

- Default non-commercial:
  > "you can only create content for your personal use and not to make money"
- Software ban:
  > "You can't make any form of video game content using Jagex Property,
  > which includes custom quests or add-ons for integration into one of our games"
- Third-party clients banned:
  > "You can't reverse engineer any of our games or create a means of
  > accessing them via a third-party client"
- Carve-outs: YouTube/Twitch ads, sponsorships, music covers, 100-unit
  handmade craft runs, fan-art commissions.
- Carve-outs DO NOT cover: subscription SaaS, software tools, plugins.

Softening line:
> "we are already in touch with the existing third-party clients being used
> by our communities and we periodically issue guidance and updates"

That's how RuneLite has survived ~10 years. Jagex tolerates, not licenses.
We inherit that posture, not a legal right.

## RISK C — code licence != IP licence

RuneLite is BSD-2. We can copy skill_icons/agility.png without violating BSD.
But the sprite still depicts Jagex IP. BSD disclaims warranty against IP
claims. If Jagex objects, "but it was BSD-licensed" is not a defence. The
defence is RuneLite parity.

## RISK D — MIT community repos same caveat

Wise Old Man (MIT), OldSchoolBot (MIT), osrsbox-db (GPL-3.0) — their licences
cover code, not Jagex's underlying rights to the depicted sprites.

## RECOMMENDED POSTURE

1. Default source: RuneLite (BSD-2). Mirror skill_icons/, skill_icons_small/,
   teleport icons, UI chrome into packages/osrs-assets/. Attribute in footer.
2. Display font: RuneStar (CC0). Drop TTFs into packages/osrs-assets/fonts/.
3. Zero hot-links to oldschool.runescape.wiki. Enforced by guard helper.
4. Hero art: commission original. Highest safety.
5. Public posture: "this project is not affiliated with or endorsed by Jagex Ltd."
6. Hard rule: any asset PR pulling from a domain outside the whitelist
   (raw.githubusercontent.com/runelite/runelite/, raw.githubusercontent.com/RuneStar/fonts/,
   own commissioned art) needs human review.

## ESCALATION SIGNALS

- C&D activity against RuneLite or HDOS.
- Jagex Plugin Hub revoking paid plugin listings.
- Fan Content Policy update removing third-party-client tolerance.

If any happen: pull marketing assets, re-evaluate.

## SOURCES

- oldschool.runescape.wiki/w/RuneScape:Copyrights
- meta.weirdgloop.org/wiki/Licensing
- github.com/runelite/runelite/blob/master/LICENSE
- github.com/RuneStar/fonts/blob/master/LICENSE
- legal.jagex.com/docs/policies/fan-content-policy
- github.com/wise-old-man/wise-old-man/blob/master/LICENSE
- github.com/oldschoolgg/oldschoolbot/blob/master/LICENSE
