package com.pawly.app.community

/**
 * A professional contact is public only after explicit consent, for a professional
 * account, and when a non-empty contact value exists.
 */
internal object ProfessionalContactPolicy {
    fun isPublic(accountType: String, consent: Boolean, contact: String): Boolean =
        accountType == "professional" && consent && contact.isNotBlank()
}
