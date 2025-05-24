package com.simtop.feature.beerdetail.presentation

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.feature.beerdetail.R
import com.simtop.feature.beerdetail.databinding.FragmentDetailBeerBinding
import com.simtop.feature.beerdetail.presentation.di.DaggerFeatureDetailComponent
import com.simtop.presentation_utils.core.observe
import com.simtop.presentation_utils.core.showToast
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

class BeerDetailFragment : Fragment(R.layout.fragment_detail_beer) {

    private var _beersDetailFragmentBinding: FragmentDetailBeerBinding? = null
    private val beersDetailFragmentBinding get() = _beersDetailFragmentBinding!!

    private val args: BeerDetailFragmentArgs by navArgs()

    @Inject lateinit var beerViewModelAssistedFactory: BeerDetailViewModel.AssistedFactory

    lateinit var assistedBeer : Beer

    private val beersViewModel: BeerDetailViewModel by viewModels {
        BeerDetailViewModel.provideFactory(
            beerViewModelAssistedFactory, assistedBeer
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initInject()
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentDetailBeerBinding.bind(view)

        binding.root.enableEdgeToEdgeXMLFragment()

        _beersDetailFragmentBinding = binding

        assistedBeer = args.myArg

        observe(
            beersViewModel.beerDetailViewState, { beerDetailViewState ->
                beerDetailViewState?.let { treatViewState(it) }
            }
        )

    }

    private fun View.enableEdgeToEdgeXMLFragment() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun initInject() {
        DaggerFeatureDetailComponent.factory().create(
            EntryPointAccessors.fromApplication(requireContext(), DynamicDependencies::class.java)
        ).inject(this)
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
        findNavController().popBackStack()
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