package io.pryce.android.autospeed

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import io.pryce.android.autospeed.companion.CompanionRole
import io.pryce.android.autospeed.companion.InstalledApplication
import io.pryce.android.autospeed.core.appearance.AppearanceMode
import io.pryce.android.autospeed.core.build.LocationBackend
import io.pryce.android.autospeed.core.settings.AutospeedSettings
import io.pryce.android.autospeed.core.settings.BackgroundMode
import io.pryce.android.autospeed.core.settings.OrientationMode
import io.pryce.android.autospeed.core.speed.SpeedUnit
import io.pryce.android.autospeed.setup.SetupActivity
import io.pryce.android.autospeed.ui.BackgroundColorPicker
import io.pryce.android.autospeed.ui.ContentScreenAppearance
import io.pryce.android.autospeed.ui.PaletteSpinnerAdapter
import io.pryce.android.autospeed.ui.applyContentInsets
import io.pryce.android.autospeed.ui.dialogBuilder
import io.pryce.android.autospeed.ui.keepScreenOnWhileVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Autospeed's single settings screen (design section 3.3): every field on it maps directly to an
 * [AutospeedSettings] value. Applies each change immediately via
 * [io.pryce.android.autospeed.settings.SettingsRepository.update] rather than a separate "save" step.
 */
private const val REQUEST_BACKGROUND_IMAGE = 1

class SettingsActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var orientationSpinner: Spinner
    private lateinit var primaryUnitSpinner: Spinner
    private lateinit var appearanceSpinner: Spinner
    private lateinit var backgroundSpinner: Spinner
    private lateinit var secondaryVisibleSwitch: Switch
    private lateinit var notificationSuppressionSwitch: Switch
    private lateinit var playFallbackRow: View
    private lateinit var playFallbackSwitch: Switch
    private lateinit var mapAppButton: Button
    private lateinit var mediaAppButton: Button
    private lateinit var backgroundColorButton: Button
    private lateinit var backgroundImageButton: Button

    /** Guards against re-entrant writes while spinner/switch listeners apply a freshly-read value. */
    private var applyingRemoteState = false
    private var settingsJob: Job? = null
    private var selectedImageUri: String? = null
    private lateinit var appearance: ContentScreenAppearance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        window.keepScreenOnWhileVisible()
        // Keeps settings text clear of the system bars, the display cutout, and the physical
        // screen edge. See applyContentInsets for why fitsSystemWindows cannot do this.
        findViewById<View>(R.id.content_container)
            .applyContentInsets(resources.getDimensionPixelSize(R.dimen.content_padding))
        appearance =
            ContentScreenAppearance(
                ServiceLocator.appearanceManager,
                window,
                findViewById(R.id.content_container),
            )
        bindViews()
        populateSpinners()
        wireListeners()
        findViewById<Button>(R.id.review_access_button).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        findViewById<Button>(R.id.about_button).setOnClickListener { showAboutDialog() }
    }

    /**
     * States who wrote Autospeed and how, that it carries no warranty, and that the vehicle's own
     * instrument cluster is the authority on speed. Presented on demand rather than at startup so
     * it never stands between the driver and the readout.
     */
    private fun showAboutDialog() {
        ServiceLocator.appearanceManager
            .dialogBuilder(this)
            .setTitle(R.string.about_dialog_title)
            .setMessage(R.string.about_dialog_body)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        settingsJob =
            activityScope.launch {
                ServiceLocator.settingsRepository.settings.collect { settings -> applySettingsToViews(settings) }
            }
    }

    override fun onStop() {
        // Without this, every return to this screen would stack another live collector on the
        // settings flow for as long as the activity lives.
        settingsJob?.cancel()
        settingsJob = null
        super.onStop()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        orientationSpinner = findViewById(R.id.orientation_spinner)
        primaryUnitSpinner = findViewById(R.id.primary_unit_spinner)
        appearanceSpinner = findViewById(R.id.appearance_spinner)
        backgroundSpinner = findViewById(R.id.background_spinner)
        secondaryVisibleSwitch = findViewById(R.id.secondary_visible_switch)
        notificationSuppressionSwitch = findViewById(R.id.notification_suppression_switch)
        playFallbackRow = findViewById(R.id.play_fallback_row)
        playFallbackSwitch = findViewById(R.id.play_fallback_switch)
        mapAppButton = findViewById(R.id.map_app_button)
        mediaAppButton = findViewById(R.id.media_app_button)
        backgroundColorButton = findViewById(R.id.background_color_button)
        backgroundImageButton = findViewById(R.id.background_image_button)
        playFallbackRow.visibility =
            if (ServiceLocator.locationBackend == LocationBackend.PLAY) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
    }

    private fun populateSpinners() {
        orientationSpinner.adapter = simpleAdapter(R.array.orientation_entries)
        primaryUnitSpinner.adapter = simpleAdapter(R.array.primary_unit_entries)
        appearanceSpinner.adapter = simpleAdapter(R.array.appearance_entries)
        backgroundSpinner.adapter = simpleAdapter(R.array.background_entries)
    }

    private fun simpleAdapter(arrayRes: Int) = PaletteSpinnerAdapter(this, resources.getTextArray(arrayRes))

    private fun wireListeners() {
        orientationSpinner.onItemSelectedListener =
            onSelected { position -> update { it.copy(orientationMode = OrientationMode.entries[position]) } }
        primaryUnitSpinner.onItemSelectedListener =
            onSelected { position -> update { it.copy(primaryUnit = SpeedUnit.entries[position]) } }
        appearanceSpinner.onItemSelectedListener =
            onSelected { position -> update { it.copy(appearanceMode = AppearanceMode.entries[position]) } }
        backgroundSpinner.onItemSelectedListener =
            onSelected { position -> update { it.copy(backgroundMode = BackgroundMode.entries[position]) } }

        secondaryVisibleSwitch.setOnCheckedChangeListener(
            onChecked { checked -> update { it.copy(secondaryVisible = checked) } },
        )
        notificationSuppressionSwitch.setOnCheckedChangeListener(
            onChecked { checked -> update { it.copy(notificationSuppressionEnabled = checked) } },
        )
        playFallbackSwitch.setOnCheckedChangeListener(
            onChecked { checked -> update { it.copy(playFallbackEnabled = checked) } },
        )

        mapAppButton.setOnClickListener { showApplicationPicker(isMap = true) }
        mediaAppButton.setOnClickListener { showApplicationPicker(isMap = false) }
        backgroundColorButton.setOnClickListener {
            BackgroundColorPicker.show(this, ServiceLocator.appearanceManager) { color ->
                update { it.copy(backgroundColorArgb = color) }
            }
        }
        backgroundImageButton.setOnClickListener { openImagePicker() }
    }

    private fun onSelected(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: android.view.View?,
            position: Int,
            id: Long,
        ) {
            if (!applyingRemoteState) action(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    private fun onChecked(action: (Boolean) -> Unit) = CompoundButton.OnCheckedChangeListener { _, checked ->
        if (!applyingRemoteState) action(checked)
    }

    private fun update(transform: (AutospeedSettings) -> AutospeedSettings) {
        activityScope.launch { ServiceLocator.settingsRepository.update(transform) }
    }

    private fun applySettingsToViews(settings: AutospeedSettings) {
        applyingRemoteState = true
        orientationSpinner.setSelection(settings.orientationMode.ordinal)
        primaryUnitSpinner.setSelection(settings.primaryUnit.ordinal)
        appearanceSpinner.setSelection(settings.appearanceMode.ordinal)
        backgroundSpinner.setSelection(settings.backgroundMode.ordinal)
        secondaryVisibleSwitch.isChecked = settings.secondaryVisible
        notificationSuppressionSwitch.isChecked = settings.notificationSuppressionEnabled
        playFallbackSwitch.isChecked = settings.playFallbackEnabled
        mapAppButton.text = applicationLabelOrDefault(settings.selectedMapPackage)
        mediaAppButton.text = applicationLabelOrDefault(settings.selectedMediaPackage)
        backgroundColorButton.visibility = visibleIf(settings.backgroundMode == BackgroundMode.SOLID_COLOR)
        backgroundImageButton.visibility = visibleIf(settings.backgroundMode == BackgroundMode.IMAGE)
        selectedImageUri = settings.backgroundImageUri
        backgroundImageButton.setText(
            if (settings.backgroundImageUri == null) {
                R.string.settings_background_image
            } else {
                R.string.settings_background_image_change
            },
        )
        appearance.apply(settings.appearanceMode)
        applyingRemoteState = false
    }

    private fun visibleIf(condition: Boolean): Int = if (condition) View.VISIBLE else View.GONE

    /**
     * Design section 3.7: image selection goes through the Storage Access Framework, and only the
     * returned URI grant is retained -- Autospeed never asks for broad photo or storage access and
     * never copies the image into its own storage.
     */
    private fun openImagePicker() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_BACKGROUND_IMAGE)
    }

    @Deprecated("Activity result callback; this screen is a plain Activity with no result registry.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_BACKGROUND_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        onImageChosen(uri)
    }

    /**
     * The grant has to be taken persistably here: without it the URI is readable only until this
     * process dies, and the background would silently disappear on the next launch. The previous
     * selection's grant is released so Autospeed holds exactly the access it is still using.
     */
    private fun onImageChosen(uri: Uri) {
        val taken =
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
        if (!taken) {
            Toast.makeText(this, R.string.settings_background_image_failed, Toast.LENGTH_LONG).show()
            return
        }
        releasePreviousImageGrant(uri)
        update { it.copy(backgroundImageUri = uri.toString()) }
    }

    private fun releasePreviousImageGrant(replacement: Uri) {
        val previous = selectedImageUri?.let(Uri::parse) ?: return
        if (previous == replacement) return
        runCatching {
            contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun applicationLabelOrDefault(packageName: String?): String {
        if (packageName.isNullOrEmpty()) return getString(R.string.settings_none_selected)
        return runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)) }
            .getOrNull()
            ?.toString() ?: packageName
    }

    private fun showApplicationPicker(isMap: Boolean) {
        val role = if (isMap) CompanionRole.MAP else CompanionRole.MEDIA
        val applications = ServiceLocator.companionLauncher.listApplications(role)
        val labels = applications.map { it.label }.toTypedArray<CharSequence>()
        ServiceLocator.appearanceManager
            .dialogBuilder(this)
            .setTitle(if (isMap) R.string.settings_map_app else R.string.settings_media_app)
            .setItems(labels) { _, index -> onApplicationChosen(isMap, applications[index]) }
            .show()
    }

    private fun onApplicationChosen(
        isMap: Boolean,
        application: InstalledApplication,
    ) {
        update {
            if (isMap) {
                it.copy(selectedMapPackage = application.packageName)
            } else {
                it.copy(selectedMediaPackage = application.packageName)
            }
        }
    }
}
