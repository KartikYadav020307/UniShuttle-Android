package com.unishuttle.app.domain.model

data class ShuttleStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val order: Int = 0,
) {
    /**
     * Checks if this stop serves [route], using [CampusRoute.stopIds] as authoritative truth.
     */
    fun servesCampusRoute(route: CampusRoute?): Boolean =
        route == null || id in route.stopIds

    /**
     * Checks if this stop is unassigned across [allRoutes], using [CampusRoute.stopIds] as authoritative truth.
     */
    fun isUnassigned(allRoutes: List<CampusRoute>): Boolean =
        allRoutes.none { id in it.stopIds }
}
