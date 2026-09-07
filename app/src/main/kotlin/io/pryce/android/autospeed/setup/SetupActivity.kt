package io.pryce.android.autospeed.setup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.pryce.android.autospeed.R
import io.pryce.android.autospeed.ServiceLocator
import io.pryce.android.autospeed.ui.ContentScreenAppearance
import io.pryce.android.autospeed.ui.applyContentInsets
import io.pryce.android.autospeed.ui.dialogBuilder
import io.pryce.android.autospeed.ui.keepScreenOnWhileVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SETUP_PERMISSION_REQUEST_CODE = 2001

/**
 * Offers every permission and special access Autospeed can use, up front, before driving starts.
 *
 * This exists because Android's permission dialogs and special-access screens are hostile when
 * they appear mid-drive. Granting here is entirely optional: only location is required, each row
 * explains what its access is for, and the screen can be dismissed with anything still ungranted.
 * The just-in-time request paths remain in place for access that is skipped here or revoked later
 * (design section 6.5).
 */
class SetupActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rows = mutableMapOf<AccessRequirement, View>()
    private lateinit var appearance: ContentScreenAppearance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        window.keepScreenOnWhileVisible()
        findViewById<View>(R.id.content_container)
            .applyContentInsets(resources.getDimensionPixelSize(R.dimen.content_padding))
        appearance =
            ContentScreenAppearance(
                ServiceLocator.appearanceManager,
                window,
                findViewById(R.id.content_container),
            )
        buildRows()
        findViewById<Button>(R.id.setup_done_button).setOnClickListener { completeSetup() }
    }

    /**
     * Statuses are refreshed on every resume because the special-access entries are granted on a
     * system screen, which gives no result callback: returning here is the only signal we get.
     */
    override fun onResume() {
        super.onResume()
        refreshRows()
        // Rows are inflated and re-styled here, so the palette has to be re-applied after them.
        activityScope.launch {
            appearance.apply(
                ServiceLocator.settingsRepository.settings
                    .first()
                    .appearanceMode,
            )
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun buildRows() {
        val container = findViewById<LinearLayout>(R.id.setup_container)
        val inflater = LayoutInflater.from(this)
        AccessRequirement.entries.forEach { requirement ->
            val row = inflater.inflate(R.layout.item_access_requirement, container, false)
            row.findViewById<TextView>(R.id.access_title).setText(requirement.titleRes)
            row.findViewById<TextView>(R.id.access_explanation).setText(requirement.explanationRes)
            row.findViewById<Button>(R.id.access_grant_button).setOnClickListener { requestAccess(requirement) }
            container.addView(row)
            rows[requirement] = row
        }
    }

    private fun refreshRows() {
        rows.forEach { (requirement, row) ->
            val granted = requirement.isGranted(this)
            row.findViewById<TextView>(R.id.access_status).setText(
                when {
                    granted -> R.string.access_status_granted
                    requirement.required -> R.string.access_status_required
                    else -> R.string.access_status_optional
                },
            )
            row.findViewById<Button>(R.id.access_grant_button).isEnabled = !granted
        }
    }

    private fun requestAccess(requirement: AccessRequirement) {
        val permissions = requirement.runtimePermissions()
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions, SETUP_PERMISSION_REQUEST_CODE)
            return
        }
        val intent = requirement.settingsIntent(this) ?: return
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Design section 7: a missing system screen is reported rather than silently doing
            // nothing, so the user is not left tapping a control that never responds.
            ServiceLocator.appearanceManager
                .dialogBuilder(this)
                .setMessage(R.string.error_settings_unavailable)
                .setPositiveButton(R.string.advisory_dismiss, null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SETUP_PERMISSION_REQUEST_CODE) {
            refreshRows()
        }
    }

    private fun completeSetup() {
        activityScope.launch {
            ServiceLocator.settingsRepository.update { it.copy(setupCompleted = true) }
            finish()
        }
    }
}
