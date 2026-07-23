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
    data class TurnAdvanced(val nextPlayer: PlayerId, val nextMark: Mark) : TicTacToeEvent
    data class GameWon(val winner: PlayerId, val winningMark: Mark) : TicTacToeEvent
    data object GameDrawn : TicTacToeEvent
}
data class TicTacToeConfiguration(override val name: String = "Classic Tic-Tac-Toe") : GameConfiguration
class Players private constructor(val x: PlayerId, val o: PlayerId) : PlayerRegistry {
    override val players = setOf(x, o)

    fun markOf(player: PlayerId): Mark {
        require(player in players) { "Unknown player: $player" }
        require(x != o) { "A self-playing player controls both marks; use the active mark." }
        return when (player) {
            x -> Mark.X
            o -> Mark.O
            else -> error("Unknown player: $player")
        }
    }

    fun playerOn(mark: Mark) = when (mark) {
        Mark.X -> x
        Mark.O -> o
    }

    companion object {
        fun opponents(x: PlayerId, o: PlayerId): Players {
            require(x != o) { "Opposing marks require distinct players; use selfPlay for one player." }
            return Players(x, o)
        }

        fun selfPlay(player: PlayerId) = Players(player, player)
    }
}

sealed interface TicTacToeStatus {
    val turn: TurnContext?

    data class AwaitingPlacement(override val turn: TurnContext, val activeMark: Mark) : TicTacToeStatus

    data class ResolvingPlacement(val player: PlayerId, val mark: Mark) : TicTacToeStatus {
        override val turn = null
    }

    data class Won(val winner: PlayerId, val winningMark: Mark) : TicTacToeStatus {
        override val turn = null
    }

    data object Draw : TicTacToeStatus {
        override val turn = null
    }
}

data class TicTacToeState(
    val cells: Map<Cell, Mark>,
    val registry: Players,
    val status: TicTacToeStatus,
    override val turnNumber: TurnNumber = TurnNumber(0),
    override val history: EventHistory<TicTacToeEvent> = EventHistory(),
) : BoardGameState<TicTacToeEvent, Map<Cell, Mark>>,
    HistoryWritableState<TicTacToeEvent> {
    override val board get() = cells
    override val turn get() = status.turn

    init {
        when (val status = status) {
            is TicTacToeStatus.AwaitingPlacement -> {
                val activePlayer = registry.playerOn(status.activeMark)
                require(status.turn.owner == activePlayer) { "The turn owner must control the active mark." }
                require(status.turn.decisionActors == setOf(activePlayer)) { "Only the active mark's player may act." }
            }

            is TicTacToeStatus.ResolvingPlacement -> require(status.player == registry.playerOn(status.mark)) {
                "The player must control the mark being resolved."
            }

            is TicTacToeStatus.Won -> require(status.winner == registry.playerOn(status.winningMark)) {
                "The winner must control the winning mark."
            }

            TicTacToeStatus.Draw -> Unit
        }
    }

    override fun withHistory(history: EventHistory<TicTacToeEvent>) = copy(history = history)
}
class TicTacToe private constructor() : ConfigurableGameDefinition<TicTacToeState, PlaceMark, TicTacToeEvent, TicTacToeConfiguration> {
    override val configuration = TicTacToeConfiguration()
    override fun legalIntents(state: TicTacToeState) = if (outcome(state) != GameOutcome.InProgress) emptySet() else allCells().filterNot(state.cells::containsKey).map { LegalIntent(PlaceMark(it)) }.toSet()
    override fun resolve(state: TicTacToeState, actor: PlayerId, intent: PlaceMark): Resolution<TicTacToeEvent> {
        val mark = state.requireActiveMark()
        val placed = TicTacToeEvent.MarkPlaced(intent.cell, mark)
        val preview = reduce(state, placed)
        val completion = boardCompletion(preview.cells)
        val rule: ResolutionStep<TicTacToeEvent> = when (completion) {
            is BoardCompletion.Won -> {
                val winner = state.registry.playerOn(completion.mark)
                ResolutionStep.RuleDriven(GAME_END_RULE, listOf(TicTacToeEvent.GameWon(winner, completion.mark)))
            }

            BoardCompletion.Draw ->
                ResolutionStep.RuleDriven(GAME_END_RULE, listOf(TicTacToeEvent.GameDrawn))

            null -> {
                val nextMark = mark.opponent()
                ResolutionStep.RuleDriven(
                    AUTOMATIC_TURN_YIELD_RULE,
                    listOf(TicTacToeEvent.TurnAdvanced(state.registry.playerOn(nextMark), nextMark)),
                )
            }
        }
        return Resolution(
            listOf(
                ResolutionStep.PlayerDriven(listOf(placed)),
                rule,
            ),
        )
    }
    override fun reduce(state: TicTacToeState, event: TicTacToeEvent): TicTacToeState = when (event) {
        is TicTacToeEvent.MarkPlaced -> state.copy(
            cells = state.cells + (event.cell to event.mark),
            status = TicTacToeStatus.ResolvingPlacement(
                player = checkNotNull(state.turnOwner),
                mark = event.mark,
            ),
        )

        is TicTacToeEvent.TurnAdvanced -> state.copy(
            status = TicTacToeStatus.AwaitingPlacement(TurnContext(event.nextPlayer), event.nextMark),
            turnNumber = TurnNumber(state.turnNumber.value + 1),
        )

        is TicTacToeEvent.GameWon -> state.copy(
            status = TicTacToeStatus.Won(event.winner, event.winningMark),
            turnNumber = TurnNumber(state.turnNumber.value + 1),
        )

        TicTacToeEvent.GameDrawn -> state.copy(
            status = TicTacToeStatus.Draw,
            turnNumber = TurnNumber(state.turnNumber.value + 1),
        )
    }
    override fun outcome(state: TicTacToeState): GameOutcome = when (val status = state.status) {
        is TicTacToeStatus.AwaitingPlacement -> GameOutcome.InProgress
        is TicTacToeStatus.ResolvingPlacement -> GameOutcome.InProgress
        is TicTacToeStatus.Won -> GameOutcome.PlayerWon(status.winner)
        TicTacToeStatus.Draw -> GameOutcome.Draw
    }

    companion object {
        fun newGame(x: PlayerId, o: PlayerId): Pair<TicTacToe, TicTacToeState> {
            val registry = Players.opponents(x, o)
            return create(registry)
        }

        fun newSelfPlayGame(player: PlayerId): Pair<TicTacToe, TicTacToeState> = create(Players.selfPlay(player))

        private fun create(registry: Players): Pair<TicTacToe, TicTacToeState> {
            val first = registry.playerOn(Mark.X)
            return TicTacToe() to TicTacToeState(
                cells = emptyMap(),
                registry = registry,
                status = TicTacToeStatus.AwaitingPlacement(TurnContext(first), Mark.X),
            )
        }

        private val AUTOMATIC_TURN_YIELD_RULE = RuleId("tic-tac-toe.automatic-turn-yield")
        private val GAME_END_RULE = RuleId("tic-tac-toe.game-end")
        private fun allCells() = (0..2).flatMap { r -> (0..2).map { c -> Cell(Row(r), Column(c)) } }
        private fun lines(): List<List<Cell>> = (0..2).map { r -> (0..2).map { c -> Cell(Row(r), Column(c)) } } + (0..2).map { c -> (0..2).map { r -> Cell(Row(r), Column(c)) } } + listOf(listOf(Cell(Row(0), Column(0)), Cell(Row(1), Column(1)), Cell(Row(2), Column(2))), listOf(Cell(Row(0), Column(2)), Cell(Row(1), Column(1)), Cell(Row(2), Column(0))))

        private fun boardCompletion(cells: Map<Cell, Mark>): BoardCompletion? {
            Mark.entries.firstOrNull { mark -> lines().any { line -> line.all { cells[it] == mark } } }
                ?.let { return BoardCompletion.Won(it) }
            return BoardCompletion.Draw.takeIf { cells.size == 9 }
        }
    }
}

private sealed interface BoardCompletion {
    data class Won(val mark: Mark) : BoardCompletion
    data object Draw : BoardCompletion
}

val TicTacToeState.activeMark: Mark?
    get() = (status as? TicTacToeStatus.AwaitingPlacement)?.activeMark

fun TicTacToeState.requireActiveMark(): Mark = checkNotNull(activeMark) { "The game is complete." }

fun Mark.opponent() = when (this) {
    Mark.X -> Mark.O
    Mark.O -> Mark.X
}
