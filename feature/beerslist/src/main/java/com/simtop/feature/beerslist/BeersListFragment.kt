package com.simtop.feature.beerslist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.dynamicfeatures.DynamicExtras
import androidx.navigation.dynamicfeatures.DynamicInstallMonitor
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.navigation.BeerListNavigation
import com.simtop.presentation_utils.core.*
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeTitle


import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    private val beersViewModel: BeersListViewModel by viewModels()

    @Inject
    lateinit var navigator: BeerListNavigation

    var progressNumber = 0.0f

    override fun onResume() {
        if (beersViewModel.beerListViewState.value is BeersListViewState.EmptyState) beersViewModel.getAllBeers()
        super.onResume()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                Scaffold(topBar = {
                    TopAppBar(title = { Text(text = requireContext().getString(R.string.billion_beers_list)) })
                }) {

                    val showDialog = remember { mutableStateOf(false) }

                    val prog = remember { mutableStateOf(0.0f) }

                    var dataVisibility = mutableStateOf(false)

                    val viewState by beersViewModel.beerListViewState.observeAsState()
                    //This is an important step to capture the instance of the variable
                    //or the IDE will ask us to cast
                    when (val state = viewState) {
                        BeersListViewState.EmptyState -> {
                            ComposeTitle(name = "Empty State")
                        }
                        is BeersListViewState.Error -> {
                            if (!dataVisibility.value) beersViewModel.showEmptyState()
                            state.result?.let { requireActivity().showToast(it) }
                        }
                        BeersListViewState.Loading -> {
                            LoadingIndicator()
                        }
                        is BeersListViewState.Success -> {
                            dataVisibility.value = true
                            showDialog.value = state.showDialog
                            prog.value = state.progress

                            val beers = state.result
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn {
                                    beers.forEach {
                                        item {
                                            ComposeBeersListItem(beer = it, ::onBeerClicked)
                                        }
                                    }
                                }
                            }
                            if (showDialog.value) {
                                DialogWithProgressBar(setShowDialog = {
                                    showDialog.value = it
                                }, number = prog.value)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onBeerClicked(beer: Beer) {
        val installMonitor = DynamicInstallMonitor()
        navigateTo(beer, DynamicExtras(installMonitor))
        setUpInstallMonitorObserver(installMonitor, beer)
    }

    private fun setUpInstallMonitorObserver(
        installMonitor: DynamicInstallMonitor,
        beer: Beer
    ) {
        if (installMonitor.isInstallRequired) {
            installMonitor.status.observe(
                this,
                object : Observer<SplitInstallSessionState> {
                    override fun onChanged(sessionState: SplitInstallSessionState) {
                        when (sessionState.status()) {
                            SplitInstallSessionStatus.INSTALLED -> {
                                //progress = 1.0f
                                //dialog.dismiss()
                                progressNumber = 1.0f
                                beersViewModel.setProgress(false, 1.0f)
                                navigateTo(beer, DynamicExtras(installMonitor))
                            }
                            SplitInstallSessionStatus.DOWNLOADED -> {
                                navigateTo(beer, DynamicExtras(installMonitor))
                            }
                            SplitInstallSessionStatus.DOWNLOADING -> {
                                val percentage = (100 * sessionState.bytesDownloaded() / sessionState.totalBytesToDownload()).toFloat()
                                progressNumber = percentage/100
                                beersViewModel.setProgress(true, if(progressNumber > 0.25) progressNumber else 0.25f)
                            }
                            SplitInstallSessionStatus.PENDING -> {
                                beersViewModel.setProgress(true, 0.1f)
                            }
                        }
                        if (sessionState.hasTerminalStatus()) {
                            installMonitor.status.removeObserver(this);
                        }
                    }
                })
        }
    }

    fun navigateTo(beer: Beer, extras: DynamicExtras) {
        navigator.fromBeersListToBeerDetail(
            beer,
            this@BeersListFragment,
            extras
        )
    }
}

@Preview
@Composable
fun ProgressBar(progressValue: Float = 0.9f) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        val animatedProgress = animateFloatAsState(
            targetValue = progressValue,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
        ).value

        Spacer(Modifier.height(30.dp))
        Text(
            "Downloading Detail Feature", textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = animatedProgress)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun DialogWithProgressBar(
    setShowDialog: (Boolean) -> Unit,
    number: Float = 0.1f
) {
    Dialog(
        onDismissRequest = { setShowDialog(false) },
        DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = true)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(White, shape = RoundedCornerShape(8.dp))
        ) {
            ProgressBar(number)
        }
    }
}

@Preview
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
    ) {
        CircularProgressIndicator(color = Color.Blue)
    }
}