package com.simtop.feature.beerdetail.presentation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.dynamicfeatures.fragment.ui.AbstractProgressFragment
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.feature.beerdetail.R
import com.simtop.feature.beerdetail.databinding.FragmentDetailBeerBinding
import com.simtop.feature.beerdetail.presentation.di.DaggerFeatureDetailComponent
import com.simtop.feature.beerdetail.presentation.navigation.BeerDetailNavigation
import com.simtop.presentation_utils.core.observe
import com.simtop.presentation_utils.core.showToast
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

@AndroidEntryPoint
class BeerDetailFragment : AbstractProgressFragment(R.layout.fragment_detail_beer) {

    private var _beersDetailFragmentBinding: FragmentDetailBeerBinding? = null
    private val beersDetailFragmentBinding get() = _beersDetailFragmentBinding!!

//    @Inject
//    lateinit var beerDetailNavigationArgs: BeerDetailNavigationArgs

//    @Inject
//    lateinit var navigatior: BeerDetailNavigation

    @Inject lateinit var beerViewModelAssistedFactory: BeerDetailViewModel.AssistedFactory

    lateinit var assistedBeer : Beer

    private val beersViewModel: BeerDetailViewModel by viewModels {
        BeerDetailViewModel.provideFactory(
            beerViewModelAssistedFactory, assistedBeer
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        DaggerFeatureDetailComponent.factory().create(
            EntryPointAccessors.fromApplication(requireContext(), DynamicDependencies::class.java)
        ).inject(this)

        //Setting view binding for Fragments
        val binding = FragmentDetailBeerBinding.bind(view)
        _beersDetailFragmentBinding = binding

//        val args = beerDetailNavigationArgs.getBeerDetailArgs(this)
        assistedBeer = Beer.empty//args.beer

        observe(
            beersViewModel.beerDetailViewState, { beerDetailViewState ->
                beerDetailViewState?.let { treatViewState(it) }
            }
        )

    }

    private fun treatViewState(result: BeersDetailViewState<Beer>) {
        when (result) {
            is BeersDetailViewState.Success -> treatSuccess(result.result)
            is BeersDetailViewState.Error -> showError(result.result)
        }

    }

    private fun showError(exception: String?) {
        exception?.let { requireActivity().showToast(it) }
    }

    private fun treatSuccess(beer: Beer) {
        beersDetailFragmentBinding.singleBeer.bind(beer, ::updateAvailability, ::onBackClicked)

    }

    private fun onBackClicked() {
        //navigatior.fromBeersListToBeerDetail(this)
    }

    private fun updateAvailability() {
        val beer = beersViewModel.beerDetailViewState.value
        if (beer is BeersDetailViewState.Success) beersViewModel.updateAvailability(beer.result)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _beersDetailFragmentBinding = null
    }

    override fun onCancelled() {
        showError("Installation canceled")
    }

    override fun onFailed(errorCode: Int) {
        showError("Installation failed")
    }

    override fun onProgress(status: Int, bytesDownloaded: Long, bytesTotal: Long) {
        val x = (bytesDownloaded.toDouble() * 100 / bytesTotal).toInt()
        showError(x.toString())
    }
}