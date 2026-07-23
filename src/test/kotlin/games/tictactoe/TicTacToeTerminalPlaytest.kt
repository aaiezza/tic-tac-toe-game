package games.tictactoe

import games.core.GameEngine
import games.core.GameOutcome
import games.core.PlayerId
import games.core.Progression
import games.core.TransitionCause

object TicTacToeTerminalPlaytest {
    @JvmStatic
    fun main(args: Array<String>) {
        val x = PlayerId("Ada")
        val o = PlayerId("Grace")
        val (game, initial) = TicTacToe.newGame(x, o)
        val engine = GameEngine(game)
        val moves = listOf(
            Cell(Row(0), Column(0)),
            Cell(Row(1), Column(0)),
            Cell(Row(0), Column(1)),
            Cell(Row(1), Column(1)),
            Cell(Row(0), Column(2)),
        )
        var state = initial

        println("Tic-Tac-Toe: deterministic transition trace\n")
        println(TicTacToeTerminalRenderer.renderState("Initial authoritative state", state))

        moves.forEach { cell ->
            val actor = checkNotNull(state.currentPlayer)
            val mark = state.requireActiveMark()
            println("\n${actor.value} chooses ${TicTacToeTerminalRenderer.describe(cell)} as $mark")
            val progression = engine.playWithTrace(state, actor, PlaceMark(cell))
            println(TicTacToeTerminalRenderer.renderProgression(progression))
            state = progression.resultingState
        }

        check(game.outcome(state) == GameOutcome.PlayerWon(x))
        check(state.status == TicTacToeStatus.Won(x, Mark.X))
        println("\n${x.value} wins as X.")
    }
}

internal object TicTacToeTerminalRenderer {
    fun renderProgression(progression: Progression<TicTacToeState, TicTacToeEvent>): String = buildString {
        progression.steps.forEachIndexed { index, step ->
            val cause = when (val cause = step.cause) {
                is TransitionCause.PlayerDriven -> {
                    val intent = cause.intent as PlaceMark
                    val mark = (step.events.single() as TicTacToeEvent.MarkPlaced).mark
                    "Player-driven: ${cause.actor.value} places $mark at ${describe(intent.cell)}"
                }

                is TransitionCause.RuleDriven -> "Rule-driven: ${cause.rule.value}"
            }
            appendLine("  Step ${index + 1} - $cause")
            step.events.forEach { appendLine("    ${describe(it)}") }
            appendLine(renderState("Resulting state", step.resultingState).prependIndent("    "))
        }
    }.trimEnd()

    fun renderState(label: String, state: TicTacToeState): String = buildString {
        appendLine(label)
        appendLine(
            when (val status = state.status) {
                is TicTacToeStatus.AwaitingPlacement ->
                    "Status: awaiting ${status.activeMark} placement from ${status.turn.owner.value}"

                is TicTacToeStatus.ResolvingPlacement ->
                    "Status: resolving ${status.mark} placement by ${status.player.value}"

                is TicTacToeStatus.Won ->
                    "Status: won by ${status.winner.value} as ${status.winningMark}"

                TicTacToeStatus.Draw -> "Status: draw"
            },
        )
        append(renderBoard(state.cells))
    }

    fun describe(cell: Cell) = "row ${cell.row.value + 1}, column ${cell.column.value + 1}"

    private fun describe(event: TicTacToeEvent): String = when (event) {
        is TicTacToeEvent.MarkPlaced -> "Placed ${event.mark} at ${describe(event.cell)}"
        is TicTacToeEvent.TurnAdvanced -> "Advanced to ${event.nextPlayer.value} playing ${event.nextMark}"
        is TicTacToeEvent.GameWon -> "${event.winner.value} wins as ${event.winningMark}"
        TicTacToeEvent.GameDrawn -> "Completed the game as a draw"
    }

    private fun renderBoard(cells: Map<Cell, Mark>): String = buildString {
        appendLine("    1   2   3")
        for (row in 0..2) {
            append("${row + 1}   ")
            append(
                (0..2).joinToString(" | ") { column ->
                    cells[Cell(Row(row), Column(column))]?.name ?: " "
                },
            )
            if (row < 2) appendLine("\n   ---+---+---") else appendLine()
        }
    }.trimEnd()
}
