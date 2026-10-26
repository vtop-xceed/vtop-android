package dev.vtoppp.vtop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import dev.vtoppp.vtop.data.AutoCaptchaClient
import dev.vtoppp.vtop.data.CredentialVault
import dev.vtoppp.vtop.data.SessionStore
import dev.vtoppp.vtop.data.VtopClient
import dev.vtoppp.vtop.ui.home.HomeViewModel
import dev.vtoppp.vtop.ui.login.LoginViewModel

class LoginVmFactory(
    private val vault: CredentialVault,
    private val autoCaptcha: AutoCaptchaClient,
    private val sessionStore: SessionStore? = null,
) : ViewModelProvider.Factory {
    // T16.4: SavedStateHandle must come from the CreationExtras the owning
    // ViewModelStore supplies; falling back to a fresh handle keeps direct
    // constructions (tests) compile-safe without persistence.
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        LoginViewModel(vault, autoCaptcha, sessionStore, extras.createSavedStateHandle()) as T

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LoginViewModel(vault, autoCaptcha, sessionStore, SavedStateHandle()) as T
}

class HomeVmFactory(
    private val client: VtopClient,
    private val authorizedId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(client, authorizedId) as T
}
