package com.unishuttle.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unishuttle.app.data.local.entity.CachedRouteGeometryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteGeometryDao {

    @Query("SELECT * FROM cached_route_geometries WHERE routeId = :routeId LIMIT 1")
    suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity?

    @Query("SELECT * FROM cached_route_geometries")
    suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity>

    @Query("SELECT * FROM cached_route_geometries")
    fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity)

    @Query("DELETE FROM cached_route_geometries WHERE routeId = :routeId")
    suspend fun deleteRouteGeometry(routeId: String)

    @Query("DELETE FROM cached_route_geometries")
    suspend fun clearAll()
}
