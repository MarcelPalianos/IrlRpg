package com.example.match3

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import java.util.ArrayDeque
import kotlin.math.min

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    data class BattleSummary(
        val reachedEnemyLevel: Int,
        val score: Int,
        val playerLevel: Int
    )

    interface BattleListener {
        fun onBattleEnded(summary: BattleSummary)
    }

    enum class Turn { PLAYER, AI }
    private enum class Side { PLAYER, AI }
    private enum class PetType { NONE, FOX, TURTLE, DRAGON }
    private data class FloatingText(
        val text: String,
        val x: Float,
        val yStart: Float,
        val color: Int,
        var progress: Float = 0f
    )
    private data class DamageTick(val amount: Int, val comboIndex: Int)
    private data class ClearBurst(
        val cells: List<Cell>,
        var progress: Float = 0f
    )
    private data class RowSweep(
        val row: Int,
        var progress: Float = 0f
    )
    private data class ColorParticle(
        val x: Float,
        val y: Float,
        val dx: Float,
        val dy: Float,
        val radius: Float,
        val color: Int,
        var progress: Float = 0f
    )
    private data class FlashPulse(
        val color: Int,
        var progress: Float = 0f
    )

    private val engine = Match3Engine()
    private val rows: Int
    private val cols: Int

    private var selected: Pair<Int, Int>? = null
    private var displayBoard = emptyArray<IntArray>()
    private var boardTop = 0f
    private var cellSize = 0f
    private var activeStep: BoardAnimStep? = null
    private var stepProgress = 0f
    private val stepQueue = ArrayDeque<BoardAnimStep>()
    private var isAnimating = false
    private var playerMaxHealth = 100
    private var playerHealth = 100
    private var displayedPlayerHealth = 100f
    private var playerDamageBonus = 0
    private var enemyLevel = 1
    private var aiMaxHealth = 100
    private var aiHealth = 100
    private var displayedAiHealth = 100f
    private var turn = Turn.PLAYER
    private var battleOver = false
    private var pendingLevelComplete = false
    private var statusText = "Your turn"
    private var playerLevel = 1
    private var playerXp = 0
    private var xpToNextLevel = 120
    private var unspentStatPoints = 0
    private var unspentTalentPoints = 0
    private var hpStatPoints = 0
    private val offenseStatPoints = IntArray(6)
    private val defenseStatPoints = IntArray(6)
    private var talentAttunement = false
    private var talentSquareMatch = false
    private var talentPower4 = false
    private var talentGuardianSkin = false
    private var talentHeal = false
    private var talentColorWipe = false
    private var talentSecondWind = false
    private var talentFocusStrike = false
    private var talentBarrier = false
    private var talentReroll = false
    private var petType = PetType.NONE
    private var healCharges = 0
    private var colorWipeCharges = 0
    private var barrierCharges = 0
    private var rerollCharges = 0
    private var focusStrikeReady = false
    private var barrierActive = false
    private val floatingTexts = mutableListOf<FloatingText>()
    private val clearBursts = mutableListOf<ClearBurst>()
    private val rowSweeps = mutableListOf<RowSweep>()
    private val particles = mutableListOf<ColorParticle>()
    private val flashPulses = mutableListOf<FlashPulse>()
    private val pendingClearDamages = ArrayDeque<DamageTick>()
    private var damageTargetSide: Side = Side.AI
    private var shakeOffsetX = 0f
    private var shakeOffsetY = 0f
    private val fxEnabled = true
    private val heavyFxEnabled = false
    private var battleListener: BattleListener? = null
    private var battleResultDispatched = false
    private var isPvpMode = false

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 54f
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 44f
    }
    private val healthTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8C0FF")
        textSize = 34f
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD166")
        textSize = 32f
    }
    private val floatingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
    }
    private val healthBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C3A")
    }
    private val playerHealthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4ADE80")
    }
    private val aiHealthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F87171")
    }
    private val boardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E2C")
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#323248")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val gemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val specialOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    init {
        val size = engine.size()
        rows = size.first
        cols = size.second
        displayBoard = engine.snapshotBoard()
        setBackgroundColor(Color.parseColor("#121220"))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(shakeOffsetX, shakeOffsetY)
        drawHeader(canvas)
        drawBoard(canvas)
        drawFlashPulses(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (battleOver) return true
        if (isAnimating || turn != Turn.PLAYER || cellSize <= 0f) return true

        val row = ((event.y - boardTop) / cellSize).toInt()
        val col = (event.x / cellSize).toInt()
        if (row !in 0 until rows || col !in 0 until cols) return true

        val current = row to col
        val prev = selected
        if (prev == null) {
            selected = current
            invalidate()
            return true
        }

        if (prev == current) {
            selected = null
            invalidate()
            return true
        }

        selected = null
        val result = engine.trySwapAnimated(
            prev.first,
            prev.second,
            current.first,
            current.second,
            playerRules()
        )
        displayBoard = result.startBoard.map { it.clone() }.toTypedArray()
        if (result.steps.isEmpty()) {
            statusText = "No match. Try another swap."
            invalidate()
            return true
        }
        pendingClearDamages.clear()
        result.clearCounts.forEachIndexed { index, count ->
            pendingClearDamages += DamageTick(
                amount = damageFromClearCount(count, comboIndex = index),
                comboIndex = index
            )
        }
        damageTargetSide = Side.AI
        statusText = "You attack!"
        stepQueue.clear()
        stepQueue.addAll(result.steps)
        playNextStep(
            onStepFinished = { onBattleStepFinished(it) },
            onSequenceFinished = {
                if (battleOver) return@playNextStep
                if (pendingLevelComplete) {
                    startNextLevel()
                    return@playNextStep
                }
                turn = Turn.AI
                statusText = "AI is thinking..."
                postDelayed({ runAiTurn() }, 700L)
            }
        )
        return true
    }

    fun resetGame() {
        if (isAnimating) return
        isPvpMode = false
        loadPersistentProgress()
        selected = null
        playerMaxHealth = 100 + hpStatPoints * 20
        playerHealth = playerMaxHealth
        displayedPlayerHealth = playerHealth.toFloat()
        playerDamageBonus = 0
        enemyLevel = 1
        aiMaxHealth = aiHealthForLevel(enemyLevel)
        aiHealth = aiMaxHealth
        displayedAiHealth = aiHealth.toFloat()
        turn = Turn.PLAYER
        battleOver = false
        battleResultDispatched = false
        pendingLevelComplete = false
        statusText = "Your turn"
        healCharges = if (talentHeal) 1 else 0
        colorWipeCharges = if (talentColorWipe) 1 else 0
        barrierCharges = if (talentBarrier) 1 else 0
        rerollCharges = if (talentReroll) 1 else 0
        focusStrikeReady = talentFocusStrike
        barrierActive = false
        if (talentSecondWind) {
            playerHealth = (playerHealth + (playerMaxHealth * 0.1f).toInt()).coerceAtMost(playerMaxHealth)
            displayedPlayerHealth = playerHealth.toFloat()
        }
        floatingTexts.clear()
        clearBursts.clear()
        rowSweeps.clear()
        pendingClearDamages.clear()
        engine.resetUntilMoveExists(rules = playerRules())
        displayBoard = engine.snapshotBoard()
        invalidate()
    }

    fun setBattleListener(listener: BattleListener?) {
        battleListener = listener
    }

    fun useHealSkill(): Boolean {
        if (battleOver || isAnimating || turn != Turn.PLAYER) return false
        if (!talentHeal || healCharges <= 0) return false
        healCharges--
        val healAmount = 20 + hpStatPoints * 2 + if (petType == PetType.FOX) 8 else 0
        playerHealth = (playerHealth + healAmount).coerceAtMost(playerMaxHealth)
        animatePlayerHealthTo(playerHealth)
        statusText = "Heal used (+$healAmount)"
        turn = Turn.AI
        invalidate()
        postDelayed({ runAiTurn() }, 450L)
        return true
    }

    fun useColorWipeSkill(color: Int): Boolean {
        if (battleOver || isAnimating || turn != Turn.PLAYER) return false
        if (!talentColorWipe || colorWipeCharges <= 0) return false
        if (color !in 1..5) return false
        colorWipeCharges--
        val result = engine.clearColorAnimated(color, playerRules())
        if (!result.accepted || result.steps.isEmpty()) {
            statusText = "No gems for that color."
            invalidate()
            return false
        }
        pendingClearDamages.clear()
        result.clearCounts.forEachIndexed { index, count ->
            pendingClearDamages += DamageTick(
                amount = damageFromClearCount(count, comboIndex = index),
                comboIndex = index
            )
        }
        damageTargetSide = Side.AI
        displayBoard = result.startBoard.map { it.clone() }.toTypedArray()
        statusText = "Color Wipe activated!"
        stepQueue.clear()
        stepQueue.addAll(result.steps)
        playNextStep(
            onStepFinished = { onBattleStepFinished(it) },
            onSequenceFinished = {
                if (battleOver) return@playNextStep
                if (pendingLevelComplete) {
                    startNextLevel()
                    return@playNextStep
                }
                turn = Turn.AI
                statusText = "AI is thinking..."
                postDelayed({ runAiTurn() }, 700L)
            }
        )
        return true
    }

    fun useBarrierSkill(): Boolean {
        if (battleOver || isAnimating || turn != Turn.PLAYER) return false
        if (!talentBarrier || barrierCharges <= 0) return false
        barrierCharges--
        barrierActive = true
        statusText = "Barrier raised."
        turn = Turn.AI
        invalidate()
        postDelayed({ runAiTurn() }, 450L)
        return true
    }

    fun useRerollSkill(): Boolean {
        if (battleOver || isAnimating || turn != Turn.PLAYER) return false
        if (!talentReroll || rerollCharges <= 0) return false
        rerollCharges--
        engine.resetUntilMoveExists(rules = playerRules())
        displayBoard = engine.snapshotBoard()
        statusText = "Board rerolled."
        turn = Turn.AI
        invalidate()
        postDelayed({ runAiTurn() }, 500L)
        return true
    }

    private fun drawHeader(canvas: Canvas) {
        canvas.drawText("Match-3", 32f, 72f, titlePaint)
        canvas.drawText("Score: ${engine.score}", 32f, 126f, scorePaint)
        canvas.drawText("Player HP: $playerHealth", 32f, 168f, healthTextPaint)
        canvas.drawText("AI HP: $aiHealth", 32f, 208f, healthTextPaint)
        canvas.drawText("Enemy Lv $enemyLevel", 250f, 248f, levelPaint)
        canvas.drawText(
            "Lv $playerLevel  XP $playerXp/$xpToNextLevel  Stat:$unspentStatPoints  Talent:$unspentTalentPoints",
            32f,
            248f,
            levelPaint
        )
        canvas.drawText(statusText, 32f, 286f, statusPaint)
        val spells = buildString {
            append("Talents: ")
            if (!talentAttunement && !talentSquareMatch && !talentPower4 && !talentGuardianSkin && !talentHeal && !talentColorWipe && !talentSecondWind && !talentFocusStrike && !talentBarrier && !talentReroll) append("None")
            if (talentAttunement) append("Attune ")
            if (talentSquareMatch) append("Square ")
            if (talentPower4) append("Power4 ")
            if (talentGuardianSkin) append("Guardian ")
            if (talentHeal) append("Heal ")
            if (talentColorWipe) append("Wipe ")
            if (talentSecondWind) append("SecondWind ")
            if (talentFocusStrike) append("Focus ")
            if (talentBarrier) append("Barrier ")
            if (talentReroll) append("Reroll ")
        }
        canvas.drawText(spells, 32f, 322f, healthTextPaint)
        canvas.drawText("Pet: ${petType.name}", 420f, 322f, healthTextPaint)
        drawHealthBar(canvas, 250f, 146f, displayedPlayerHealth / playerMaxHealth, playerHealthPaint)
        drawHealthBar(canvas, 250f, 186f, displayedAiHealth / aiMaxHealth, aiHealthPaint)
    }

    private fun drawBoard(canvas: Canvas) {
        val topPadding = 350f
        cellSize = min(width.toFloat() / cols, (height - topPadding - 24f) / rows)
        boardTop = topPadding
        val boardWidth = cellSize * cols
        val boardHeight = cellSize * rows

        canvas.drawRoundRect(
            RectF(0f, boardTop, boardWidth, boardTop + boardHeight),
            16f,
            16f,
            boardBgPaint
        )

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = c * cellSize
                val top = boardTop + r * cellSize
                val right = left + cellSize
                val bottom = top + cellSize
                val inset = 10f
                val value = displayBoard[r][c]
                if (value != 0 && !shouldSkipBaseGem(r, c)) {
                    drawGem(canvas, value, left, top, right, bottom, inset)
                }

                canvas.drawRect(left, top, right, bottom, gridPaint)

                if (selected == (r to c)) {
                    canvas.drawRect(left + 3f, top + 3f, right - 3f, bottom - 3f, selectedPaint)
                }
            }
        }

        drawActiveAnimation(canvas, inset = 10f)
        drawRowSweeps(canvas)
        drawClearBursts(canvas)
        drawParticles(canvas)
        drawFloatingTexts(canvas)
    }

    private fun gemColor(value: Int): Int {
        val baseColor = when {
            value in 1..5 -> value
            value in 101..105 -> value - 100
            value in 201..205 -> value - 200
            value == 300 -> 0
            else -> 0
        }
        return when (baseColor) {
            1 -> Color.parseColor("#FF5C8A")
            2 -> Color.parseColor("#FFD166")
            3 -> Color.parseColor("#06D6A0")
            4 -> Color.parseColor("#4CC9F0")
            5 -> Color.parseColor("#B388FF")
            else -> Color.WHITE
        }
    }

    private fun drawGem(
        canvas: Canvas,
        value: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        inset: Float,
        alpha: Int = 255,
        scale: Float = 1f
    ) {
        gemPaint.color = gemColor(value)
        gemPaint.alpha = alpha
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val halfW = (right - left) * scale / 2f - inset
        val halfH = (bottom - top) * scale / 2f - inset
        val rect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        canvas.drawRoundRect(rect, 18f, 18f, gemPaint)
        val marker = specialMarker(value)
        if (marker.isNotEmpty()) {
            floatingTextPaint.color = Color.parseColor("#1A1A1A")
            floatingTextPaint.alpha = alpha
            floatingTextPaint.textSize = 32f * scale
            canvas.drawText(marker, cx - 10f * scale, cy + 10f * scale, floatingTextPaint)
            val pulse = ((System.currentTimeMillis() % 1200L).toFloat() / 1200f)
            val pulseAlpha = (140 + 90 * kotlin.math.sin((pulse * 6.28318f).toDouble())).toInt().coerceIn(40, 230)
            specialOutlinePaint.color = Color.WHITE
            specialOutlinePaint.alpha = pulseAlpha
            specialOutlinePaint.strokeWidth = 3f + 2f * scale
            canvas.drawRoundRect(rect, 18f, 18f, specialOutlinePaint)
        }
        gemPaint.alpha = 255
        floatingTextPaint.alpha = 255
        specialOutlinePaint.alpha = 255
    }

    private fun drawActiveAnimation(canvas: Canvas, inset: Float) {
        when (val step = activeStep) {
            is BoardAnimStep.Swap -> drawSwapStep(canvas, step, stepProgress, inset)
            is BoardAnimStep.Clear -> drawClearStep(canvas, step, stepProgress, inset)
            is BoardAnimStep.Fall -> drawFallStep(canvas, step, stepProgress, inset)
            is BoardAnimStep.Spawn -> drawSpawnStep(canvas, step, stepProgress, inset)
            null -> Unit
        }
    }

    private fun drawSwapStep(canvas: Canvas, step: BoardAnimStep.Swap, progress: Float, inset: Float) {
        val first = step.first
        val second = step.second
        val aGem = displayBoard[first.row][first.col]
        val bGem = displayBoard[second.row][second.col]
        if (aGem == 0 && bGem == 0) return

        val aLeft = first.col * cellSize
        val aTop = boardTop + first.row * cellSize
        val bLeft = second.col * cellSize
        val bTop = boardTop + second.row * cellSize

        val curALeft = lerp(aLeft, bLeft, progress)
        val curATop = lerp(aTop, bTop, progress)
        val curBLeft = lerp(bLeft, aLeft, progress)
        val curBTop = lerp(bTop, aTop, progress)

        drawGem(
            canvas = canvas,
            value = aGem,
            left = curALeft,
            top = curATop,
            right = curALeft + cellSize,
            bottom = curATop + cellSize,
            inset = inset
        )
        drawGem(
            canvas = canvas,
            value = bGem,
            left = curBLeft,
            top = curBTop,
            right = curBLeft + cellSize,
            bottom = curBTop + cellSize,
            inset = inset
        )
    }

    private fun drawClearStep(canvas: Canvas, step: BoardAnimStep.Clear, progress: Float, inset: Float) {
        val alpha = (255f * (1f - progress)).toInt().coerceIn(0, 255)
        val scale = 1f - 0.25f * progress
        for (cell in step.cells) {
            val gem = displayBoard[cell.row][cell.col]
            if (gem == 0) continue
            val left = cell.col * cellSize
            val top = boardTop + cell.row * cellSize
            drawGem(
                canvas = canvas,
                value = gem,
                left = left,
                top = top,
                right = left + cellSize,
                bottom = top + cellSize,
                inset = inset,
                alpha = alpha,
                scale = scale
            )
        }
    }

    private fun drawFallStep(canvas: Canvas, step: BoardAnimStep.Fall, progress: Float, inset: Float) {
        for (move in step.moves) {
            val startTop = boardTop + move.fromRow * cellSize
            val endTop = boardTop + move.toRow * cellSize
            val curTop = lerp(startTop, endTop, progress)
            val left = move.col * cellSize
            drawGem(
                canvas = canvas,
                value = move.gem,
                left = left,
                top = curTop,
                right = left + cellSize,
                bottom = curTop + cellSize,
                inset = inset
            )
        }
    }

    private fun drawSpawnStep(canvas: Canvas, step: BoardAnimStep.Spawn, progress: Float, inset: Float) {
        for (move in step.moves) {
            val startTop = boardTop + move.startRow * cellSize
            val endTop = boardTop + move.toRow * cellSize
            val curTop = lerp(startTop, endTop, progress)
            val left = move.col * cellSize
            drawGem(
                canvas = canvas,
                value = move.gem,
                left = left,
                top = curTop,
                right = left + cellSize,
                bottom = curTop + cellSize,
                inset = inset
            )
        }
    }

    private fun playNextStep(
        onStepFinished: ((BoardAnimStep) -> Unit)? = null,
        onSequenceFinished: (() -> Unit)? = null
    ) {
        val next = stepQueue.pollFirst()
        if (next == null) {
            activeStep = null
            isAnimating = false
            displayBoard = engine.snapshotBoard()
            invalidate()
            onSequenceFinished?.invoke()
            return
        }

        activeStep = next
        isAnimating = true
        stepProgress = 0f
        val duration = when (next) {
            is BoardAnimStep.Swap -> 230L
            is BoardAnimStep.Clear -> 240L
            is BoardAnimStep.Fall -> 280L
            is BoardAnimStep.Spawn -> 230L
        }
        val interpolator = when (next) {
            is BoardAnimStep.Clear -> AccelerateDecelerateInterpolator()
            else -> DecelerateInterpolator()
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener {
                stepProgress = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                onStepFinished?.invoke(next)
                applyStepToDisplayBoard(next)
                activeStep = null
                stepProgress = 0f
                invalidate()
                postDelayed({ playNextStep(onStepFinished, onSequenceFinished) }, 80L)
            }
            start()
        }
    }

    private fun applyStepToDisplayBoard(step: BoardAnimStep) {
        when (step) {
            is BoardAnimStep.Swap -> {
                val a = step.first
                val b = step.second
                val temp = displayBoard[a.row][a.col]
                displayBoard[a.row][a.col] = displayBoard[b.row][b.col]
                displayBoard[b.row][b.col] = temp
            }

            is BoardAnimStep.Clear -> {
                for (cell in step.cells) {
                    displayBoard[cell.row][cell.col] = 0
                }
            }

            is BoardAnimStep.Fall -> {
                for (move in step.moves) {
                    displayBoard[move.fromRow][move.col] = 0
                }
                for (move in step.moves) {
                    displayBoard[move.toRow][move.col] = move.gem
                }
            }

            is BoardAnimStep.Spawn -> {
                for (move in step.moves) {
                    displayBoard[move.toRow][move.col] = move.gem
                }
            }
        }
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    private fun shouldSkipBaseGem(row: Int, col: Int): Boolean {
        return when (val step = activeStep) {
            is BoardAnimStep.Swap -> {
                (step.first.row == row && step.first.col == col) ||
                    (step.second.row == row && step.second.col == col)
            }

            is BoardAnimStep.Clear -> step.cells.any { it.row == row && it.col == col }

            is BoardAnimStep.Fall -> step.moves.any {
                (it.fromRow == row && it.col == col) || (it.toRow == row && it.col == col)
            }

            is BoardAnimStep.Spawn -> step.moves.any { it.toRow == row && it.col == col }
            null -> false
        }
    }

    private fun runAiTurn() {
        if (battleOver || isAnimating) return
        val move = engine.randomValidMove()
        if (move == null) {
            engine.resetUntilMoveExists(rules = playerRules())
            displayBoard = engine.snapshotBoard()
            turn = Turn.PLAYER
            statusText = "Board reshuffled. Your turn."
            invalidate()
            return
        }

        val (a, b) = move
        val result = engine.trySwapAnimated(a.row, a.col, b.row, b.col)
        displayBoard = result.startBoard.map { it.clone() }.toTypedArray()
        pendingClearDamages.clear()
        result.clearCounts.forEachIndexed { index, count ->
            pendingClearDamages += DamageTick(
                amount = damageFromClearCount(count, comboIndex = index),
                comboIndex = index
            )
        }
        damageTargetSide = Side.PLAYER
        statusText = "AI attacks!"

        stepQueue.clear()
        stepQueue.addAll(result.steps)
        playNextStep(
            onStepFinished = { onBattleStepFinished(it) },
            onSequenceFinished = {
                if (battleOver) return@playNextStep
                if (pendingLevelComplete) {
                    startNextLevel()
                    return@playNextStep
                }
                turn = Turn.PLAYER
                statusText = "Your turn"
                invalidate()
            }
        )
    }

    private fun damageFromClearCount(clearCount: Int, comboIndex: Int): Int {
        var damage = clearCount * (3 + playerDamageBonus)
        if (talentAttunement) damage += 2
        if (talentPower4 && clearCount >= 4) damage += 8
        val comboMultiplier = 1f + comboIndex * 0.25f
        return (damage * comboMultiplier).toInt().coerceAtLeast(6)
    }

    private fun drawHealthBar(
        canvas: Canvas,
        left: Float,
        top: Float,
        ratio: Float,
        fillPaint: Paint
    ) {
        val width = 220f
        val height = 20f
        canvas.drawRoundRect(
            RectF(left, top, left + width, top + height),
            10f,
            10f,
            healthBarBgPaint
        )
        canvas.drawRoundRect(
            RectF(left, top, left + width * ratio.coerceIn(0f, 1f), top + height),
            10f,
            10f,
            fillPaint
        )
    }

    private fun onBattleStepFinished(step: BoardAnimStep) {
        if (step !is BoardAnimStep.Clear) return
        triggerClearBurst(step.cells)
        triggerRowSweepIfSpecial(step.cells)
        triggerParticles(step.cells)
        triggerColorBombFlash(step.cells)
        val tick = pendingClearDamages.pollFirst() ?: return
        val clearColor = dominantClearColor(step.cells)
        var damage = scaleDamageByElements(tick.amount, clearColor)
        val comboText = if (tick.comboIndex > 0) " x${tick.comboIndex + 1}" else ""
        when (damageTargetSide) {
            Side.AI -> {
                if (focusStrikeReady && tick.comboIndex == 0) {
                    damage += 6
                    focusStrikeReady = false
                }
                aiHealth = (aiHealth - damage).coerceAtLeast(0)
                animateAiHealthTo(aiHealth)
                addFloatingDamage("-$damage$comboText", 220f, boardTop - 16f, Color.parseColor("#FF6B6B"))
                statusText = "You deal $damage$comboText"
                triggerShake(intensity = 9f)
            }

            Side.PLAYER -> {
                if (barrierActive) {
                    damage = (damage * 0.5f).toInt().coerceAtLeast(1)
                    barrierActive = false
                }
                playerHealth = (playerHealth - damage).coerceAtLeast(0)
                animatePlayerHealthTo(playerHealth)
                addFloatingDamage("-$damage$comboText", 32f, boardTop - 16f, Color.parseColor("#FF8FA3"))
                statusText = "AI deals $damage$comboText"
                triggerShake(intensity = 12f)
            }
        }
        checkBattleEnd()
    }

    private fun playerRules(): MatchRules = MatchRules(
        minLineMatch = 3,
        enableSquareMatch = talentSquareMatch
    )

    private fun addPlayerXp(amount: Int) {
        playerXp += amount
        var leveled = false
        while (playerXp >= xpToNextLevel) {
            playerXp -= xpToNextLevel
            playerLevel += 1
            xpToNextLevel = (xpToNextLevel * 1.35f).toInt()
            leveled = true
            unspentStatPoints += 3
            unspentTalentPoints += 1
            statusText = "Level up! +3 stat points, +1 talent point."
        }
        if (leveled) {
            savePersistentProgress()
            invalidate()
        }
    }

    private fun animatePlayerHealthTo(target: Int) {
        ValueAnimator.ofFloat(displayedPlayerHealth, target.toFloat()).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                displayedPlayerHealth = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateAiHealthTo(target: Int) {
        ValueAnimator.ofFloat(displayedAiHealth, target.toFloat()).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                displayedAiHealth = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun addFloatingDamage(text: String, x: Float, y: Float, color: Int) {
        val floating = FloatingText(text = text, x = x, yStart = y, color = color)
        floatingTexts += floating
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                floating.progress = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                floatingTexts.remove(floating)
                invalidate()
            }
            start()
        }
    }

    private fun drawFloatingTexts(canvas: Canvas) {
        for (floating in floatingTexts) {
            val alpha = (255f * (1f - floating.progress)).toInt().coerceIn(0, 255)
            floatingTextPaint.color = floating.color
            floatingTextPaint.alpha = alpha
            val y = floating.yStart - (40f * floating.progress)
            canvas.drawText(floating.text, floating.x, y, floatingTextPaint)
        }
        floatingTextPaint.alpha = 255
    }

    private fun triggerClearBurst(cells: List<Cell>) {
        if (!fxEnabled) return
        if (cells.isEmpty()) return
        val burst = ClearBurst(cells = cells.take(if (heavyFxEnabled) 18 else 8))
        clearBursts += burst
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (heavyFxEnabled) 260L else 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                burst.progress = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                clearBursts.remove(burst)
                invalidate()
            }
            start()
        }
    }

    private fun triggerRowSweepIfSpecial(cells: List<Cell>) {
        if (!fxEnabled) return
        val triggeredRows = mutableSetOf<Int>()
        for (cell in cells) {
            val gem = displayBoard[cell.row][cell.col]
            if (gem in 101..105) triggeredRows += cell.row
        }
        for (row in triggeredRows) {
            val sweep = RowSweep(row = row)
            rowSweeps += sweep
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (heavyFxEnabled) 300L else 220L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    sweep.progress = it.animatedValue as Float
                    invalidate()
                }
                doOnEnd {
                    rowSweeps.remove(sweep)
                    invalidate()
                }
                start()
            }
        }
    }

    private fun drawClearBursts(canvas: Canvas) {
        for (burst in clearBursts) {
            val alpha = (190f * (1f - burst.progress)).toInt().coerceIn(0, 255)
            val radius = cellSize * (0.18f + burst.progress * 0.45f)
            for (cell in burst.cells) {
                val cx = cell.col * cellSize + cellSize / 2f
                val cy = boardTop + cell.row * cellSize + cellSize / 2f
                fxPaint.color = Color.WHITE
                fxPaint.alpha = alpha
                canvas.drawCircle(cx, cy, radius, fxPaint)
            }
        }
        fxPaint.alpha = 255
    }

    private fun drawRowSweeps(canvas: Canvas) {
        for (sweep in rowSweeps) {
            val top = boardTop + sweep.row * cellSize
            val bottom = top + cellSize
            val right = cellSize * cols * sweep.progress
            fxPaint.color = Color.parseColor("#FFFFFF")
            fxPaint.alpha = (120f * (1f - sweep.progress)).toInt().coerceIn(0, 160)
            canvas.drawRoundRect(RectF(0f, top + 6f, right, bottom - 6f), 10f, 10f, fxPaint)
        }
        fxPaint.alpha = 255
    }

    private fun triggerParticles(cells: List<Cell>) {
        if (!fxEnabled) return
        val capped = cells.take(if (heavyFxEnabled) 12 else 5)
        for (cell in capped) {
            val gemValue = displayBoard[cell.row][cell.col]
            val color = gemColor(gemValue)
            val cx = cell.col * cellSize + cellSize / 2f
            val cy = boardTop + cell.row * cellSize + cellSize / 2f
            repeat(if (heavyFxEnabled) 3 else 1) { idx ->
                val angle = (idx * 2.094f) + ((System.nanoTime() % 1000L).toFloat() / 1000f)
                val speed = 26f + idx * 10f
                val particle = ColorParticle(
                    x = cx,
                    y = cy,
                    dx = kotlin.math.cos(angle) * speed,
                    dy = kotlin.math.sin(angle) * speed - 8f,
                    radius = 3f + idx,
                    color = color
                )
                particles += particle
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = if (heavyFxEnabled) 420L else 240L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        particle.progress = it.animatedValue as Float
                        invalidate()
                    }
                    doOnEnd {
                        particles.remove(particle)
                        invalidate()
                    }
                    start()
                }
            }
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            val alpha = (220f * (1f - p.progress)).toInt().coerceIn(0, 255)
            val x = p.x + p.dx * p.progress
            val y = p.y + p.dy * p.progress + 14f * p.progress * p.progress
            fxPaint.color = p.color
            fxPaint.alpha = alpha
            canvas.drawCircle(x, y, p.radius * (1f - p.progress * 0.2f), fxPaint)
        }
        fxPaint.alpha = 255
    }

    private fun triggerColorBombFlash(cells: List<Cell>) {
        if (!fxEnabled || !heavyFxEnabled) return
        var hasColorBomb = false
        for (cell in cells) {
            if (displayBoard[cell.row][cell.col] == 300) {
                hasColorBomb = true
                break
            }
        }
        if (!hasColorBomb) return

        val pulse = FlashPulse(color = Color.WHITE)
        flashPulses += pulse
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulse.progress = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                flashPulses.remove(pulse)
                invalidate()
            }
            start()
        }
    }

    private fun drawFlashPulses(canvas: Canvas) {
        for (pulse in flashPulses) {
            val alpha = (120f * (1f - pulse.progress)).toInt().coerceIn(0, 160)
            fxPaint.color = pulse.color
            fxPaint.alpha = alpha
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fxPaint)
        }
        fxPaint.alpha = 255
    }

    private fun triggerShake(intensity: Float) {
        if (!fxEnabled || !heavyFxEnabled) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 170L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val p = it.animatedValue as Float
                val damp = 1f - p
                val wave = kotlin.math.sin(p * 28f).toFloat()
                shakeOffsetX = wave * intensity * damp
                shakeOffsetY = wave * intensity * 0.35f * damp
                invalidate()
            }
            doOnEnd {
                shakeOffsetX = 0f
                shakeOffsetY = 0f
                invalidate()
            }
            start()
        }
    }

    private fun specialMarker(value: Int): String {
        return when {
            value in 101..105 -> "R"
            value in 201..205 -> "B"
            value == 300 -> "*"
            else -> ""
        }
    }

    private fun checkBattleEnd() {
        when {
            playerHealth <= 0 -> {
                battleOver = true
                pendingLevelComplete = false
                turn = Turn.PLAYER
                statusText = if (isPvpMode) "You lost the PvP battle!" else "AI wins!"
                if (!battleResultDispatched) {
                    battleResultDispatched = true
                    battleListener?.onBattleEnded(
                        BattleSummary(
                            reachedEnemyLevel = enemyLevel,
                            score = engine.score,
                            playerLevel = playerLevel
                        )
                    )
                }
            }

            aiHealth <= 0 -> {
                if (isPvpMode) {
                    battleOver = true
                    pendingLevelComplete = false
                    turn = Turn.PLAYER
                    statusText = "You won the PvP battle!"
                    if (!battleResultDispatched) {
                        battleResultDispatched = true
                        battleListener?.onBattleEnded(
                            BattleSummary(
                                reachedEnemyLevel = enemyLevel,
                                score = engine.score,
                                playerLevel = playerLevel
                            )
                        )
                    }
                } else {
                    pendingLevelComplete = true
                    turn = Turn.PLAYER
                    statusText = "Level cleared!"
                }
            }
        }
    }

    private fun startNextLevel() {
        pendingLevelComplete = false
        val xpReward = 90 + enemyLevel * 35
        addPlayerXp(xpReward)
        enemyLevel += 1
        aiMaxHealth = aiHealthForLevel(enemyLevel)
        aiHealth = aiMaxHealth
        displayedAiHealth = aiHealth.toFloat()
        playerHealth = playerMaxHealth
        if (talentSecondWind) {
            playerHealth = (playerHealth + (playerMaxHealth * 0.1f).toInt()).coerceAtMost(playerMaxHealth)
        }
        displayedPlayerHealth = playerHealth.toFloat()
        engine.resetUntilMoveExists(rules = playerRules())
        displayBoard = engine.snapshotBoard()
        turn = Turn.PLAYER
        statusText = "Level $enemyLevel starts! +$xpReward XP, full heal."
        invalidate()
    }

    private fun aiHealthForLevel(level: Int): Int = 90 + (level - 1) * 30

    private fun loadPersistentProgress() {
        val progress = ProgressStore.load(context)
        playerLevel = progress.playerLevel
        playerXp = progress.playerXp
        xpToNextLevel = progress.xpToNextLevel
        unspentStatPoints = progress.unspentPoints
        unspentTalentPoints = progress.unspentTalentPoints
        hpStatPoints = progress.hpPoints
        for (c in 1..5) {
            offenseStatPoints[c] = progress.offensePoints[c]
            defenseStatPoints[c] = progress.defensePoints[c]
        }
        talentAttunement = progress.talentAttunement
        talentSquareMatch = progress.talentSquareMatch
        talentPower4 = progress.talentPower4
        talentGuardianSkin = progress.talentGuardianSkin
        talentHeal = progress.talentHeal
        talentColorWipe = progress.talentColorWipe
        talentSecondWind = progress.talentSecondWind
        talentFocusStrike = progress.talentFocusStrike
        talentBarrier = progress.talentBarrier
        talentReroll = progress.talentReroll
        petType = when (progress.petType.uppercase()) {
            "FOX" -> PetType.FOX
            "TURTLE" -> PetType.TURTLE
            "DRAGON" -> PetType.DRAGON
            else -> PetType.NONE
        }
    }

    private fun savePersistentProgress() {
        ProgressStore.save(
            context = context,
            progress = PlayerProgress(
                playerLevel = playerLevel,
                playerXp = playerXp,
                xpToNextLevel = xpToNextLevel,
                unspentPoints = unspentStatPoints,
                unspentTalentPoints = unspentTalentPoints,
                hpPoints = hpStatPoints,
                offensePoints = offenseStatPoints.copyOf(),
                defensePoints = defenseStatPoints.copyOf(),
                talentAttunement = talentAttunement,
                talentSquareMatch = talentSquareMatch,
                talentPower4 = talentPower4,
                talentGuardianSkin = talentGuardianSkin,
                talentHeal = talentHeal,
                talentColorWipe = talentColorWipe,
                talentSecondWind = talentSecondWind,
                talentFocusStrike = talentFocusStrike,
                talentBarrier = talentBarrier,
                talentReroll = talentReroll,
                petType = petType.name
            )
        )
    }
    fun setInitialState(playerHp: Int, enemyHp: Int, turn: Turn) {
        isPvpMode = true
        battleOver = false
        battleResultDispatched = false
        pendingLevelComplete = false
        selected = null

        loadPersistentProgress()

        playerMaxHealth = playerHp
        playerHealth = playerHp
        displayedPlayerHealth = playerHp.toFloat()

        aiMaxHealth = enemyHp
        aiHealth = enemyHp
        displayedAiHealth = enemyHp.toFloat()

        this.turn = turn
        statusText = if (turn == Turn.PLAYER) "Your turn" else "AI is thinking..."

        healCharges = if (talentHeal) 1 else 0
        colorWipeCharges = if (talentColorWipe) 1 else 0
        barrierCharges = if (talentBarrier) 1 else 0
        rerollCharges = if (talentReroll) 1 else 0
        focusStrikeReady = talentFocusStrike
        barrierActive = false

        floatingTexts.clear()
        clearBursts.clear()
        rowSweeps.clear()
        pendingClearDamages.clear()

        engine.resetUntilMoveExists(rules = playerRules())
        displayBoard = engine.snapshotBoard()

        invalidate()
    }
    private fun dominantClearColor(cells: List<Cell>): Int {
        val counts = IntArray(6)
        for (cell in cells) {
            val color = baseColor(displayBoard[cell.row][cell.col])
            if (color in 1..5) counts[color]++
        }
        var best = 1
        for (c in 2..5) {
            if (counts[c] > counts[best]) best = c
        }
        return best
    }

    private fun scaleDamageByElements(baseDamage: Int, color: Int): Int {
        val offenseBonus = offenseStatPoints[color] * 2
        val petOffense = if (petType == PetType.DRAGON) 3 else 0
        val petDefense = if (petType == PetType.TURTLE) 3 else 0
        val defenseReduce = defenseStatPoints[color] * 2 + if (talentGuardianSkin) 2 else 0
        return when (damageTargetSide) {
            Side.AI -> (baseDamage + offenseBonus + petOffense).coerceAtLeast(1)
            Side.PLAYER -> (baseDamage - defenseReduce - petDefense).coerceAtLeast(1)
        }
    }

    private fun baseColor(value: Int): Int {
        return when {
            value in 1..5 -> value
            value in 101..105 -> value - 100
            value in 201..205 -> value - 200
            else -> 0
        }
    }
}
