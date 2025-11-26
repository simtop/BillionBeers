package com.simtop.feature.beerslist

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
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
import androidx.navigation.fragment.findNavController
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.BeerListNavigation
import com.simtop.presentation_utils.core.*
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeTitle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.simtop.core.core.CommonUiState

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    private val beersViewModel: BeersListViewModel by viewModels()

    @Inject
    lateinit var navigator: BeerListNavigation

    var progressNumber = 0.0f

    override fun onResume() {
        if (beersViewModel.beerListViewState.value is CommonUiState.Empty) beersViewModel.getAllBeers()
        super.onResume()
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UnrememberedMutableState")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                Scaffold(topBar = {
                    TopAppBar(title = { Text(text = requireContext().getString(com.simtop.core.R.string.billion_beers_list)) })
                }) { paddingValues->

                    val showDialog = rememberSaveable { mutableStateOf(false) }
                    val prog = rememberSaveable { mutableFloatStateOf(0.0f) }
                    val dataVisibility = rememberSaveable { mutableStateOf(false) }

                    val viewState by beersViewModel.beerListViewState.collectAsState()
                    //This is an important step to capture the instance of the variable
                    //or the IDE will ask us to cast

                    when (val state = viewState) {
                        CommonUiState.Empty -> {
                            ComposeTitle(name = "Empty State")
                        }

                        is CommonUiState.Error -> {
                            if (!dataVisibility.value) beersViewModel.showEmptyState()
                            state.message?.let { requireActivity().showToast(it) }
                        }

                        CommonUiState.Loading -> {
                            LoadingIndicator()
                        }

                        is CommonUiState.Success -> {
                            dataVisibility.value = true
                            showDialog.value = state.data.showDialog
                            prog.value = state.data.progress

                            val beers = state.data.beers
                            Box(modifier = Modifier.fillMaxSize().padding(
                                paddingValues
                            )) {
                                val listState = rememberLazyListState()
                                
                                InfiniteListHandler(
                                    listState = listState,
                                    isLoadingNextPage = state.data.isLoadingNextPage,
                                    onLoadMore = { beersViewModel.onScrollToBottom() }
                                )

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.testTag("beer_list"),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(beers.count()) { index ->
                                        ComposeBeersListItem(beer = beers[index], onClick = ::onBeerClicked)
                                    }
                                    
                                    if (state.data.isLoadingNextPage) {
                                        item(key = "loading_footer") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(32.dp),
                                                    strokeWidth = 3.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (showDialog.value) {
                                DialogWithProgressBar(setShowDialog = {
                                    showDialog.value = it
                                }, number = prog.floatValue)
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
                                val totalBytes = sessionState.totalBytesToDownload()
                                val downloadedBytes = sessionState.bytesDownloaded()

                                val progress = if (totalBytes > 0L) {
                                    downloadedBytes.toFloat() / totalBytes.toFloat()
                                } else {
                                    0f
                                }

                                progressNumber = progress
                                beersViewModel.setProgress(
                                    true,
                                    if (progressNumber > 0.25f) progressNumber else 0.25f
                                )
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
            this@BeersListFragment.findNavController(),
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
        LinearProgressIndicator(progress = {
            animatedProgress
        })
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