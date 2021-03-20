package com.simtop.feature.beerdetail.presentation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.observe
import com.simtop.billionbeers.core.showToast
import com.simtop.feature.beerdetail.R
import com.simtop.feature.beerdetail.databinding.FragmentDetailBeerBinding
import com.simtop.feature.beerdetail.presentation.navigation.BeerDetailNavigation
import com.simtop.feature.beerdetail.presentation.navigation.BeerDetailNavigationArgs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BeerDetailFragment : Fragment(R.layout.fragment_detail_beer) {

    private var _beersDetailFragmentBinding: FragmentDetailBeerBinding? = null
    private val beersDetailFragmentBinding get() = _beersDetailFragmentBinding!!

    @Inject
    lateinit var beerDetailNavigationArgs: BeerDetailNavigationArgs

    @Inject
    lateinit var navigatior: BeerDetailNavigation

    @Inject lateinit var beerViewModelAssistedFactory: BeerDetailViewModel.AssistedFactory

    lateinit var assistedBeer : Beer

    private val beersViewModel: BeerDetailViewModel by viewModels {
        BeerDetailViewModel.provideFactory(
            beerViewModelAssistedFactory, assistedBeer
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Setting view binding for Fragments
        val binding = FragmentDetailBeerBinding.bind(view)
        _beersDetailFragmentBinding = binding

        val args = beerDetailNavigationArgs.getBeerDetailArgs(this)
        assistedBeer = args.beer

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
        navigatior.fromBeersListToBeerDetail(this)
    }

    private fun updateAvailability() {
        val beer = beersViewModel.beerDetailViewState.value
        if (beer is BeersDetailViewState.Success) beersViewModel.updateAvailability(beer.result)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _beersDetailFragmentBinding = null
    }
}