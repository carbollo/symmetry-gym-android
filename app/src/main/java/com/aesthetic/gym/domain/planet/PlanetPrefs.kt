package com.aesthetic.gym.domain.planet

import android.content.Context

/**
 * Persistencia mínima del planeta (SharedPreferences, mismo nivel que el resto de la app):
 *  - la SEMILLA base se congela la primera vez que existe historial, para que editar o
 *    borrar la primera sesión jamás repinte los planetas ya vistos;
 *  - la MARCA DE AGUA de puntos garantiza que el planeta nunca regrese aunque se borren
 *    sesiones ("tu esfuerzo nunca se pierde").
 */
object PlanetPrefs {
    private const val PREFS = "zenit_planet"
    private const val KEY_BASE_SEED = "base_seed"
    private const val KEY_HIGH_WATER = "high_water_xp"

    fun baseSeed(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_BASE_SEED, 0L)

    fun highWater(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HIGH_WATER, 0)

    fun persist(context: Context, baseSeed: Long, totalXp: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (baseSeed != 0L && prefs.getLong(KEY_BASE_SEED, 0L) == 0L) {
            editor.putLong(KEY_BASE_SEED, baseSeed)
        }
        if (totalXp > prefs.getInt(KEY_HIGH_WATER, 0)) {
            editor.putInt(KEY_HIGH_WATER, totalXp)
        }
        editor.apply()
    }
}
