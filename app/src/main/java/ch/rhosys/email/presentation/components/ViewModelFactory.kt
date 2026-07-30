package ch.rhosys.email.presentation.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable

/** Manual-DI ViewModel construction (decision #76: no DI framework). */
class LambdaViewModelFactory<T : ViewModel>(private val create: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}

@Composable
inline fun <reified T : ViewModel> rememberViewModel(noinline create: () -> T): T =
    viewModel(factory = LambdaViewModelFactory(create))
