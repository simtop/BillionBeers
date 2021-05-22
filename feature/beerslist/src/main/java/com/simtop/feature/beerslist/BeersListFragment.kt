package com.simtop.feature.beerslist
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.dynamicfeatures.DynamicExtras
import androidx.navigation.dynamicfeatures.DynamicInstallMonitor
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.databinding.FragmentListBeersBinding
import com.simtop.feature.beerslist.navigation.BeerListNavigation
import com.simtop.presentation_utils.adapters.BeersAdapter
import com.simtop.presentation_utils.core.*
import com.simtop.presentation_utils.databinding.FragmentProgressBinding


import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    private val beersViewModel: BeersListViewModel by viewModels()

    private var _fragmentListBeersBinding: FragmentListBeersBinding? = null
    private val fragmentListBeersBinding get() = _fragmentListBeersBinding!!

    private lateinit var dialogProgressBinding: FragmentProgressBinding

    private lateinit var beersAdapter: BeersAdapter

    @Inject
    lateinit var navigator: BeerListNavigation

    override fun onResume() {
        if (beersViewModel.beerListViewState.value is BeersListViewState.EmptyState) beersViewModel.getAllBeers()
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentListBeersBinding.bind(view)
        _fragmentListBeersBinding = binding

        setUpToolbar()

        setUpBeersRecyclerView()

        observe(
            beersViewModel.beerListViewState,
            { viewState -> viewState?.let { treatViewState(it) } })

    }

    private fun setUpToolbar() {
        fragmentListBeersBinding.toolbar.title =
            requireContext().getString(R.string.billion_beers_list)
    }


    private fun setUpBeersRecyclerView() {
        beersAdapter = BeersAdapter(
            listener = ::onBeerClicked
        )
        fragmentListBeersBinding.beersRecyclerview.apply {
            adapter = beersAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun treatViewState(it: BeersListViewState<List<Beer>>) {
        when (it) {
            is BeersListViewState.Success -> treatSuccess(it.result)
            is BeersListViewState.Error -> treatError(it.result)
            BeersListViewState.Loading -> {
                fragmentListBeersBinding.apply {
                    progressBar.visibility = VISIBLE
                    beersRecyclerview.visibility = GONE
                    emptyState.visibility = GONE
                }
            }
            BeersListViewState.EmptyState -> {
                fragmentListBeersBinding.apply {
                    progressBar.visibility = GONE
                    beersRecyclerview.visibility = VISIBLE
                    emptyState.visibility = GONE
                }
            }
        }
    }


    private fun treatSuccess(list: List<Beer>) {

        beersAdapter.submitList(list)

        fragmentListBeersBinding.apply {
            progressBar.visibility = GONE
            beersRecyclerview.visibility = VISIBLE
            emptyState.visibility = GONE
        }
    }

    private fun onBeerClicked(beer: Beer) {
        val installMonitor = DynamicInstallMonitor()
        navigateTo(beer, DynamicExtras(installMonitor))

        dialogProgressBinding = FragmentProgressBinding.inflate(LayoutInflater.from(requireContext()))

        val dialog = getDialog(
            context = requireContext(),
            layout = dialogProgressBinding.root
        )

        if (installMonitor.isInstallRequired) {
            installMonitor.status.observe(
                this,
                object : androidx.lifecycle.Observer<SplitInstallSessionState> {
                    override fun onChanged(sessionState: SplitInstallSessionState) {
                        when (sessionState.status()) {
                            SplitInstallSessionStatus.INSTALLED -> {
                                dialogProgressBinding.progressBar.progress = 100
                                dialog.dismiss()
                                navigateTo(beer, DynamicExtras(installMonitor))
                            }
                            SplitInstallSessionStatus.DOWNLOADED -> {
                                navigateTo(beer, DynamicExtras(installMonitor))
                            }
                            SplitInstallSessionStatus.DOWNLOADING -> {
                                val loadingPercentage =
                                    (100 * sessionState.bytesDownloaded() / sessionState.totalBytesToDownload()).toInt()
                                dialogProgressBinding.progressBar.progress = loadingPercentage
                            }
                            SplitInstallSessionStatus.PENDING -> {
                                dialog.show()
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

    private fun treatError(exception: String?) {
        if (fragmentListBeersBinding.beersRecyclerview.visibility != VISIBLE) beersViewModel.showEmptyState()
        exception?.let { requireActivity().showToast(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _fragmentListBeersBinding = null
    }
}