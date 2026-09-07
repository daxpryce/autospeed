package io.pryce.android.autospeed

import android.app.Application

/**
 * Autospeed's application class. Holds no framework-launching, DI-container, or background-job
 * machinery: [ServiceLocator] provides the small number of shared singletons Autospeed needs
 * (design section 5.2 explicitly avoids a dependency-injection framework).
 */
class AutospeedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
