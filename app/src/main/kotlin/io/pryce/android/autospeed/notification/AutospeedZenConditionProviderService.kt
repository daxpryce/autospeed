package io.pryce.android.autospeed.notification

import android.net.Uri
import android.service.notification.ConditionProviderService

/**
 * The [ConditionProviderService] Autospeed's own [AutomaticZenRule][android.app.AutomaticZenRule]
 * declares as its owner (design section 4.3). The system still requires a valid, enabled
 * [ConditionProviderService] component to accept an app-owned rule, even though the class itself
 * is deprecated in favor of [android.app.NotificationManager.setAutomaticZenRuleState] for actual
 * state pushes -- which is exactly what [ZenRuleManager.setActive] already uses, so this service
 * does no work of its own beyond satisfying that ownership requirement.
 */
@Suppress("DEPRECATION")
class AutospeedZenConditionProviderService : ConditionProviderService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onConnected() = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onSubscribe(conditionId: Uri) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onUnsubscribe(conditionId: Uri) = Unit
}
