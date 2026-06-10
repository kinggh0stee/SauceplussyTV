package com.saucedplussytv.androidtv.browse

import androidx.lifecycle.ViewModel
import com.saucedplussytv.androidtv.authenticate.AuthManager
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient
import com.saucedplussytv.androidtv.client.SocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val client: SaucedplussyTVClient,
    private val socketClient: SocketClient,
    private val authManager: AuthManager
) : ViewModel()
