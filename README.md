# Tic-Tac-Toe

Immutable, event-driven Tic-Tac-Toe implemented with the Board Game Framework.

Each placement produces a player-driven mark event followed by either a rule-driven automatic turn yield or an explicit game-completion event. Use `GameEngine.playWithTrace` to inspect both semantic steps.

`TicTacToeStatus` makes the authoritative lifecycle exhaustive:

- `AwaitingPlacement(turn, activeMark)`
- `ResolvingPlacement(player, mark)`
- `Won(winner, winningMark)`
- `Draw`

`ResolvingPlacement` is an intermediate trace state: the placement has occurred, no player may submit another intent, and the rule-driven win/draw check and turn advancement remain pending. The other statuses are authoritative persistence boundaries. The state derives its nullable framework `turn` projection from this status, preventing an active turn from being combined with a terminal outcome. Mark remains a domain role distinct from player identity, so ordinary games require different X and O players while intentional self-play is explicit:

```kotlin
val (game, state) = TicTacToe.newSelfPlayGame(computer)
```

## Terminal playtest

Run a complete deterministic game through the public engine while printing every player- and rule-driven transition:

```bash
mvn -Pterminal test-compile exec:java
```

The terminal acts as a UI adapter. It submits a fixed sequence of ordinary `PlaceMark` intents, renders each transition and intermediate board, and asserts that Ada wins as X.

## Verification

Run `mvn spotless:apply` to format Kotlin sources and `mvn verify` to check formatting and tests.
