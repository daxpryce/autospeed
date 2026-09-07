package io.pryce.android.autospeed.core.build

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalUseAcknowledgmentTest {
    @Test
    fun `exact required text is satisfied`() {
        assertTrue(PersonalUseAcknowledgment.isSatisfiedBy(PersonalUseAcknowledgment.REQUIRED_TEXT))
    }

    @Test
    fun `required text matches the design document wording exactly`() {
        assertTrue(
            PersonalUseAcknowledgment.isSatisfiedBy(
                "Do not configure or interact with Autospeed while driving. " +
                    "Obey applicable laws and remain attentive.",
            ),
        )
    }

    @Test
    fun `null value is not satisfied`() {
        assertFalse(PersonalUseAcknowledgment.isSatisfiedBy(null))
    }

    @Test
    fun `empty value is not satisfied`() {
        assertFalse(PersonalUseAcknowledgment.isSatisfiedBy(""))
    }

    @Test
    fun `value differing by one character is not satisfied`() {
        assertFalse(
            PersonalUseAcknowledgment.isSatisfiedBy(
                "Do not configure or interact with Autospeed while driving. " +
                    "Obey applicable laws and remain attentive!",
            ),
        )
    }

    @Test
    fun `value differing by trailing whitespace is not satisfied`() {
        assertFalse(PersonalUseAcknowledgment.isSatisfiedBy("${PersonalUseAcknowledgment.REQUIRED_TEXT} "))
    }

    @Test
    fun `value differing by case is not satisfied`() {
        assertFalse(PersonalUseAcknowledgment.isSatisfiedBy(PersonalUseAcknowledgment.REQUIRED_TEXT.uppercase()))
    }
}
