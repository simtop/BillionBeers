package com.simtop.billionbeers.presentation.beerdetail

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.simtop.billionbeers.R
import com.simtop.billionbeers.appComponent
import com.simtop.billionbeers.core.observe
import com.simtop.billionbeers.core.showToast
import com.simtop.billionbeers.databinding.FragmentDetailBeerBinding
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.presentation.MainActivity
import javax.inject.Inject


class BeerDetailFragment : Fragment(R.layout.fragment_detail_beer) {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val beersViewModel by viewModels<BeerDetailViewModel> { viewModelFactory }

    private lateinit var beersDetailFragmentBinding: FragmentDetailBeerBinding

    private val args: BeerDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appComponent.inject(this)

        //Setting view binding for Fragments
        val binding = FragmentDetailBeerBinding.bind(view)
        beersDetailFragmentBinding = binding

        (requireActivity() as MainActivity).showToolbar(false)

        beersViewModel.setBeer(args.myArg)

        observe(
            beersViewModel.beerDetailViewState,
            { beerDetailViewState -> beerDetailViewState?.let { treatViewState(it) } })

    }

    private fun treatViewState(result: BeersDetailViewState<Beer>) {
        when (result) {
            is BeersDetailViewState.Success -> treatSuccess(result.result)
            is BeersDetailViewState.Error -> showError(result.result)
        }

    }

    private fun showError(value: Exception) {
        value.message?.let { requireActivity().showToast(it) }
    }

    private fun treatSuccess(beer: Beer) {
        beersDetailFragmentBinding.singleBeer.bind(beer, ::updateAvailability, ::onBackClicked)

    }

    private fun onBackClicked() {
        findNavController().popBackStack()
    }

    private fun updateAvailability() {
        val beer = beersViewModel.beerDetailViewState.value
        if (beer is BeersDetailViewState.Success) beersViewModel.updateAvailability(beer.result)
    }
}