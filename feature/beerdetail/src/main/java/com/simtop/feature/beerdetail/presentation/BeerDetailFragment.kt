package com.simtop.feature.beerdetail.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.feature.beerdetail.R
import com.simtop.feature.beerdetail.presentation.di.DaggerFeatureDetailComponent
import com.simtop.presentation_utils.core.showToast
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject
import com.simtop.core.core.CommonUiState

class BeerDetailFragment : Fragment() {

    private val args: BeerDetailFragmentArgs by navArgs()

    @Inject lateinit var beerViewModelAssistedFactory: BeerDetailViewModel.AssistedFactory

    lateinit var assistedBeer : Beer

    private val beersViewModel: BeerDetailViewModel by viewModels {
        BeerDetailViewModel.provideFactory(
            beerViewModelAssistedFactory, assistedBeer
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val viewState by beersViewModel.beerDetailViewState.collectAsState()

                when (val state = viewState) {
                    is CommonUiState.Success -> {
                        ComposeBeerDetail(
                            beer = state.data,
                            onBackClick = { findNavController().popBackStack() },
                            onToggleAvailability = { beersViewModel.updateAvailability(state.data) }
                        )
                    }
                    is CommonUiState.Error -> {
                        // Show error state or toast
                        state.message?.let { requireActivity().showToast(it) }
                    }
                    CommonUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initInject()
        super.onViewCreated(view, savedInstanceState)
        assistedBeer = args.myArg
    }

    private fun initInject() {
        DaggerFeatureDetailComponent.factory().create(
            EntryPointAccessors.fromApplication(requireContext(), DynamicDependencies::class.java)
        ).inject(this)
    }
}