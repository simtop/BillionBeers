package com.simtop.billionbeers.presentation.beerdetail

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.simtop.billionbeers.R
import com.simtop.billionbeers.core.observe
import com.simtop.billionbeers.core.showToast
import com.simtop.billionbeers.databinding.FragmentDetailBeerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BeerDetailFragment : Fragment(R.layout.fragment_detail_beer) {

    private val beersViewModel : BeerDetailViewModel by viewModels()

    private var _beersDetailFragmentBinding: FragmentDetailBeerBinding? = null
    private val beersDetailFragmentBinding get() = _beersDetailFragmentBinding

    private val args: BeerDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Setting view binding for Fragments
        val binding = FragmentDetailBeerBinding.bind(view)
        _beersDetailFragmentBinding = binding

        beersViewModel.setBeer(args.myArg)

        observe(
            beersViewModel.beerDetailViewState,
            { beerDetailViewState -> beerDetailViewState?.let { treatViewState(it) } })

    }

    private fun treatViewState(result: BeersDetailViewState<com.simtop.beerdomain.domain.models.Beer>) {
        when (result) {
            is BeersDetailViewState.Success -> treatSuccess(result.result)
            is BeersDetailViewState.Error -> showError(result.result)
        }

    }

    private fun showError(exception: String?) {
        exception?.let { requireActivity().showToast(it) }
    }

    private fun treatSuccess(beer: com.simtop.beerdomain.domain.models.Beer) {
        beersDetailFragmentBinding?.singleBeer?.bind(beer, ::updateAvailability, ::onBackClicked)

    }

    private fun onBackClicked() {
        findNavController().popBackStack()
    }

    private fun updateAvailability() {
        val beer = beersViewModel.beerDetailViewState.value
        if (beer is BeersDetailViewState.Success) beersViewModel.updateAvailability(beer.result)
    }
}