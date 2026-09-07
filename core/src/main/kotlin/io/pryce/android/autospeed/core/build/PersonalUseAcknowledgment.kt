package io.pryce.android.autospeed.core.build

/**
 * Validates the exact, case-sensitive build-time acknowledgment required to produce a personal
 * profile release artifact (design section 2.1). The check is intentionally a plain string
 * comparison: the value is not a secret and must not be treated as authentication, only as
 * evidence of a deliberate build-time choice.
 */
object PersonalUseAcknowledgment {
    const val REQUIRED_TEXT: String =
        "Do not configure or interact with Autospeed while driving. " +
            "Obey applicable laws and remain attentive."

    /** Returns true only when [candidate] matches [REQUIRED_TEXT] exactly. */
    fun isSatisfiedBy(candidate: String?): Boolean = candidate == REQUIRED_TEXT
}
