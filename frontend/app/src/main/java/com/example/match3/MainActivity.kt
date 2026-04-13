package com.example.match3

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private val prefs by lazy { getSharedPreferences("match3_stats", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121220"))
        }
        setContentView(root)
        testBackendConnection()
        showHome()
    }

    private var currentPvpGameId: String? = null
    private fun showHome() {
        val layout = screenLayout()
        layout.addView(title("Match3 Battle"))
        layout.addView(spacer(24))
        layout.addView(button("Play") { showLobby() })
        layout.addView(button("Stats Tree") { showStats() })
        layout.addView(button("Talent Tree") { showTalentHub() })
        layout.addView(button("Quit") { finish() })
        setScreen(layout)
    }

    private fun showLobby() {
        val layout = screenLayout()
        layout.addView(title("Lobby"))
        layout.addView(subtitle("Choose your mode"))
        layout.addView(spacer(16))
        layout.addView(button("Start PvE Battle") { startBattle() })
        layout.addView(button("Start PvP Battle") { startPvpBattleFromBackend() })
        layout.addView(button("Stats Tree") { showStats() })
        layout.addView(button("Talent Tree") { showTalentHub() })
        layout.addView(button("Back Home") { showHome() })
        setScreen(layout)
    }
    private fun startPvpBattle(playerHp: Int, enemyHp: Int, turn: GameView.Turn) {
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121220"))
        }

        val gameView = GameView(this)

        gameView.setInitialState(playerHp, enemyHp, turn)

        gameView.setBattleListener(object : GameView.BattleListener {
            override fun onBattleEnded(summary: GameView.BattleSummary) {
                runOnUiThread {
                    showPvpBattleResult(summary)
                }
            }
        })

        container.addView(
            gameView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 24)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#55000000"))
        }
        controls.addView(
            button("Test PvP Move") {
                makePvpMove()
            }
        )
        controls.addView(
            button("Back to Lobby") {
                showLobby()
            }
        )

        container.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setScreen(container)
    }
    private fun showStats() {
        val progress = ProgressStore.load(this)
        showStatsEditor(
            base = progress,
            hpDelta = 0,
            offenseDelta = IntArray(6),
            defenseDelta = IntArray(6),
            remaining = progress.unspentPoints
        )
    }
    private fun showPvpBattleResult(summary: GameView.BattleSummary) {
        val layout = screenLayout()
        layout.addView(title("PvP Battle Ended"))
        layout.addView(stat("Enemy level reached", summary.reachedEnemyLevel.toString()))
        layout.addView(stat("Score", summary.score.toString()))
        layout.addView(stat("Player level", summary.playerLevel.toString()))
        layout.addView(spacer(16))
        layout.addView(button("Back to Lobby") { showLobby() })
        setScreen(layout)
    }
    private fun startPvpBattleFromBackend() {
        Thread {
            try {
                val url = java.net.URL("http://10.0.2.2:3000/start-game")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                connection.outputStream.use { output ->
                    output.write("{}".toByteArray())
                }

                val response = connection.inputStream.bufferedReader().readText()
                android.util.Log.d("START_GAME", response)
                connection.disconnect()

                val json = org.json.JSONObject(response)
                val gameId = json.getString("gameId")
                currentPvpGameId = gameId

                val playerHp = json.getJSONObject("player").getInt("hp")
                val enemyHp = json.getJSONObject("enemy").getInt("hp")
                val turnString = json.getString("turn")

                val turn = if (turnString == "player") {
                    GameView.Turn.PLAYER
                } else {
                    GameView.Turn.AI
                }

                runOnUiThread {
                    startPvpBattle(playerHp, enemyHp, turn)
                }
            } catch (e: Exception) {
                android.util.Log.e("START_GAME", "Failed to start game from backend", e)
            }
        }.start()
    }

    private fun makePvpMove() {
        val gameId = currentPvpGameId ?: return

        Thread {
            try {
                val url = java.net.URL("http://10.0.2.2:3000/make-move")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val body = """{"gameId":"$gameId"}"""
                connection.outputStream.use { output ->
                    output.write(body.toByteArray())
                }

                val responseCode = connection.responseCode
                val response = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                }
                android.util.Log.d("PVP_MOVE", "Code=$responseCode Body=$response")

                val json = org.json.JSONObject(response)
                val playerHp = json.getInt("playerHp")
                val enemyHp = json.getInt("enemyHp")
                val playerDamage = json.getInt("playerDamage")
                val enemyDamage = json.getInt("enemyDamage")
                val turnString = json.getString("turn")
                val battleOver = json.getBoolean("battleOver")
                val winner = if (json.isNull("winner")) null else json.getString("winner")
                runOnUiThread {
                    if (battleOver) {
                        showPvpBattleFinished(playerHp, enemyHp, winner)
                    } else {
                        showPvpMoveResult(playerHp, enemyHp, playerDamage, enemyDamage, turnString)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PVP_MOVE", "Failed to make PvP move", e)
            }
        }.start()
    }

    private fun showPvpMoveResult(playerHp: Int, enemyHp: Int, playerDamage: Int, enemyDamage: Int, turn: String) {
        val layout = screenLayout()
        layout.addView(title("PvP Move Result"))
        layout.addView(stat("Player HP", playerHp.toString()))
        layout.addView(stat("Enemy HP", enemyHp.toString()))
        layout.addView(stat("You dealt", playerDamage.toString()))
        layout.addView(stat("Enemy dealt", enemyDamage.toString()))
        layout.addView(stat("Next turn", turn))
        layout.addView(spacer(16))
        layout.addView(button("Make Another PvP Move") { makePvpMove() })
        layout.addView(button("Back to Lobby") { showLobby() })
        setScreen(layout)
    }
    private fun showPvpBattleFinished(playerHp: Int, enemyHp: Int, winner: String?) {
        val layout = screenLayout()
        layout.addView(title("PvP Battle Finished"))

        val resultText = when (winner) {
            "player" -> "You won!"
            "enemy" -> "You lost!"
            else -> "Battle ended"
        }

        layout.addView(subtitle(resultText))
        layout.addView(spacer(12))
        layout.addView(stat("Player HP", playerHp.toString()))
        layout.addView(stat("Enemy HP", enemyHp.toString()))
        layout.addView(spacer(16))
        layout.addView(button("Back to Lobby") { showLobby() })

        setScreen(layout)
    }
    private fun startBattle() {
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121220"))
        }
        val gameView = GameView(this)
        gameView.setBattleListener(object : GameView.BattleListener {
            override fun onBattleEnded(summary: GameView.BattleSummary) {
                saveBattleSummary(summary)
                showBattleResult(summary)
            }
        })
        gameView.resetGame()

        container.addView(
            gameView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 24)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#55000000"))
        }
        val progress = ProgressStore.load(this)
        val rowTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val rowBottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        rowTop.addView(smallButton("Lobby") { showLobby() })
        rowTop.addView(smallButton("Heal") { gameView.useHealSkill() }.apply {
            isEnabled = progress.talentHeal
        })
        rowTop.addView(smallButton("Barrier") { gameView.useBarrierSkill() }.apply {
            isEnabled = progress.talentBarrier
        })
        rowTop.addView(smallButton("Reroll") { gameView.useRerollSkill() }.apply {
            isEnabled = progress.talentReroll
        })
        rowBottom.addView(smallButton("Fire") { gameView.useColorWipeSkill(1) }.apply {
            isEnabled = progress.talentColorWipe
        })
        rowBottom.addView(smallButton("Light") { gameView.useColorWipeSkill(2) }.apply {
            isEnabled = progress.talentColorWipe
        })
        rowBottom.addView(smallButton("Nature") { gameView.useColorWipeSkill(3) }.apply {
            isEnabled = progress.talentColorWipe
        })
        rowBottom.addView(smallButton("Water") { gameView.useColorWipeSkill(4) }.apply {
            isEnabled = progress.talentColorWipe
        })
        rowBottom.addView(smallButton("Arcane") { gameView.useColorWipeSkill(5) }.apply {
            isEnabled = progress.talentColorWipe
        })
        controls.addView(rowTop)
        controls.addView(rowBottom)
        container.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        setScreen(container)
    }

    private fun showBattleResult(summary: GameView.BattleSummary) {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#AA111122"))
        }
        overlay.addView(title("Run Complete"))
        overlay.addView(stat("Enemy level reached", summary.reachedEnemyLevel.toString()))
        overlay.addView(stat("Score", summary.score.toString()))
        overlay.addView(stat("Player level", summary.playerLevel.toString()))
        overlay.addView(spacer(16))
        overlay.addView(button("Play Again") { startBattle() })
        overlay.addView(button("Back Lobby") { showLobby() })
        setScreen(overlay)
    }

    private fun saveBattleSummary(summary: GameView.BattleSummary) {
        val battles = prefs.getInt("battles", 0) + 1
        val bestEnemyLevel = maxOf(prefs.getInt("best_enemy_level", 1), summary.reachedEnemyLevel)
        val bestScore = maxOf(prefs.getInt("best_score", 0), summary.score)
        val bestPlayerLevel = maxOf(prefs.getInt("best_player_level", 1), summary.playerLevel)
        prefs.edit()
            .putInt("battles", battles)
            .putInt("best_enemy_level", bestEnemyLevel)
            .putInt("best_score", bestScore)
            .putInt("best_player_level", bestPlayerLevel)
            .apply()
    }

    private fun showStatsEditor(
        base: PlayerProgress,
        hpDelta: Int,
        offenseDelta: IntArray,
        defenseDelta: IntArray,
        remaining: Int
    ) {
        val battles = prefs.getInt("battles", 0)
        val bestEnemyLevel = prefs.getInt("best_enemy_level", 1)
        val bestScore = prefs.getInt("best_score", 0)
        val bestPlayerLevel = prefs.getInt("best_player_level", 1)
        val colorNames = mapOf(1 to "Fire", 2 to "Light", 3 to "Nature", 4 to "Water", 5 to "Arcane")

        val layout = screenLayout()
        layout.addView(title("Stats"))
        layout.addView(spacer(10))
        layout.addView(stat("Total battles", battles.toString()))
        layout.addView(stat("Best enemy level reached", bestEnemyLevel.toString()))
        layout.addView(stat("Best score", bestScore.toString()))
        layout.addView(stat("Best player level", bestPlayerLevel.toString()))
        layout.addView(spacer(10))
        layout.addView(subtitle("Persistent Progress"))
        layout.addView(stat("Level", base.playerLevel.toString()))
        layout.addView(stat("XP", "${base.playerXp}/${base.xpToNextLevel}"))
        layout.addView(stat("Available stat points", remaining.toString()))
        layout.addView(stat("Available talent points", base.unspentTalentPoints.toString()))
        layout.addView(
            statCard(
                "Preview",
                listOf(
                    "Max HP: ${100 + (base.hpPoints + hpDelta) * 20}",
                    "Total offense points: ${(1..5).sumOf { base.offensePoints[it] + offenseDelta[it] }}",
                    "Total defense points: ${(1..5).sumOf { base.defensePoints[it] + defenseDelta[it] }}"
                )
            )
        )
        layout.addView(spacer(10))
        layout.addView(subtitle("Stat Tree (preview until Confirm)"))
        layout.addView(
            button("Quick: +1 all Offense") {
                if (remaining < 5) return@button
                val next = offenseDelta.copyOf()
                for (e in 1..5) next[e] += 1
                showStatsEditor(base, hpDelta, next, defenseDelta.copyOf(), remaining - 5)
            }
        )
        layout.addView(
            button("Quick: +1 all Defense") {
                if (remaining < 5) return@button
                val next = defenseDelta.copyOf()
                for (e in 1..5) next[e] += 1
                showStatsEditor(base, hpDelta, offenseDelta.copyOf(), next, remaining - 5)
            }
        )

        layout.addView(
            allocationRow(
                label = "HP (+20 max HP)",
                value = base.hpPoints + hpDelta,
                color = Color.parseColor("#C4B5FD"),
                canDec = hpDelta > 0,
                canInc = remaining > 0,
                onDec = {
                    showStatsEditor(
                        base,
                        hpDelta - 1,
                        offenseDelta.copyOf(),
                        defenseDelta.copyOf(),
                        remaining + 1
                    )
                },
                onInc = {
                    showStatsEditor(
                        base,
                        hpDelta + 1,
                        offenseDelta.copyOf(),
                        defenseDelta.copyOf(),
                        remaining - 1
                    )
                }
            )
        )

        for (element in 1..5) {
            val name = colorNames[element] ?: "Color $element"
            val elementColor = elementUiColor(element)
            layout.addView(
                allocationRow(
                    label = "\u25A0 Offense $name",
                    value = base.offensePoints[element] + offenseDelta[element],
                    color = elementColor,
                    canDec = offenseDelta[element] > 0,
                    canInc = remaining > 0,
                    onDec = {
                        val next = offenseDelta.copyOf()
                        next[element] -= 1
                        showStatsEditor(
                            base,
                            hpDelta,
                            next,
                            defenseDelta.copyOf(),
                            remaining + 1
                        )
                    },
                    onInc = {
                        val next = offenseDelta.copyOf()
                        next[element] += 1
                        showStatsEditor(
                            base,
                            hpDelta,
                            next,
                            defenseDelta.copyOf(),
                            remaining - 1
                        )
                    }
                )
            )
            layout.addView(
                allocationRow(
                    label = "\u25A0 Defense $name",
                    value = base.defensePoints[element] + defenseDelta[element],
                    color = elementColor,
                    canDec = defenseDelta[element] > 0,
                    canInc = remaining > 0,
                    onDec = {
                        val next = defenseDelta.copyOf()
                        next[element] -= 1
                        showStatsEditor(
                            base,
                            hpDelta,
                            offenseDelta.copyOf(),
                            next,
                            remaining + 1
                        )
                    },
                    onInc = {
                        val next = defenseDelta.copyOf()
                        next[element] += 1
                        showStatsEditor(
                            base,
                            hpDelta,
                            offenseDelta.copyOf(),
                            next,
                            remaining - 1
                        )
                    }
                )
            )
        }

        val changed = hpDelta > 0 || (1..5).any { offenseDelta[it] > 0 || defenseDelta[it] > 0 }
        layout.addView(spacer(12))
        layout.addView(
            button("Confirm Allocation") {
                if (!changed) return@button
                applyAllocation(
                    base = base,
                    hpDelta = hpDelta,
                    offenseDelta = offenseDelta,
                    defenseDelta = defenseDelta,
                    remaining = remaining
                )
                showStats()
            }.apply { isEnabled = changed }
        )
        layout.addView(button("Reset Preview") { showStats() })
        layout.addView(button("Open Talent Tree") { showTalentHub() })
        layout.addView(button("Back Lobby") { showLobby() })
        layout.addView(button("Back Home") { showHome() })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(layout)
        }
        setScreen(scroll)
    }

    private fun applyAllocation(
        base: PlayerProgress,
        hpDelta: Int,
        offenseDelta: IntArray,
        defenseDelta: IntArray,
        remaining: Int
    ) {
        val nextOffense = base.offensePoints.copyOf()
        val nextDefense = base.defensePoints.copyOf()
        for (c in 1..5) {
            nextOffense[c] += offenseDelta[c]
            nextDefense[c] += defenseDelta[c]
        }
        val updated = base.copy(
            unspentPoints = remaining,
            hpPoints = base.hpPoints + hpDelta,
            offensePoints = nextOffense,
            defensePoints = nextDefense
        )
        ProgressStore.save(this, updated)
    }

    private fun showTalentHub() {
        val p = ProgressStore.load(this)
        val layout = screenLayout()
        layout.addView(title("Talent Tree"))
        layout.addView(stat("Available talent points", p.unspentTalentPoints.toString()))
        layout.addView(spacer(12))
        layout.addView(button("Passive Talents") { showPassiveTalents() })
        layout.addView(button("Active Skills") { showActiveTalents() })
        layout.addView(button("Pet") { showPetMenu() })
        layout.addView(button("Back Lobby") { showLobby() })
        setScreen(layout)
    }

    private fun showPassiveTalents() {
        val p = ProgressStore.load(this)
        val layout = screenLayout()
        layout.addView(title("Passive Talents"))
        layout.addView(stat("Talent points", p.unspentTalentPoints.toString()))
        layout.addView(subtitle("Tree: Core -> Branches"))
        layout.addView(treeMap("Core", listOf("Attunement", "Square", "Power4", "Guardian")))
        layout.addView(
            talentTreeNode(
                prefix = "Core",
                "Elemental Attunement (+2 base damage)",
                p.talentAttunement,
                p.unspentTalentPoints > 0
            ) { unlockTalent("attunement") }
        )
        layout.addView(
            talentTreeNode(
                prefix = "\u251C\u2500 Combo",
                "Square Match (2x2 clears)",
                p.talentSquareMatch,
                p.unspentTalentPoints > 0 && p.talentAttunement
            ) { unlockTalent("square") }
        )
        layout.addView(
            talentTreeNode(
                prefix = "\u2502  \u2514\u2500 Combo+",
                "Power4 (+8 on 4+ matches)",
                p.talentPower4,
                p.unspentTalentPoints > 0 && p.talentSquareMatch
            ) { unlockTalent("power4") }
        )
        layout.addView(
            talentTreeNode(
                prefix = "\u2514\u2500 Defense",
                "Guardian Skin (+flat defense)",
                p.talentGuardianSkin,
                p.unspentTalentPoints > 0 && p.talentAttunement
            ) { unlockTalent("guardian") }
        )
        layout.addView(
            talentTreeNode(
                prefix = "\u2514\u2500 Sustain",
                "Second Wind (+10% HP at battle start)",
                p.talentSecondWind,
                p.unspentTalentPoints > 0 && p.talentGuardianSkin
            ) { unlockTalent("secondwind") }
        )
        layout.addView(
            talentTreeNode(
                prefix = "\u251C\u2500 Burst",
                "Focus Strike (+bonus on first hit)",
                p.talentFocusStrike,
                p.unspentTalentPoints > 0 && p.talentPower4
            ) { unlockTalent("focus") }
        )
        layout.addView(button("Back Talent Hub") { showTalentHub() })
        setScreen(ScrollView(this).apply { addView(layout) })
    }

    private fun showActiveTalents() {
        val p = ProgressStore.load(this)
        val layout = screenLayout()
        layout.addView(title("Active Skills"))
        layout.addView(stat("Talent points", p.unspentTalentPoints.toString()))
        layout.addView(subtitle("Tree: Core -> Skills"))
        layout.addView(treeMap("Core", listOf("Heal", "Color Wipe")))
        layout.addView(
            talentTreeNode(
                prefix = "Core",
                "Heal (1 use/battle)",
                p.talentHeal,
                p.unspentTalentPoints > 0 && p.talentAttunement
            ) { unlockTalent("heal") }
        )
        layout.addView(
            talentTreeNode(
                prefix = "\u2514\u2500 Upgrade",
                "Color Wipe (1 use/battle)",
                p.talentColorWipe,
                p.unspentTalentPoints > 0 && p.talentHeal
            ) { unlockTalent("colorwipe") }
        )
        layout.addView(
            talentTreeNode(
                prefix = "\u2514\u2500 Defense",
                "Barrier (halve next incoming hit)",
                p.talentBarrier,
                p.unspentTalentPoints > 0 && p.talentHeal
            ) { unlockTalent("barrier") }
        )
        layout.addView(
            talentTreeNode(
                prefix = "\u251C\u2500 Utility",
                "Reroll (reshuffle board once)",
                p.talentReroll,
                p.unspentTalentPoints > 0 && p.talentColorWipe
            ) { unlockTalent("reroll") }
        )
        layout.addView(button("Back Talent Hub") { showTalentHub() })
        setScreen(layout)
    }

    private fun showPetMenu() {
        val p = ProgressStore.load(this)
        val layout = screenLayout()
        layout.addView(title("Pet"))
        layout.addView(subtitle("Current: ${p.petType}"))
        layout.addView(button("No Pet") { setPet("None") })
        layout.addView(button("Fox (+heal skill power)") { setPet("Fox") })
        layout.addView(button("Turtle (+defense)") { setPet("Turtle") })
        layout.addView(button("Dragon (+offense)") { setPet("Dragon") })
        layout.addView(button("Back Talent Hub") { showTalentHub() })
        setScreen(layout)
    }

    private fun unlockTalent(kind: String) {
        val p = ProgressStore.load(this)
        if (p.unspentTalentPoints <= 0) return
        val updated = when (kind) {
            "attunement" -> if (!p.talentAttunement) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentAttunement = true
            ) else p

            "square" -> if (!p.talentSquareMatch && p.talentAttunement) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentSquareMatch = true
            ) else p

            "power4" -> if (!p.talentPower4 && p.talentSquareMatch) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentPower4 = true
            ) else p

            "guardian" -> if (!p.talentGuardianSkin && p.talentAttunement) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentGuardianSkin = true
            ) else p

            "secondwind" -> if (!p.talentSecondWind && p.talentGuardianSkin) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentSecondWind = true
            ) else p

            "focus" -> if (!p.talentFocusStrike && p.talentPower4) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentFocusStrike = true
            ) else p

            "heal" -> if (!p.talentHeal && p.talentAttunement) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentHeal = true
            ) else p

            "colorwipe" -> if (!p.talentColorWipe && p.talentHeal) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentColorWipe = true
            ) else p

            "barrier" -> if (!p.talentBarrier && p.talentHeal) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentBarrier = true
            ) else p

            "reroll" -> if (!p.talentReroll && p.talentColorWipe) p.copy(
                unspentTalentPoints = p.unspentTalentPoints - 1,
                talentReroll = true
            ) else p

            else -> p
        }
        ProgressStore.save(this, updated)
        when (kind) {
            "attunement", "square", "power4", "guardian", "secondwind", "focus" -> showPassiveTalents()
            "heal", "colorwipe", "barrier", "reroll" -> showActiveTalents()
        }
    }

    private fun setPet(pet: String) {
        val p = ProgressStore.load(this)
        ProgressStore.save(this, p.copy(petType = pet))
        showPetMenu()
    }

    private fun setScreen(view: View) {
        root.removeAllViews()
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun screenLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
            setBackgroundColor(Color.parseColor("#121220"))
        }
    }

    private fun title(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 30f
        }
    }

    private fun subtitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#B8C0FF"))
            textSize = 18f
        }
    }

    private fun stat(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label: $value"
            setTextColor(Color.parseColor("#E5E7EB"))
            textSize = 20f
            setPadding(0, 6, 0, 6)
        }
    }

    private fun statCard(title: String, rows: List<String>): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundColor(Color.parseColor("#1A1A30"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 10
            layoutParams = p
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#C4B5FD"))
            textSize = 16f
        })
        for (row in rows) {
            card.addView(TextView(this).apply {
                text = row
                setTextColor(Color.parseColor("#E5E7EB"))
                textSize = 14f
                setPadding(0, 4, 0, 0)
            })
        }
        return card
    }

    private fun spacer(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun button(label: String, onClick: () -> Unit): AppCompatButton {
        return AppCompatButton(this).apply {
            text = label
            setOnClickListener { onClick() }
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2F3A8F"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 12
            layoutParams = p
            setPadding(24, 22, 24, 22)
        }
    }

    private fun smallButton(label: String, onClick: () -> Unit): AppCompatButton {
        return AppCompatButton(this).apply {
            text = label
            setOnClickListener { onClick() }
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3848A8"))
            val p = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            p.marginEnd = 8
            layoutParams = p
            setPadding(10, 12, 10, 12)
        }
    }

    private fun allocationRow(
        label: String,
        value: Int,
        color: Int,
        canDec: Boolean,
        canInc: Boolean,
        onDec: () -> Unit,
        onInc: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#1E1E2C"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 8
            layoutParams = p
        }
        val labelView = TextView(this).apply {
            text = "$label: $value"
            setTextColor(color)
            textSize = 17f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val minus = AppCompatButton(this).apply {
            text = "-"
            isEnabled = canDec
            setOnClickListener { onDec() }
        }
        val plus = AppCompatButton(this).apply {
            text = "+"
            isEnabled = canInc
            setOnClickListener { onInc() }
        }
        row.addView(labelView)
        row.addView(minus)
        row.addView(plus)
        return row
    }

    private fun talentTreeNode(
        prefix: String,
        label: String,
        unlocked: Boolean,
        canUnlock: Boolean,
        onUnlock: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#1E1E2C"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 8
            layoutParams = p
        }
        val status = if (unlocked) "Unlocked" else "Locked"
        val labelView = TextView(this).apply {
            text = "$prefix  $label ($status)"
            setTextColor(if (unlocked) Color.parseColor("#86EFAC") else Color.parseColor("#E5E7EB"))
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val action = AppCompatButton(this).apply {
            text = if (unlocked) "Done" else "Unlock"
            isEnabled = !unlocked && canUnlock
            setOnClickListener { onUnlock() }
        }
        row.addView(labelView)
        row.addView(action)
        return row
    }

    private fun treeMap(root: String, branches: List<String>): TextView {
        val lines = buildString {
            append(root).append('\n')
            branches.forEachIndexed { idx, b ->
                append(if (idx == branches.lastIndex) "\u2514\u2500 " else "\u251C\u2500 ")
                append(b).append('\n')
            }
        }
        return TextView(this).apply {
            text = lines.trimEnd()
            setTextColor(Color.parseColor("#A5B4FC"))
            textSize = 14f
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#191932"))
        }
    }
    private fun testBackendConnection() {
        Thread {
            try {
                val url = java.net.URL("http://10.0.2.2:3000/health")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val response = connection.inputStream.bufferedReader().readText()
                android.util.Log.d("API_TEST", response)

                connection.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("API_TEST", "Backend connection failed", e)
            }
        }.start()
    }
    private fun elementUiColor(element: Int): Int {
        return when (element) {
            1 -> Color.parseColor("#FF5C8A")
            2 -> Color.parseColor("#FFD166")
            3 -> Color.parseColor("#06D6A0")
            4 -> Color.parseColor("#4CC9F0")
            5 -> Color.parseColor("#B388FF")
            else -> Color.parseColor("#E5E7EB")
        }
    }
}
