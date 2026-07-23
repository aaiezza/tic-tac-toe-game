# Tic-Tac-Toe

Immutable, event-driven Tic-Tac-Toe implemented with the Board Game Framework.

Each placement produces a player-driven mark event followed by either a rule-driven automatic turn yield or an explicit game-completion event. Use `GameEngine.playWithTrace` to inspect both semantic steps.

`TicTacToeStatus` makes the authoritative lifecycle exhaustive:

- `AwaitingPlacement(turn, activeMark)`
- `Won(winner, winningMark)`
- `Draw`

The state derives its nullable framework `turn` projection from this status, preventing an active turn from being combined with a terminal outcome. Mark remains a domain role distinct from player identity, so ordinary games require different X and O players while intentional self-play is explicit:

```kotlin
val (game, state) = TicTacToe.newSelfPlayGame(computer)
```

Run `mvn spotless:apply` to format Kotlin sources and `mvn verify` to check formatting and tests.
