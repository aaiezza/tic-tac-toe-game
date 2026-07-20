# Tic-Tac-Toe

Immutable, event-driven Tic-Tac-Toe implemented with the Board Game Framework.

Each placement produces a player-driven mark event followed by a rule-driven automatic turn yield. Use `GameEngine.playWithTrace` to inspect both semantic steps.

Run `mvn spotless:apply` to format Kotlin sources and `mvn verify` to check formatting and tests.
