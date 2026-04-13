package com.example.match3

import kotlin.math.abs
import kotlin.random.Random

data class Cell(val row: Int, val col: Int)
data class FallMove(val fromRow: Int, val toRow: Int, val col: Int, val gem: Int)
data class SpawnMove(val startRow: Int, val toRow: Int, val col: Int, val gem: Int)
data class MatchRules(
    val minLineMatch: Int = 3,
    val enableSquareMatch: Boolean = false
)

sealed class BoardAnimStep {
    data class Swap(val first: Cell, val second: Cell) : BoardAnimStep()
    data class Clear(val cells: List<Cell>) : BoardAnimStep()
    data class Fall(val moves: List<FallMove>) : BoardAnimStep()
    data class Spawn(val moves: List<SpawnMove>) : BoardAnimStep()
}

data class MoveResult(
    val accepted: Boolean,
    val startBoard: Array<IntArray>,
    val steps: List<BoardAnimStep>,
    val finalScore: Int,
    val clearedGems: Int,
    val clearCounts: List<Int>
)

class Match3Engine(
    private val rows: Int = 8,
    private val cols: Int = 8,
    private val gemTypes: Int = 5
) {
    companion object {
        const val SPECIAL_ROW_BASE = 100
        const val SPECIAL_BOMB_BASE = 200
        const val SPECIAL_COLOR_BOMB = 300
    }

    private val random = Random(System.currentTimeMillis())
    private val board = Array(rows) { IntArray(cols) }

    var score: Int = 0
        private set

    init {
        fillBoardWithoutInitialMatches()
    }

    fun reset() {
        score = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                board[r][c] = 0
            }
        }
        fillBoardWithoutInitialMatches()
    }

    fun size(): Pair<Int, Int> = rows to cols

    fun gemAt(row: Int, col: Int): Int = board[row][col]

    fun snapshotBoard(): Array<IntArray> = board.map { it.clone() }.toTypedArray()

    fun trySwapAnimated(
        r1: Int,
        c1: Int,
        r2: Int,
        c2: Int,
        rules: MatchRules = MatchRules()
    ): MoveResult {
        val startBoard = snapshotBoard()
        if (!isInside(r1, c1) || !isInside(r2, c2) || !areAdjacent(r1, c1, r2, c2)) {
            return MoveResult(
                accepted = false,
                startBoard = startBoard,
                steps = emptyList(),
                finalScore = score,
                clearedGems = 0,
                clearCounts = emptyList()
            )
        }

        val steps = mutableListOf<BoardAnimStep>()
        val first = Cell(r1, c1)
        val second = Cell(r2, c2)
        var totalCleared = 0
        val clearCounts = mutableListOf<Int>()
        swap(r1, c1, r2, c2)
        steps += BoardAnimStep.Swap(first, second)

        val specialSwapClear = buildSpecialSwapClearSet(first, second)
        if (specialSwapClear.isNotEmpty()) {
            val expanded = expandSpecialEffects(specialSwapClear.toMutableSet())
            val clearCells = expanded.map { Cell(it.first, it.second) }
            totalCleared += clearCells.size
            clearCounts += clearCells.size
            score += clearCells.size * 10
            steps += BoardAnimStep.Clear(clearCells)
            clearMatches(expanded)
            val fallMoves = collapseColumnsWithMoves()
            if (fallMoves.isNotEmpty()) steps += BoardAnimStep.Fall(fallMoves)
            val spawnMoves = refillBoardWithMoves()
            if (spawnMoves.isNotEmpty()) steps += BoardAnimStep.Spawn(spawnMoves)

            var matches = findMatches(rules)
            while (matches.isNotEmpty()) {
                val withSpecials = applySpecialCreation(matches)
                val expandedLoop = expandSpecialEffects(withSpecials.toMutableSet())
                val clearLoop = expandedLoop.map { Cell(it.first, it.second) }
                totalCleared += clearLoop.size
                clearCounts += clearLoop.size
                score += clearLoop.size * 10
                steps += BoardAnimStep.Clear(clearLoop)
                clearMatches(expandedLoop)
                val loopFall = collapseColumnsWithMoves()
                if (loopFall.isNotEmpty()) steps += BoardAnimStep.Fall(loopFall)
                val loopSpawn = refillBoardWithMoves()
                if (loopSpawn.isNotEmpty()) steps += BoardAnimStep.Spawn(loopSpawn)
                matches = findMatches(rules)
            }

            return MoveResult(
                accepted = true,
                startBoard = startBoard,
                steps = steps,
                finalScore = score,
                clearedGems = totalCleared,
                clearCounts = clearCounts
            )
        }

        val firstMatches = findMatches(rules)
        if (firstMatches.isEmpty()) {
            swap(r1, c1, r2, c2)
            steps += BoardAnimStep.Swap(first, second)
            return MoveResult(
                accepted = false,
                startBoard = startBoard,
                steps = steps,
                finalScore = score,
                clearedGems = 0,
                clearCounts = emptyList()
            )
        }

        var matches = firstMatches
        while (matches.isNotEmpty()) {
            val withSpecials = applySpecialCreation(matches)
            val expanded = expandSpecialEffects(withSpecials.toMutableSet())
            val clearCells = expanded.map { Cell(it.first, it.second) }
            totalCleared += clearCells.size
            clearCounts += clearCells.size
            score += clearCells.size * 10
            steps += BoardAnimStep.Clear(clearCells)
            clearMatches(expanded)
            val fallMoves = collapseColumnsWithMoves()
            if (fallMoves.isNotEmpty()) {
                steps += BoardAnimStep.Fall(fallMoves)
            }
            val spawnMoves = refillBoardWithMoves()
            if (spawnMoves.isNotEmpty()) {
                steps += BoardAnimStep.Spawn(spawnMoves)
            }
            matches = findMatches(rules)
        }
        return MoveResult(
            accepted = true,
            startBoard = startBoard,
            steps = steps,
            finalScore = score,
            clearedGems = totalCleared,
            clearCounts = clearCounts
        )
    }

    fun randomValidMove(rules: MatchRules = MatchRules()): Pair<Cell, Cell>? {
        val moves = mutableListOf<Pair<Cell, Cell>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (c + 1 < cols && wouldSwapMatch(r, c, r, c + 1, rules)) {
                    moves += (Cell(r, c) to Cell(r, c + 1))
                }
                if (r + 1 < rows && wouldSwapMatch(r, c, r + 1, c, rules)) {
                    moves += (Cell(r, c) to Cell(r + 1, c))
                }
            }
        }
        if (moves.isEmpty()) return null
        return moves[random.nextInt(moves.size)]
    }

    fun clearColorAnimated(color: Int, rules: MatchRules = MatchRules()): MoveResult {
        val startBoard = snapshotBoard()
        if (color !in 1..gemTypes) {
            return MoveResult(
                accepted = false,
                startBoard = startBoard,
                steps = emptyList(),
                finalScore = score,
                clearedGems = 0,
                clearCounts = emptyList()
            )
        }
        val initial = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (baseColor(board[r][c]) == color) initial += (r to c)
            }
        }
        if (initial.isEmpty()) {
            return MoveResult(
                accepted = false,
                startBoard = startBoard,
                steps = emptyList(),
                finalScore = score,
                clearedGems = 0,
                clearCounts = emptyList()
            )
        }

        val steps = mutableListOf<BoardAnimStep>()
        val clearCounts = mutableListOf<Int>()
        var totalCleared = 0

        var expanded = expandSpecialEffects(initial)
        var clearCells = expanded.map { Cell(it.first, it.second) }
        totalCleared += clearCells.size
        clearCounts += clearCells.size
        score += clearCells.size * 10
        steps += BoardAnimStep.Clear(clearCells)
        clearMatches(expanded)
        val fall = collapseColumnsWithMoves()
        if (fall.isNotEmpty()) steps += BoardAnimStep.Fall(fall)
        val spawn = refillBoardWithMoves()
        if (spawn.isNotEmpty()) steps += BoardAnimStep.Spawn(spawn)

        var matches = findMatches(rules)
        while (matches.isNotEmpty()) {
            val withSpecials = applySpecialCreation(matches)
            expanded = expandSpecialEffects(withSpecials.toMutableSet())
            clearCells = expanded.map { Cell(it.first, it.second) }
            totalCleared += clearCells.size
            clearCounts += clearCells.size
            score += clearCells.size * 10
            steps += BoardAnimStep.Clear(clearCells)
            clearMatches(expanded)
            val f = collapseColumnsWithMoves()
            if (f.isNotEmpty()) steps += BoardAnimStep.Fall(f)
            val s = refillBoardWithMoves()
            if (s.isNotEmpty()) steps += BoardAnimStep.Spawn(s)
            matches = findMatches(rules)
        }

        return MoveResult(
            accepted = true,
            startBoard = startBoard,
            steps = steps,
            finalScore = score,
            clearedGems = totalCleared,
            clearCounts = clearCounts
        )
    }

    fun resetUntilMoveExists(
        maxAttempts: Int = 20,
        rules: MatchRules = MatchRules()
    ) {
        repeat(maxAttempts) {
            reset()
            if (randomValidMove(rules) != null) return
        }
    }

    private fun fillBoardWithoutInitialMatches() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var value: Int
                do {
                    value = nextGem()
                } while (
                    (c >= 2 && board[r][c - 1] == value && board[r][c - 2] == value) ||
                    (r >= 2 && board[r - 1][c] == value && board[r - 2][c] == value)
                )
                board[r][c] = encodeNormal(value)
            }
        }
    }

    private fun findMatches(rules: MatchRules): Set<Pair<Int, Int>> {
        val matched = mutableSetOf<Pair<Int, Int>>()

        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                val value = baseColor(board[r][c])
                if (value == 0) {
                    c++
                    continue
                }
                var end = c + 1
                while (end < cols && baseColor(board[r][end]) == value) end++
                if (end - c >= rules.minLineMatch) {
                    for (x in c until end) matched.add(r to x)
                }
                c = end
            }
        }

        for (c in 0 until cols) {
            var r = 0
            while (r < rows) {
                val value = baseColor(board[r][c])
                if (value == 0) {
                    r++
                    continue
                }
                var end = r + 1
                while (end < rows && baseColor(board[end][c]) == value) end++
                if (end - r >= rules.minLineMatch) {
                    for (x in r until end) matched.add(x to c)
                }
                r = end
            }
        }

        if (rules.enableSquareMatch) {
            for (r in 0 until rows - 1) {
                for (c in 0 until cols - 1) {
                    val v = baseColor(board[r][c])
                    if (v == 0) continue
                    if (
                        baseColor(board[r][c + 1]) == v &&
                        baseColor(board[r + 1][c]) == v &&
                        baseColor(board[r + 1][c + 1]) == v
                    ) {
                        matched += (r to c)
                        matched += (r to (c + 1))
                        matched += ((r + 1) to c)
                        matched += ((r + 1) to (c + 1))
                    }
                }
            }
        }

        return matched
    }

    private fun clearMatches(matches: Set<Pair<Int, Int>>) {
        for ((r, c) in matches) {
            board[r][c] = 0
        }
    }

    private fun collapseColumnsWithMoves(): List<FallMove> {
        val moves = mutableListOf<FallMove>()
        for (c in 0 until cols) {
            var writeRow = rows - 1
            for (r in rows - 1 downTo 0) {
                if (board[r][c] != 0) {
                    val gem = board[r][c]
                    board[writeRow][c] = board[r][c]
                    if (writeRow != r) {
                        board[r][c] = 0
                        moves += FallMove(fromRow = r, toRow = writeRow, col = c, gem = gem)
                    }
                    writeRow--
                }
            }
        }
        return moves
    }

    private fun refillBoardWithMoves(): List<SpawnMove> {
        val moves = mutableListOf<SpawnMove>()
        for (c in 0 until cols) {
            var spawnOffset = 1
            for (r in rows - 1 downTo 0) {
                if (board[r][c] == 0) {
                    val gem = nextGem()
                    board[r][c] = encodeNormal(gem)
                    moves += SpawnMove(
                        startRow = -spawnOffset,
                        toRow = r,
                        col = c,
                        gem = gem
                    )
                    spawnOffset++
                }
            }
        }
        return moves
    }

    private fun nextGem(): Int = random.nextInt(1, gemTypes + 1)

    private fun swap(r1: Int, c1: Int, r2: Int, c2: Int) {
        val tmp = board[r1][c1]
        board[r1][c1] = board[r2][c2]
        board[r2][c2] = tmp
    }

    private fun isInside(r: Int, c: Int): Boolean =
        r in 0 until rows && c in 0 until cols

    private fun areAdjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean =
        abs(r1 - r2) + abs(c1 - c2) == 1

    private fun wouldSwapMatch(r1: Int, c1: Int, r2: Int, c2: Int, rules: MatchRules): Boolean {
        swap(r1, c1, r2, c2)
        val hasMatch = findMatches(rules).isNotEmpty() ||
            buildSpecialSwapClearSet(Cell(r1, c1), Cell(r2, c2)).isNotEmpty()
        swap(r1, c1, r2, c2)
        return hasMatch
    }

    private fun encodeNormal(color: Int): Int = color

    private fun baseColor(value: Int): Int {
        return when {
            value in 1..gemTypes -> value
            value in (SPECIAL_ROW_BASE + 1)..(SPECIAL_ROW_BASE + gemTypes) -> value - SPECIAL_ROW_BASE
            value in (SPECIAL_BOMB_BASE + 1)..(SPECIAL_BOMB_BASE + gemTypes) -> value - SPECIAL_BOMB_BASE
            value == SPECIAL_COLOR_BOMB -> 0
            else -> 0
        }
    }

    private fun isRowSpecial(value: Int): Boolean =
        value in (SPECIAL_ROW_BASE + 1)..(SPECIAL_ROW_BASE + gemTypes)

    private fun isBombSpecial(value: Int): Boolean =
        value in (SPECIAL_BOMB_BASE + 1)..(SPECIAL_BOMB_BASE + gemTypes)

    private fun isColorBomb(value: Int): Boolean = value == SPECIAL_COLOR_BOMB

    private fun buildSpecialSwapClearSet(first: Cell, second: Cell): Set<Pair<Int, Int>> {
        val firstGem = board[first.row][first.col]
        val secondGem = board[second.row][second.col]
        val clear = mutableSetOf<Pair<Int, Int>>()
        if (isColorBomb(firstGem)) {
            clear += (first.row to first.col)
            val targetColor = baseColor(secondGem)
            for (r in 0 until rows) for (c in 0 until cols) {
                if (baseColor(board[r][c]) == targetColor && targetColor != 0) clear += (r to c)
            }
        }
        if (isColorBomb(secondGem)) {
            clear += (second.row to second.col)
            val targetColor = baseColor(firstGem)
            for (r in 0 until rows) for (c in 0 until cols) {
                if (baseColor(board[r][c]) == targetColor && targetColor != 0) clear += (r to c)
            }
        }
        return clear
    }

    private fun applySpecialCreation(matches: Set<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val clearSet = matches.toMutableSet()
        val groups = findLineGroups()
        for (group in groups) {
            val filtered = group.filter { matches.contains(it) }
            if (filtered.size >= 5) {
                val anchor = filtered.first()
                val color = baseColor(board[anchor.first][anchor.second])
                if (color != 0) {
                    board[anchor.first][anchor.second] = SPECIAL_COLOR_BOMB
                    clearSet.remove(anchor)
                }
            } else if (filtered.size == 4) {
                val anchor = filtered.first()
                val color = baseColor(board[anchor.first][anchor.second])
                if (color != 0) {
                    board[anchor.first][anchor.second] = SPECIAL_ROW_BASE + color
                    clearSet.remove(anchor)
                }
            }
        }
        return clearSet
    }

    private fun findLineGroups(): List<List<Pair<Int, Int>>> {
        val groups = mutableListOf<List<Pair<Int, Int>>>()
        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                val color = baseColor(board[r][c])
                if (color == 0) {
                    c++
                    continue
                }
                var end = c + 1
                while (end < cols && baseColor(board[r][end]) == color) end++
                if (end - c >= 3) {
                    groups += (c until end).map { x -> r to x }
                }
                c = end
            }
        }
        for (c in 0 until cols) {
            var r = 0
            while (r < rows) {
                val color = baseColor(board[r][c])
                if (color == 0) {
                    r++
                    continue
                }
                var end = r + 1
                while (end < rows && baseColor(board[end][c]) == color) end++
                if (end - r >= 3) {
                    groups += (r until end).map { x -> x to c }
                }
                r = end
            }
        }
        return groups
    }

    private fun expandSpecialEffects(initial: MutableSet<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val queue = ArrayDeque(initial.toList())
        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            val gem = board[r][c]
            if (isRowSpecial(gem)) {
                for (cc in 0 until cols) {
                    val cell = r to cc
                    if (initial.add(cell)) queue.add(cell)
                }
            } else if (isBombSpecial(gem)) {
                for (rr in (r - 1)..(r + 1)) {
                    for (cc in (c - 1)..(c + 1)) {
                        if (rr in 0 until rows && cc in 0 until cols) {
                            val cell = rr to cc
                            if (initial.add(cell)) queue.add(cell)
                        }
                    }
                }
            } else if (isColorBomb(gem)) {
                val color = detectColorBombTargetColor(initial)
                if (color != 0) {
                    for (rr in 0 until rows) for (cc in 0 until cols) {
                        if (baseColor(board[rr][cc]) == color) {
                            val cell = rr to cc
                            if (initial.add(cell)) queue.add(cell)
                        }
                    }
                }
            }
        }
        return initial
    }

    private fun detectColorBombTargetColor(clears: Set<Pair<Int, Int>>): Int {
        for ((r, c) in clears) {
            val color = baseColor(board[r][c])
            if (color != 0 && !isColorBomb(board[r][c])) return color
        }
        return random.nextInt(1, gemTypes + 1)
    }
}
