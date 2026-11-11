/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.devices.ant_plus

import android.content.Context
import android.os.Bundle
import android.os.RemoteException
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.channel.AntChannelProvider
import com.dsi.ant.channel.ChannelNotAvailableException
import com.dsi.ant.channel.ipc.IAntChannelCommunicator
import com.dsi.ant.channel.ipc.IAntChannelProviderCommunicator
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Helper utility for acquiring ANT+ channels on Predefined Network slot 1 (ANT_PLUS)
 * using android_antlib_4-16-0.aar (REQ-CON-032.1, ATT-5084).
 *
 * Avoids Android_ANTRadioService rejecting RF frequency 57 on PredefinedNetwork.PUBLIC (slot 0)
 * and avoiding raw network key rejection.
 */
object AntChannelHelper {
    /** Predefined network number for ANT+ profiles (slot 1) */
    const val PREDEFINED_NETWORK_ANT_PLUS = 1

    /** Standard ANT+ Bike Radar device profile constants */
    const val BIKE_RADAR_DEVICE_TYPE = 40 // 0x28
    const val BIKE_RADAR_RF_FREQUENCY = 57 // 2457 MHz
    const val BIKE_RADAR_CHANNEL_PERIOD = 4084 // ~8.0235 Hz (counts)

    /** Standard ANT+ Bike Lights device profile constants (REQ-CON-033.1, ATT-5103) */
    const val BIKE_LIGHT_DEVICE_TYPE = 35 // 0x23
    const val BIKE_LIGHT_RF_FREQUENCY = 57 // 2457 MHz
    const val BIKE_LIGHT_CHANNEL_PERIOD = 4084 // ~8.0235 Hz (counts)

    fun interface ChannelAcquirer {
        @Throws(ChannelNotAvailableException::class, RemoteException::class)
        fun acquire(context: Context, provider: AntChannelProvider): AntChannel
    }

    private val defaultAcquirer = ChannelAcquirer { context, provider ->
        val providerField: Field = AntChannelProvider::class.java.getDeclaredField("mAntChannelProvider")
        providerField.isAccessible = true
        val communicator = providerField.get(provider) as? IAntChannelProviderCommunicator
            ?: throw ChannelNotAvailableException(com.dsi.ant.channel.ChannelNotAvailableReason.UNKNOWN)

        val bundle = Bundle()
        val channelComm: IAntChannelCommunicator? = communicator.acquireChannel(
            context,
            PREDEFINED_NETWORK_ANT_PLUS,
            null,
            null,
            bundle
        )

        if (channelComm == null) {
            bundle.classLoader = ChannelNotAvailableException::class.java.classLoader
            val error = bundle.getParcelable<ChannelNotAvailableException>("error")
            if (error != null) {
                throw error
            }
            throw ChannelNotAvailableException(com.dsi.ant.channel.ChannelNotAvailableReason.UNKNOWN)
        }

        val constructor: Constructor<AntChannel> = AntChannel::class.java.getDeclaredConstructor(IAntChannelCommunicator::class.java)
        constructor.isAccessible = true
        val antChannel = constructor.newInstance(channelComm)

        try {
            val voteMethod: Method = AntChannelProvider::class.java.getDeclaredMethod("voteEventBufferToDefault", AntChannel::class.java)
            voteMethod.isAccessible = true
            voteMethod.invoke(null, antChannel)
        } catch (_: Exception) {
            // Best effort event buffer voting
        }

        antChannel
    }

    private var currentAcquirer: ChannelAcquirer = defaultAcquirer

    @JvmStatic
    fun setChannelAcquirerForTesting(acquirer: ChannelAcquirer?) {
        currentAcquirer = acquirer ?: defaultAcquirer
    }

    @JvmStatic
    @Throws(ChannelNotAvailableException::class, RemoteException::class)
    fun acquireAntPlusChannel(context: Context, provider: AntChannelProvider): AntChannel {
        return currentAcquirer.acquire(context, provider)
    }
}
