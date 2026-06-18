package com.abk.kernel.ui.navigation3

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for Navigation3.
 * MainRoute 是 tab 容器（tab 之间用内部状态切换，不走导航）。
 * 子页面（如 ThemeSettings）作为独立 Route push 到 back stack。
 */
sealed interface Route : NavKey, Parcelable {

    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object ThemeSettings : Route

    @Parcelize
    @Serializable
    data object AppProfileTemplates : Route

    @Parcelize
    @Serializable
    data object ManagerTools : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object OpenSourceLicenses : Route

    @Parcelize
    @Serializable
    data object ExtensionManager : Route

    // 以后可按需添加更多子页面 Route
}
