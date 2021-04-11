package com.simtop.feature.beerslist
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.dynamicfeatures.DynamicExtras
import androidx.navigation.dynamicfeatures.DynamicInstallMonitor
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.databinding.FragmentListBeersBinding
import com.simtop.feature.beerslist.navigation.BeerListNavigation
import com.simtop.presentation_utils.adapters.BeersAdapter
import com.simtop.presentation_utils.core.observe
import com.simtop.presentation_utils.core.showToast


import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    private val beersViewModel : BeersListViewModel by viewModels()

    private var _fragmentListBeersBinding: FragmentListBeersBinding? = null
    private val fragmentListBeersBinding get() = _fragmentListBeersBinding!!

    lateinit var beersAdapter: BeersAdapter

    @Inject
    lateinit var navigatior: BeerListNavigation


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
                fragmentListBeersBinding.progressBar.visibility = VISIBLE
                fragmentListBeersBinding.beersRecyclerview.visibility = GONE
                fragmentListBeersBinding.emptyState.visibility = GONE
            }
            BeersListViewState.EmptyState -> {
                fragmentListBeersBinding.beersRecyclerview.visibility = GONE
                fragmentListBeersBinding.emptyState.visibility = VISIBLE
                fragmentListBeersBinding.progressBar.visibility = GONE
            }
        }
    }

    private fun treatSuccess(list: List<Beer>) {

        beersAdapter.submitList(list)

        fragmentListBeersBinding.progressBar.visibility = GONE
        fragmentListBeersBinding.beersRecyclerview.visibility = VISIBLE
        fragmentListBeersBinding.emptyState.visibility = GONE
    }

    private fun onBeerClicked(beer: Beer) {
        val installMonitor = DynamicInstallMonitor()

        navigateToInfo(beer, installMonitor)

        if (installMonitor.isInstallRequired) {
            fragmentListBeersBinding.progressBar.visibility = VISIBLE

            installMonitor.status.observe(
                viewLifecycleOwner,
                object : Observer<SplitInstallSessionState> {
                    override fun onChanged(sessionState: SplitInstallSessionState?) {
                        when (sessionState?.status()) {
                            SplitInstallSessionStatus.INSTALLED -> {
                                fragmentListBeersBinding.progressBar.visibility = GONE
                                //navigateToInfo(beer, installMonitor)
                            }
                            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> requestInfoInstallConfirmation(sessionState)
                            SplitInstallSessionStatus.FAILED -> showInfoInstallFailed()
                            SplitInstallSessionStatus.CANCELED -> showInfoInstallCanceled()
                        }

                        sessionState?.let {
                            if (it.hasTerminalStatus()) {
                                installMonitor.status.removeObserver(this)
                            }
                        }
                    }

                })
        }

        //navigateToInfo(beer, installMonitor)
//       navigatior.fromBeersListToBeerDetail(beer, this)

    }

    private fun navigateToInfo(beer: Beer, installMonitor: DynamicInstallMonitor) {
        navigatior.fromBeersListToBeerDetail(beer,this, installMonitor)
    }

    private fun treatError(exception: String?) {
        if (fragmentListBeersBinding.beersRecyclerview.visibility != VISIBLE) beersViewModel.showEmptyState()
        exception?.let { requireActivity().showToast(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _fragmentListBeersBinding = null
    }

    private fun requestInfoInstallConfirmation(sessionState: SplitInstallSessionState) {
        fragmentListBeersBinding.progressBar.visibility = GONE

        startIntentSenderForResult(
            sessionState.resolutionIntent()!!.intentSender,
            INSTALL_REQUEST_CODE,
            null, 0, 0, 0, null
        )
    }

    private fun showInfoInstallFailed() {
        fragmentListBeersBinding.progressBar.visibility = GONE
        Toast.makeText(context, R.string.installation_failed, Toast.LENGTH_SHORT).show()
    }

    private fun showInfoInstallCanceled() {
        fragmentListBeersBinding.progressBar.visibility = GONE
        Toast.makeText(context, R.string.installation_cancelled, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == INSTALL_REQUEST_CODE) {
            if(resultCode == Activity.RESULT_CANCELED) {
                showInfoInstallCanceled()
            }
        }
    }

    companion object {
        const val INSTALL_REQUEST_CODE = 100
    }
}