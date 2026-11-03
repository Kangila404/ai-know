package org.aiknow.android.notifications

/** A notification can open one destination; never trust a push as proof of admin access. */
sealed interface PushDestination {
    data class Card(val id: Long) : PushDestination
    data class AdminAlert(val id: Long) : PushDestination

    companion object {
        fun parse(adminAlertId: String?, cardNewsId: String?): PushDestination? {
            // An invalid admin payload must not fall through into an unrelated card.
            if (adminAlertId != null) return adminAlertId.toLongOrNull()?.takeIf { it > 0 }?.let(::AdminAlert)
            return cardNewsId?.toLongOrNull()?.takeIf { it > 0 }?.let(::Card)
        }
    }
}

data class PendingPush(val destination: PushDestination, val sequence: Long)
