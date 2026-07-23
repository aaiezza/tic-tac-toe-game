package games.tictactoe

import games.core.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicTacToeIntegrationTest {
    @Test fun `x wins across the top row`() {
        val x = PlayerId("x")
        val o = PlayerId("o")
        val (game, initial) = TicTacToe.newGame(x, o)
        val engine = GameEngine(game)
        fun move(state: TicTacToeState, player: PlayerId, r: Int, c: Int) = engine.play(state, player, PlaceMark(Cell(Row(r), Column(c))))
        var state = initial
        state = move(state, x, 0, 0)
        state = move(state, o, 1, 0)
        state = move(state, x, 0, 1)
        state = move(state, o, 1, 1)
        state = move(state, x, 0, 2)
        assertEquals(GameOutcome.PlayerWon(x), game.outcome(state))
        assertEquals(TicTacToeStatus.Won(x, Mark.X), state.status)
        assertNull(state.turn)
        assertEquals(10, state.history.events.size)
        assertTrue(game.legalIntents(state).isEmpty())
        assertEquals(
            TransitionCause.RuleDriven(RuleId("tic-tac-toe.game-end")),
            state.history.transitions.last().cause,
        )
    }

    @Test fun `a move returns its placement and automatic turn-yield trace`() {
        val x = PlayerId("x")
        val o = PlayerId("o")
        val (game, initial) = TicTacToe.newGame(x, o)

        val progression = GameEngine(game).playWithTrace(initial, x, PlaceMark(Cell(Row(1), Column(1))))

        assertEquals(2, progression.steps.size)
        assertTrue(progression.steps.first().cause is TransitionCause.PlayerDriven)
        assertEquals(
            TransitionCause.RuleDriven(RuleId("tic-tac-toe.automatic-turn-yield")),
            progression.steps.last().cause,
        )
        assertEquals(o, progression.resultingState.currentPlayer)
        assertEquals(Mark.O, progression.resultingState.activeMark)
    }

    @Test fun `full board without a line completes as a draw`() {
        val x = PlayerId("x")
        val o = PlayerId("o")
        val (game, initial) = TicTacToe.newGame(x, o)
        val engine = GameEngine(game)
        val moves = listOf(
            x to Cell(Row(0), Column(0)),
            o to Cell(Row(0), Column(1)),
            x to Cell(Row(0), Column(2)),
            o to Cell(Row(1), Column(1)),
            x to Cell(Row(1), Column(0)),
            o to Cell(Row(1), Column(2)),
            x to Cell(Row(2), Column(1)),
            o to Cell(Row(2), Column(0)),
            x to Cell(Row(2), Column(2)),
        )

        val result = moves.fold(initial) { state, (actor, cell) ->
            engine.play(state, actor, PlaceMark(cell))
        }

        assertEquals(TicTacToeStatus.Draw, result.status)
        assertEquals(GameOutcome.Draw, game.outcome(result))
        assertNull(result.turn)
        assertEquals(TicTacToeEvent.GameDrawn, result.history.events.last().event)
    }

    @Test fun `one player can intentionally control both marks`() {
        val player = PlayerId("computer")
        val (game, initial) = TicTacToe.newSelfPlayGame(player)

        val result = GameEngine(game).play(
            initial,
            player,
            PlaceMark(Cell(Row(1), Column(1))),
        )

        assertEquals(player, result.currentPlayer)
        assertEquals(Mark.O, result.activeMark)
        assertEquals(setOf(player), result.registry.players)
    }

    @Test fun `ordinary game rejects one player controlling both marks implicitly`() {
        val player = PlayerId("same")

        assertThrows(IllegalArgumentException::class.java) {
            TicTacToe.newGame(player, player)
        }
    }

    @Test fun `status and registry reject players assigned to the wrong domain role`() {
        val x = PlayerId("x")
        val o = PlayerId("o")
        val (_, state) = TicTacToe.newGame(x, o)

        assertThrows(IllegalArgumentException::class.java) {
            state.copy(status = TicTacToeStatus.Won(o, Mark.X))
        }
        assertThrows(IllegalArgumentException::class.java) {
            state.registry.markOf(PlayerId("unknown"))
        }
    }
}
