# MaceRituals (Paper 1.21.11)

Custom heavy cores drop from ominous trial vaults. Put one in the 2x2 inventory crafting grid with a
Breeze Rod and click the result: both items are consumed and a ritual starts right where you stand
(no arena needed). The whole server is told who started it and where. Survive every wave to receive the
custom mace, which has a passive effect and a right-click ability.

## Build
Push to GitHub; the included workflow builds the jar (Actions tab -> Artifacts).
Or with JDK 21 + Maven:  `mvn package`  ->  `target/MaceRituals.jar`

## Test
1. `/ritual givecore <you> inferno` and get a Breeze Rod.
2. Put both in your inventory crafting grid and click the result.
3. Edit `plugins/MaceRituals/config.yml`, then `/ritual reload`.
Arenas are optional: set `ritual.use-arenas: true` and use `/ritual arena create <name>`.
