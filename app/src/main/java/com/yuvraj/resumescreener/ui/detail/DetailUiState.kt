package com.yuvraj.resumescreener.ui.detail

import com.yuvraj.resumescreener.data.local.ScreeningEntity

data class DetailUiState(
    val entity: ScreeningEntity? = null,
    val rawJson: String = "",
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val deleted: Boolean = false,
    val confirmingDelete: Boolean = false,
)
