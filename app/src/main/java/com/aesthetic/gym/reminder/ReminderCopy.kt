package com.aesthetic.gym.reminder

/**
 * Every reminder string (es-ES). Tone: neutral, short, dismissible. They always reference the
 * plan the user set. Forbidden: false urgency, guilt, "streak at risk", absences, "we miss you".
 * Rule of thumb: if writing it means mentioning what the user did NOT do, it is not used.
 *
 * This is the single file to audit to know the ethical line is still intact.
 */
object ReminderCopy {

    // ---- Reminder notification (a training day) ----
    const val REMINDER_TITLE = "Zenit"

    /** With a routine and a resolvable day. */
    fun bodyWithDay(dayName: String) = "Hoy en tu plan: $dayName"

    /** No active routine / no resolvable day. */
    const val GENERIC_BODY = "Hoy toca entrenar según tu plan"

    // ---- Auto-disable: one final notice, for transparency ----
    const val PAUSED_TITLE = "Hemos pausado los recordatorios"
    const val PAUSED_BODY = "No queremos molestarte. Reactívalos cuando quieras desde Perfil."

    // ---- Settings: manufacturers + inexactness (honest, promising no reliability) ----
    const val OEM_WARNING =
        "En algunos móviles (Xiaomi, Huawei, Samsung, OPPO…) el sistema puede retrasar o " +
            "cancelar el aviso para ahorrar batería. Si no te llega, entra en los ajustes de la " +
            "app, permite el «inicio automático» y quita la optimización de batería para Zenit. " +
            "No podemos garantizar que llegue siempre a la hora exacta."

    // ---- Permission blocked (two refusals) ----
    const val PERM_BLOCKED =
        "Los avisos están bloqueados en el sistema. Actívalos en Ajustes para recibir tu recordatorio."
}
