# Third party licenses

This file catalogs every third-party asset that ships inside this
repo (plugin resources, marketing site media, vendored fonts and
sprites). Library dependencies are tracked through their respective
package managers and surfaced via the dependency-license tooling in
`packages/tooling/`; this file is the source of truth for shipped
assets that the package-manager pipeline does not cover.

The runelite hub plugin manifest (`apps/plugin/src/main/resources/runelite-plugin.properties`)
has a `warning=` line that points users at this file per
[docs/runelite-hub/SUBMISSION_CHECKLIST.md section 2.4](docs/runelite-hub/SUBMISSION_CHECKLIST.md).

## Companion Probe base mesh

- Source: Quaternius "Sci-Fi Essentials Kit" / "Robot Enemy Flying"
- License: CC0 1.0 Universal (Public Domain Dedication)
- Author: Quaternius (Tomas Laulhe, <laulhet@gmail.com>)
- URL: <https://quaternius.com/packs/scifiessentialskit.html>
- Mirror: <https://poly.pizza/m/lF3jeRJwiH>
- Vendored at: `apps/plugin/src/main/resources/companion/source/probe.glb`
- License text: `apps/plugin/src/main/resources/companion/source/LICENSE.txt`
- Pick rationale: [docs/product/COMPANION_3D_SOURCE.md](docs/product/COMPANION_3D_SOURCE.md)

> The Probe companion's base 3D mesh is by Quaternius (Tomas Laulhe),
> released under CC0 1.0 Universal. Find more of their work at
> <https://quaternius.com/>.

## RuneStar OSRS fonts

- License: CC0 1.0 Universal
- Author: RuneStar
- URL: <https://github.com/RuneStar/fonts>
- Vendored at: `packages/osrs-assets/`
- See: `packages/osrs-assets/LICENSES.md`

## RuneLite icons

- License: BSD 2-Clause
- Author: RuneLite contributors
- URL: <https://github.com/runelite/runelite>
- Vendored at: `packages/osrs-assets/`
- See: `packages/osrs-assets/LICENSES.md`

## Notes

This file is a living catalog. When a new third-party asset is
vendored, append an entry above before the PR lands. Format:

```
## <Asset name>

- Source: <vendor / project>
- License: <SPDX identifier>
- Author: <person or org>
- URL: <upstream URL>
- Vendored at: <repo path>
- License text: <repo path to LICENSE file>
- Pick rationale: <doc link, if applicable>
```
