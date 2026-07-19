package games.tictactoe

import games.core.*
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(5, state.history.events.size)
        assertTrue(game.legalIntents(state).isEmpty())
    }
}
