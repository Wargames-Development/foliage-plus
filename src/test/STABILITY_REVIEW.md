# Foliage+ stability and compatibility review

Reviewed 23 October 2026. Pack inspection was read-only. Changes are confined to
Foliage+'s `src/` tree; the existing uncommitted changes and version properties
were preserved. No mod was installed in Release Testing.

## Confirmed defect: invisible NTM beams trigger foliage detection

The installed NTM jar contains `com.hbm.blocks.machine.BlockBeamBase`. Its
`isLeaves()` and `isAir()` methods both return true, and its collision box is
null. The local NTM source explains that the leaf flag lets trees grow through
floodlight beams. It is a tree-generation classification, not physical foliage.

The installed Foliage+ X13 jar uses the same raw block-bounds intersection
algorithm as this repository's committed original hook. Even a zero-size beam
box can satisfy Minecraft's `intersectsWith` comparisons when its point lies
inside the player's body. This reproduces slowdown in visually empty space.
The working-tree cell-based contact fix that existed before this review still
accepted these invisible blocks because it also trusted `isLeaves()` alone.

**Verification:** an isolated copy of the committed original hook reproduced
the zero-volume beam false positive with real Minecraft AABBs. The regression
case passes with the revised hook. Installed NTM and Foliage+ bytecode were
inspected directly, in addition to reading local source.

**Fix:** collision, slowdown, sound contact, and the full-cover restriction now
share a foliage rule: `BlockLeaves` or `Material.leaves`, `isLeaves() == true`,
and `isAir() == false`. Invisible beam blocks are excluded.

## Other causes and changes

| Issue | Evidence and resulting behavior |
| --- | --- |
| Slowdown after leaving leaves | The old smoothing path approached normal speed over about 9 ticks at the default 0.5 multiplier, up to 17 ticks at 0.01. Exit now removes the owned modifier immediately at the next authoritative contact sample. Entry remains smooth. |
| Mutable singleton block bounds | The original detector reads bounds that rendering or other block operations can change. The existing working-tree fix uses stable leaf cells and a 0.01 overlap tolerance; this is retained. Standing on, touching, or barely brushing a leaf face does not count as entry. |
| Collision/contact disagreement | The old detector ignored entity eligibility and `allowFullCover`. Both now use the same predicate, including the rule that the lower block of a stacked leaf column stays solid when full cover is disabled. |
| Solid Et Futurum blocks classified as leaves | Installed shroomlight and wart blocks return true from `isLeaves()`. They now retain normal solidity and never cause foliage slowdown. |
| Client/server configuration disagreement | Previously each side loaded its own collision flags. A versioned optional channel now sends server settings to supporting clients. Remote settings affect client worlds only, do not rewrite the client's config file, and are cleared on disconnect. |
| Stale connection state | Sync subscriptions are keyed by connection, survive the interval where Forge clears `playerNetServerHandler` during login, and survive player replacement on respawn. Late packets and disconnects from an older connection cannot change the new session's settings. |
| Client/server speed interference | The existing working-tree server-only attribute ownership and per-entity state are retained. Player speed and sound use one contact sample at PlayerTick END; the living-update path handles other living entities. |
| Persistent or unrelated movement penalties | Only the Foliage+ UUID is added/removed. Its modifier is not saved. Existing logout, respawn, dimension, death, and world-unload cleanup is retained and tested. Flight, noclip, and mounted riders clear the penalty. |
| Custom leaf collision overrides | The original ASM hook affected the base Block implementation only. A narrow World entity-collision call-site guard now covers overrides that do not call `super`, while retaining virtual dispatch for other blocks and null-entity collision queries. |
| Transformer robustness | Matching now uses exact MCP/SRG/obfuscated descriptors, rejects static/native/abstract lookalikes, and avoids duplicate injection. The World rewrite preserves the existing stack and frames. |
| Unsafe contact queries | Wrong-world entities, invalid/oversized boxes, and unloaded chunks are rejected without requesting chunks. Nonfinite speed settings resolve to normal speed. |
| Malformed mod metadata | The packaged `mcmod.info` description had a missing closing quote. Its JSON is now repaired and parsed in a regression case. |

## Release Testing compatibility assessment

Pack inspected:
`PrismLauncher/instances/Release Testing/minecraft/mods`.

| Installed component | Assessment |
| --- | --- |
| `HBM-NTM-[1.0.27_X5852_H261_WG2.4.0].jar` | Invisible beams excluded. `BlockRubberLeaves` inherits `BlockLeaves` and remains eligible. Armor/weapon movement modifiers retain their own UUIDs, operations, and base values. Attribute coexistence is tested using real Minecraft attributes. |
| `etfuturum-3.5.28-master.19-c4001bcfb1.jar` | `BaseLeaves` subclasses remain eligible. The local fork's generic mangrove/pale-oak blocks use leaf material and remain eligible. Shroomlight and wart blocks remain solid. Powder-snow frost uses a separate attribute UUID and is preserved. |
| `BetterFoliage-LegacyEdition-1.2.4.jar` | Detection does not depend on visual leaf extensions or render-mutated singleton bounds. Its extra rendered foliage is not itself a world block. Full live rendering/collision interaction remains to be checked. |
| `angelica-2.2.14.jar`, `CoreTweaks-1.7.10-0.3.3.2.jar`, `+unimixins-all-1.7.10-0.2.1.jar` | Real Forge Block/World bytecode and synthetic naming variants pass ASM analysis. This does not simulate the complete pack's transformer ordering/cache. Check both hook messages during a live launch. |
| `FMUR-WDG-Edition-1.8.06_X170-dev.jar`, MCHeli fork sources | Vehicle/projectile leaf rules remain owned by those mods. Foliage+ does not alter projectile ray traces or movement attribute base values; mounted players are exempt from its speed modifier. Vehicle gameplay is not certified by the headless tests. |
| `WGCore-2.7.5_X203.jar`, `WGHangers-0.1.0_X48.jar` | Source searches found no competing use of the Foliage+ speed UUID or added foliage collision override. No changes made to those projects. |
| `kinetic anti-cheat-7.3.1.jar` | The inspected jar contains mod-checker/reporting classes, rather than movement/collision transformer classes. Server-side movement/anti-cheat behavior still requires a multiplayer test. |
| `FoliagePlus-1.1.1_X13-release.jar` | Installed pack jar still contains the old detector. It was not replaced. Both server and clients need the revised build to gain settings synchronization; older versions remain protocol-compatible but cannot receive it. |

Relevant inspected jar SHA-256 values, to identify this snapshot while other
chats update the pack:

```text
Foliage+ X13: d980eee9144b0aee907c3595571b8cd48a203051ff91f1910f49a419e16c4cd3
NTM:         27a28842b92f5c4156896ab8f32a747b01c4f314ea79ba0acc66583bae94e47b
Et Futurum:  2d2b6bc3088ee8b8d459b02a11de46d1d4de91efd9f191ee1616b5fdbcb187eb
```

## Automated verification

Use Java 8 and the source-only Gradle init script:

```sh
bash gradlew --offline -I src/test/gradle/regressions.init.gradle test -x incrementBuildVersion --no-daemon
bash gradlew --offline -I src/test/gradle/regressions.init.gradle build -x incrementBuildVersion --no-daemon
```

The runner needs no new test dependencies and leaves the production dependency
list unchanged. It uses isolated world/entity fixtures, real Forge events,
Minecraft AABBs and attributes, Netty embedded channels, and ASM analysis.
It does not launch Minecraft, load a save, or require a graphics context.

32 regression cases cover the beam defect, false leaf classifications, negative
coordinates and height limits, boundary contact, stale block bounds, unloaded
chunks, toggles/full cover, immediate exit and lifecycle cleanup, other speed
modifiers, server-only ownership, rustle cooldown, subclass collision dispatch,
all three naming environments, real Block/World bytecode, packet serialization,
handshake delay, respawn, old clients, unregister, closed connections, and
reconnect isolation, and valid mod metadata.

## Required live acceptance checks

These checks have **not** been performed. Automated results establish the
specific code behavior above; they do not certify the entire moving modpack.

1. Launch an isolated client and dedicated-server test copy with the revised
   Foliage+ build on both sides. Confirm Block and World collision hook messages.
2. Cross NTM floodlight beams in empty space with floodlights on/off. No foliage
   slowdown or rustle should occur.
3. Walk, sprint, strafe, crouch, jump, and stand on every vanilla, Et Futurum,
   NTM, and HarvestCraft leaf type available in the pack. Contact should match
   real block cells, including negative coordinates and chunk boundaries.
4. Leave foliage, remove a touching leaf block, teleport away, relog, die,
   respawn, and change dimension. Foliage+ should retain no speed penalty after
   its next server contact sample.
5. Test mismatched client/server config files. Supporting clients should follow
   server collision settings while connected and restore local settings after
   disconnect. Test both full-cover options and all-entities options.
6. Combine leaves with sprint, potions, NTM armor/held weapons, and powder-snow
   frost. Leaving leaves must remove only the foliage penalty.
7. Verify rustles are audible to another player, occur once per server cooldown,
   and stop outside foliage. Repeat under realistic latency.
8. Test vehicles, passenger changes, spectator/noclip, creative flight, and
   affected mobs. Verify unrelated solid blocks, projectiles, scaffolding,
   powder snow, and multipart blocks retain their own collision behavior.
9. Enable `general.debugContactTransitions` for reproduction if needed. It logs
   canonical contact and rejected legacy matches, including zero-volume beam
   points. Disable it after diagnosis to avoid unnecessary scans/logging.

Nonstandard leaf blocks using neither `BlockLeaves` nor `Material.leaves` need
explicit compatibility review. This restriction is intentional: `isLeaves()`
alone demonstrably includes functional and invisible blocks in this pack.
