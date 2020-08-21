package com.simtop.billionbeers.presentation.beerdetail

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.simtop.billionbeers.R
import com.simtop.billionbeers.appComponent
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.core.ViewState
import com.simtop.billionbeers.core.observe
import com.simtop.billionbeers.core.showToast
import com.simtop.billionbeers.databinding.FragmentDetailBeerBinding
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.presentation.beerslist.BeersViewModel
import javax.inject.Inject


class BeerDetailFragment : Fragment(R.layout.fragment_detail_beer) {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val beersViewModel by activityViewModels<BeersViewModel> { viewModelFactory }

    private lateinit var beersDetailFragmentBinding: FragmentDetailBeerBinding

    private val args: BeerDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appComponent.inject(this)

        //Setting view binding for Fragments
        val binding = FragmentDetailBeerBinding.bind(view)
        beersDetailFragmentBinding = binding


        (requireActivity() as MainActivity).setupToolbar("SecondFragment", true)

        observe(beersViewModel.detailBeer, { detailBeer -> detailBeer?.let { treatViewState(it) } })

        observe(
            beersViewModel.myViewState,
            { myViewState -> myViewState?.let { treatViewState2(it) } })


        beersDetailFragmentBinding.buttonSecond.setOnClickListener {
            beersViewModel.updateAvailability()
        }
    }

    private fun treatViewState2(it: ViewState<Exception, List<Beer>>) {
        when (it) {
            is ViewState.Result -> when (it.result) {
                is Either.Left -> showError(it.result.value)
                is Either.Right -> {
                }
            }
            ViewState.Loading -> {
            }
            ViewState.EmptyState -> {
            }
        }
    }

    private fun showError(value: Exception) {
        value.message?.let { requireActivity().showToast(it) }
    }

    private fun treatViewState(beer: Beer) {
        beersDetailFragmentBinding.textviewBeerDetail.text = beer.toString()

    }
}