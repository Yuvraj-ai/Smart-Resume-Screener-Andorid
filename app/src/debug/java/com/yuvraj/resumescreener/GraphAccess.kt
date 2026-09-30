package com.yuvraj.resumescreener

import com.yuvraj.resumescreener.data.local.ScreeningDao
import com.yuvraj.resumescreener.data.repository.ScreeningRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Exposes the app's own repository and DAO so instrumented tests can seed and
 * inspect the same graph the screens use.
 *
 * Lives in the debug source set rather than androidTest on purpose. Hilt
 * generates EntryPoint implementations during the app's own compilation, so an
 * interface declared only in androidTest arrives too late: the generated
 * Singleton component would not implement it and every test would fail casting.
 *
 * Debug-only, so it is absent from release builds entirely. No Hilt testing
 * dependency is needed, and the tests exercise the production wiring rather
 * than a graph the app never ships.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GraphAccess {
    fun repository(): ScreeningRepository
    fun dao(): ScreeningDao
}
