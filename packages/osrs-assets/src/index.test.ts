import { describe, expect, test } from "bun:test";
import {
  ASSETS, FONTS, isForbiddenAssetUrl, SKILLS, SKILL_ICONS, SKILL_ICONS_SMALL,
} from "./index";

describe("@osrs-llm-helper/osrs-assets", () => {
  test("exports 26 skill icons", () => {
    expect(SKILLS.length).toBe(26);
    expect(SKILL_ICONS.length).toBe(26);
    expect(SKILL_ICONS_SMALL.length).toBe(26);
  });
  test("exports 3 fonts", () => {
    expect(FONTS.length).toBe(3);
  });
  test("every asset has provenance metadata", () => {
    for (const asset of ASSETS) {
      expect(asset.id).toBeTruthy();
      expect(asset.path).toBeTruthy();
      expect(asset.source.startsWith("https://")).toBe(true);
      expect(["BSD-2-Clause", "CC0-1.0", "MIT"]).toContain(asset.license);
    }
  });
  test("BSD-2 assets require attribution", () => {
    for (const asset of ASSETS) {
      if (asset.license === "BSD-2-Clause") {
        expect(asset.attribution).toContain("RuneLite");
      }
    }
  });
  test("rejects OSRS Wiki URLs", () => {
    expect(isForbiddenAssetUrl("https://oldschool.runescape.wiki/images/Attack.png")).toBe(true);
    expect(isForbiddenAssetUrl("https://runescape.wiki/w/File:Attack.png")).toBe(true);
    expect(isForbiddenAssetUrl("https://secure.runescape.com/m=assets/img.png")).toBe(true);
  });
  test("allows RuneLite raw URLs", () => {
    expect(isForbiddenAssetUrl(
      "https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/resources/skill_icons/attack.png"
    )).toBe(false);
  });
  test("handles malformed URLs", () => {
    expect(isForbiddenAssetUrl("not a url")).toBe(false);
    expect(isForbiddenAssetUrl("")).toBe(false);
  });
});
