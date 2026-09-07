package io.pryce.android.autospeed.notification

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.core.content.edit
import androidx.core.net.toUri
import io.pryce.android.autospeed.SettingsActivity

private const val PREFS_NAME = "autospeed_zen"
private const val KEY_RULE_ID = "rule_id"
private val CONDITION_URI: Uri = "condition://io.pryce.android.autospeed/suppression".toUri()

/**
 * Creates, activates, deactivates, and removes Autospeed's single app-owned [AutomaticZenRule]
 * (design section 4.3). Autospeed only ever creates or edits this one rule; it never touches the
 * global interruption filter (`NotificationManager.setNotificationPolicy`), edits another rule, or
 * reads prior DND state. Revoking Notification Policy Access removes Autospeed's effective
 * suppression (Android simply stops honoring the rule) without any code path here needing to
 * detect that revocation directly.
 */
class ZenRuleManager(
    private val context: Context,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPolicyAccess(): Boolean = notificationManager?.isNotificationPolicyAccessGranted == true

    /** Activates or deactivates Autospeed's rule. No-op when policy access is not granted. */
    fun setActive(active: Boolean) {
        val manager = notificationManager ?: return
        if (!hasPolicyAccess()) return
        val ruleId = ensureRule(manager) ?: return
        val condition =
            Condition(CONDITION_URI, "", if (active) Condition.STATE_TRUE else Condition.STATE_FALSE)
        runCatching { manager.setAutomaticZenRuleState(ruleId, condition) }
    }

    /** Deactivates and removes Autospeed's rule entirely, e.g. when suppression is disabled. */
    fun removeRule() {
        val manager = notificationManager ?: return
        val ruleId = prefs.getString(KEY_RULE_ID, null) ?: return
        runCatching { manager.removeAutomaticZenRule(ruleId) }
        prefs.edit { remove(KEY_RULE_ID) }
    }

    private fun ensureRule(manager: NotificationManager): String? {
        val existing = prefs.getString(KEY_RULE_ID, null)
        if (existing != null && manager.automaticZenRules.containsKey(existing)) {
            return existing
        }
        val policy =
            ZenPolicy
                .Builder()
                .allowAlarms(true)
                .allowMedia(true)
                .allowReminders(false)
                .allowEvents(false)
                .allowCalls(ZenPolicy.PEOPLE_TYPE_NONE)
                .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
                .allowRepeatCallers(false)
                .showFullScreenIntent(false)
                .showLights(false)
                .showPeeking(false)
                .showStatusBarIcons(true)
                .showInNotificationList(true)
                .build()
        val rule =
            AutomaticZenRule(
                "Autospeed",
                ComponentName(context, AutospeedZenConditionProviderService::class.java),
                ComponentName(context, SettingsActivity::class.java),
                CONDITION_URI,
                policy,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                true,
            )
        return runCatching { manager.addAutomaticZenRule(rule) }
            .onSuccess { id -> prefs.edit { putString(KEY_RULE_ID, id) } }
            .getOrNull()
    }
}
