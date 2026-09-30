// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the device says about its network and battery, as polling needs it. */
public interface DeviceConditions {
    /** Whether a network is up; polling stops without one and resumes when one returns, not on a timer. */
    public val online: StateFlow<Boolean>

    public val metered: Boolean

    public val powerSave: Boolean
}

/**
 * The default network as the system reports it. Only "reaches the internet" is asked, not "validated": a
 * server on the local network is reachable where no validation check ever passes.
 */
@Singleton
internal class AndroidDeviceConditions @Inject constructor(@ApplicationContext context: Context) : DeviceConditions {
    private val connectivity = requireNotNull(context.getSystemService<ConnectivityManager>())
    private val power = requireNotNull(context.getSystemService<PowerManager>())
    private val state = MutableStateFlow(
        connectivity.getNetworkCapabilities(connectivity.activeNetwork).reachesInternet(),
    )

    init {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    state.value = capabilities.reachesInternet()
                }

                override fun onLost(network: Network) {
                    state.value = false
                }
            },
        )
    }

    override val online: StateFlow<Boolean> = state

    override val metered: Boolean get() = connectivity.isActiveNetworkMetered

    override val powerSave: Boolean get() = power.isPowerSaveMode

    private fun NetworkCapabilities?.reachesInternet() =
        this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
