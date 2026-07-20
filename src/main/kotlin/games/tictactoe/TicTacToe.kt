package games.tictactoe

import games.core.*

@JvmInline value class Row(val value: Int) {
    init {
        require(value in 0..2)
    }
}

@JvmInline value class Column(val value: Int) {
    init {
        require(value in 0..2)
    }
}
data class Cell(val row: Row, val column: Column)
enum class Mark { X, O }
data class PlaceMark(val cell: Cell) : PlayerIntent
sealed interface TicTacToeEvent : GameEvent {
    data class MarkPlaced(val cell: Cell, val mark: Mark) : TicTacToeEvent
    data class TurnAdvanced(val nextPlayer: PlayerId?) : TicTacToeEvent
}
data class TicTacToeConfiguration(override val name: String = "Classic Tic-Tac-Toe") : GameConfiguration
data class Players(val x: PlayerId, val o: PlayerId) : PlayerRegistry {
    override val players = setOf(x, o)
    fun markOf(player: PlayerId) = when (player) {
        x -> Mark.X
        o -> Mark.O
        else -> error("Unknown player")
    }
    fun playerOf(mark: Mark) = if (mark == Mark.X) x else o
    fun opponent(player: PlayerId) = if (player == x) o else x
}
data class TicTacToeState(
    val cells: Map<Cell, Mark>,
    val registry: Players,
    override val turn: TurnContext?,
    override val turnNumber: TurnNumber = TurnNumber(0),
    override val history: EventHistory<TicTacToeEvent> = EventHistory(),
) : BoardGameState<TicTacToeEvent, Map<Cell, Mark>>,
    HistoryWritableState<TicTacToeEvent> {
    override val board get() = cells
    override fun withHistory(history: EventHistory<TicTacToeEvent>) = copy(history = history)
}
class TicTacToe private constructor() : ConfigurableGameDefinition<TicTacToeState, PlaceMark, TicTacToeEvent, TicTacToeConfiguration> {
    override val configuration = TicTacToeConfiguration()
    override fun legalIntents(state: TicTacToeState) = if (outcome(state) != GameOutcome.InProgress) emptySet() else allCells().filterNot(state.cells::containsKey).map { LegalIntent(PlaceMark(it)) }.toSet()
    override fun resolve(state: TicTacToeState, actor: PlayerId, intent: PlaceMark): Resolution<TicTacToeEvent> {
        val placed = TicTacToeEvent.MarkPlaced(intent.cell, state.registry.markOf(actor))
        val preview = reduce(state, placed)
        val nextPlayer = if (outcome(preview) == GameOutcome.InProgress) state.registry.opponent(actor) else null
        return Resolution(
            listOf(
                ResolutionStep.PlayerDriven(listOf(placed)),
                ResolutionStep.RuleDriven(AUTOMATIC_TURN_YIELD_RULE, listOf(TicTacToeEvent.TurnAdvanced(nextPlayer))),
            ),
        )
    }
    override fun reduce(state: TicTacToeState, event: TicTacToeEvent): TicTacToeState = when (event) {
        is TicTacToeEvent.MarkPlaced -> state.copy(cells = state.cells + (event.cell to event.mark))

        is TicTacToeEvent.TurnAdvanced -> state.copy(
            turn = event.nextPlayer?.let(::TurnContext),
            turnNumber = TurnNumber(state.turnNumber.value + 1),
        )
    }
    override fun outcome(state: TicTacToeState): GameOutcome {
        Mark.entries.firstOrNull { mark -> lines().any { line -> line.all { state.cells[it] == mark } } }?.let { return GameOutcome.PlayerWon(state.registry.playerOf(it)) }
        return if (state.cells.size == 9) GameOutcome.Draw else GameOutcome.InProgress
    }
    companion object {
        fun newGame(x: PlayerId, o: PlayerId): Pair<TicTacToe, TicTacToeState> {
            require(x != o)
            return TicTacToe() to TicTacToeState(emptyMap(), Players(x, o), TurnContext(x))
        }
        private val AUTOMATIC_TURN_YIELD_RULE = RuleId("tic-tac-toe.automatic-turn-yield")
        private fun allCells() = (0..2).flatMap { r -> (0..2).map { c -> Cell(Row(r), Column(c)) } }
        private fun lines(): List<List<Cell>> = (0..2).map { r -> (0..2).map { c -> Cell(Row(r), Column(c)) } } + (0..2).map { c -> (0..2).map { r -> Cell(Row(r), Column(c)) } } + listOf(listOf(Cell(Row(0), Column(0)), Cell(Row(1), Column(1)), Cell(Row(2), Column(2))), listOf(Cell(Row(0), Column(2)), Cell(Row(1), Column(1)), Cell(Row(2), Column(0))))
    }
}
