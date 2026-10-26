package com.idlix.tv.update

/**
 * Update engine state machine for self-update from GitHub releases.
 *
 * Transitions: Idle -> Checking -> UpToDate | Available -> Downloading ->
 * Downloaded -> (installed via [UpdateInstaller], back to Idle) | Failed.
 * Any failure is fail-closed: surfaces [Failed] with a short reason, never throws.
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Available(
        val version: String,
        val notes: String,
        val assetUrl: String,
        val sizeBytes: Long
    ) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Downloaded(val fileUri: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}
