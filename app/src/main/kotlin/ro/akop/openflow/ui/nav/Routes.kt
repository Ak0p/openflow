package ro.akop.openflow.ui.nav

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object RecordRoute

@Serializable
data class DetailRoute(val transcriptId: String)

@Serializable
data object SettingsRoute
