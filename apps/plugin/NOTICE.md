# Third-Party Notices

This plugin includes data and code adapted from the open-source RuneLite plugins below.

## shortest-path

- **Source:** <https://github.com/Skretzo/shortest-path>
- **License:** BSD-2-Clause
- **What we use:**
  - `src/main/resources/collision-map.zip` — verbatim, refresh via `./gradlew refreshCollisionMap`.
  - `src/main/resources/transports/*.tsv` — verbatim transport data, ingested into our `transports.json` via `./gradlew refreshTransportDb`.
  - Data structures and BFS algorithm in `co.rowm.osrsllm.pathfinder` — adapted/simplified Kotlin port of their `SplitFlagMap`, `FlagMap`, `CollisionMap`, and `Pathfinder` (stripped to pure tile walking; no transport-graph traversal).

```
Copyright (c) 2022-, Skretzo
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## How to refresh

When shortest-path ships an updated `collision-map.zip` (e.g. Jagex changes the world
map), run:

```
./gradlew refreshCollisionMap
git add src/main/resources/collision-map.zip
git commit -m "Refresh collision-map.zip from shortest-path master"
```

Transport data:

```
./gradlew refreshTransportDb
```
