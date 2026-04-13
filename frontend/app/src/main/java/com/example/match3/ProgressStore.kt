package com.example.match3

import android.content.Context

data class PlayerProgress(
    val playerLevel: Int,
    val playerXp: Int,
    val xpToNextLevel: Int,
    val unspentPoints: Int,
    val unspentTalentPoints: Int,
    val hpPoints: Int,
    val offensePoints: IntArray,
    val defensePoints: IntArray,
    val talentAttunement: Boolean,
    val talentSquareMatch: Boolean,
    val talentPower4: Boolean,
    val talentGuardianSkin: Boolean,
    val talentHeal: Boolean,
    val talentColorWipe: Boolean,
    val talentSecondWind: Boolean,
    val talentFocusStrike: Boolean,
    val talentBarrier: Boolean,
    val talentReroll: Boolean,
    val petType: String
)

object ProgressStore {
    private const val PREF_NAME = "match3_progress"
    private const val KEY_LEVEL = "player_level"
    private const val KEY_XP = "player_xp"
    private const val KEY_XP_NEXT = "xp_to_next"
    private const val KEY_POINTS = "unspent_points"
    private const val KEY_TALENT_POINTS = "unspent_talent_points"
    private const val KEY_HP_POINTS = "hp_points"
    private const val KEY_TALENT_ATTUNEMENT = "talent_attunement"
    private const val KEY_TALENT_SQUARE = "talent_square_match"
    private const val KEY_TALENT_POWER4 = "talent_power4"
    private const val KEY_TALENT_GUARDIAN = "talent_guardian"
    private const val KEY_TALENT_HEAL = "talent_heal"
    private const val KEY_TALENT_COLOR_WIPE = "talent_color_wipe"
    private const val KEY_TALENT_SECOND_WIND = "talent_second_wind"
    private const val KEY_TALENT_FOCUS_STRIKE = "talent_focus_strike"
    private const val KEY_TALENT_BARRIER = "talent_barrier"
    private const val KEY_TALENT_REROLL = "talent_reroll"
    private const val KEY_PET_TYPE = "pet_type"

    private fun offKey(color: Int): String = "offense_$color"
    private fun defKey(color: Int): String = "defense_$color"

    fun load(context: Context): PlayerProgress {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val offense = IntArray(6)
        val defense = IntArray(6)
        for (c in 1..5) {
            offense[c] = prefs.getInt(offKey(c), 0)
            defense[c] = prefs.getInt(defKey(c), 0)
        }
        val level = prefs.getInt(KEY_LEVEL, 1)
        val savedUnspent = prefs.getInt(KEY_POINTS, 0)
        val savedTalentUnspent = prefs.getInt(KEY_TALENT_POINTS, 0)
        val hpPoints = prefs.getInt(KEY_HP_POINTS, 0)
        val attunement = prefs.getBoolean(KEY_TALENT_ATTUNEMENT, false)
        val square = prefs.getBoolean(KEY_TALENT_SQUARE, false)
        val power4 = prefs.getBoolean(KEY_TALENT_POWER4, false)
        val guardian = prefs.getBoolean(KEY_TALENT_GUARDIAN, false)
        val heal = prefs.getBoolean(KEY_TALENT_HEAL, false)
        val colorWipe = prefs.getBoolean(KEY_TALENT_COLOR_WIPE, false)
        val secondWind = prefs.getBoolean(KEY_TALENT_SECOND_WIND, false)
        val focusStrike = prefs.getBoolean(KEY_TALENT_FOCUS_STRIKE, false)
        val barrier = prefs.getBoolean(KEY_TALENT_BARRIER, false)
        val reroll = prefs.getBoolean(KEY_TALENT_REROLL, false)
        val petType = prefs.getString(KEY_PET_TYPE, "None") ?: "None"
        val spent = hpPoints + (1..5).sumOf { offense[it] + defense[it] }
        val earned = (level - 1).coerceAtLeast(0) * 3
        val reconciledUnspent = maxOf(savedUnspent, earned - spent, 0)
        val spentTalent = listOf(
            attunement,
            square,
            power4,
            guardian,
            heal,
            colorWipe,
            secondWind,
            focusStrike,
            barrier,
            reroll
        ).count { it }
        val earnedTalent = (level - 1).coerceAtLeast(0)
        val reconciledTalentUnspent = maxOf(savedTalentUnspent, earnedTalent - spentTalent, 0)
        return PlayerProgress(
            playerLevel = level,
            playerXp = prefs.getInt(KEY_XP, 0),
            xpToNextLevel = prefs.getInt(KEY_XP_NEXT, 120),
            unspentPoints = reconciledUnspent,
            unspentTalentPoints = reconciledTalentUnspent,
            hpPoints = hpPoints,
            offensePoints = offense,
            defensePoints = defense,
            talentAttunement = attunement,
            talentSquareMatch = square,
            talentPower4 = power4,
            talentGuardianSkin = guardian,
            talentHeal = heal,
            talentColorWipe = colorWipe,
            talentSecondWind = secondWind,
            talentFocusStrike = focusStrike,
            talentBarrier = barrier,
            talentReroll = reroll,
            petType = petType
        )
    }

    fun save(context: Context, progress: PlayerProgress) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        edit.putInt(KEY_LEVEL, progress.playerLevel)
        edit.putInt(KEY_XP, progress.playerXp)
        edit.putInt(KEY_XP_NEXT, progress.xpToNextLevel)
        edit.putInt(KEY_POINTS, progress.unspentPoints)
        edit.putInt(KEY_TALENT_POINTS, progress.unspentTalentPoints)
        edit.putInt(KEY_HP_POINTS, progress.hpPoints)
        edit.putBoolean(KEY_TALENT_ATTUNEMENT, progress.talentAttunement)
        edit.putBoolean(KEY_TALENT_SQUARE, progress.talentSquareMatch)
        edit.putBoolean(KEY_TALENT_POWER4, progress.talentPower4)
        edit.putBoolean(KEY_TALENT_GUARDIAN, progress.talentGuardianSkin)
        edit.putBoolean(KEY_TALENT_HEAL, progress.talentHeal)
        edit.putBoolean(KEY_TALENT_COLOR_WIPE, progress.talentColorWipe)
        edit.putBoolean(KEY_TALENT_SECOND_WIND, progress.talentSecondWind)
        edit.putBoolean(KEY_TALENT_FOCUS_STRIKE, progress.talentFocusStrike)
        edit.putBoolean(KEY_TALENT_BARRIER, progress.talentBarrier)
        edit.putBoolean(KEY_TALENT_REROLL, progress.talentReroll)
        edit.putString(KEY_PET_TYPE, progress.petType)
        for (c in 1..5) {
            edit.putInt(offKey(c), progress.offensePoints[c])
            edit.putInt(defKey(c), progress.defensePoints[c])
        }
        edit.apply()
    }
}
