package com.simtop.billionbeers.presentation.beerslist


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.simtop.billionbeers.R
import com.simtop.billionbeers.appComponent
import com.simtop.billionbeers.core.ViewState
import com.simtop.billionbeers.core.observe
import com.simtop.billionbeers.core.showToast
import com.simtop.billionbeers.databinding.FragmentListBeersBinding
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.presentation.MainActivity
import javax.inject.Inject

class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    //TODO: Decide if they will share the view model or not
    private val beersViewModel by activityViewModels<BeersViewModel> { viewModelFactory }

    private lateinit var fragmentListBeersBinding: FragmentListBeersBinding

    lateinit var beersAdapter: BeersAdapter


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appComponent.inject(this)

        val binding = FragmentListBeersBinding.bind(view)
        fragmentListBeersBinding = binding

        (requireActivity() as MainActivity).setupToolbar("FirstFragment", false)

        beersViewModel.getAllBeers()

        observe(beersViewModel.myViewState, { viewState -> viewState?.let { treatViewState(viewState) } })

    }

    private fun treatViewState(viewState: ViewState<Exception, List<Beer>>) {
        when (viewState) {
            is ViewState.Result -> viewState.result.either(::treatError, ::treatSuccess)
            ViewState.Loading -> {
                requireActivity().showToast("WIP Loading")
            }
            ViewState.EmptyState -> {
                requireActivity().showToast("WIP Empty")
            }
        }
    }

    private fun treatSuccess(list: List<Beer>) {
        beersAdapter = BeersAdapter(
            items = list.toMutableList(),
            listener = ::onBeerClicked
        )
        fragmentListBeersBinding.beersRecyclerview.apply {
            adapter = beersAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun onBeerClicked(beer: Beer) {
        requireActivity().showToast(beer.toString())
        beersViewModel.saveBeerDetail(beer)

        val action = BeersListFragmentDirections.actionBeersListFragmentToBeerDetailFragment(beer)
        findNavController().navigate(action)
    }

    private fun treatError(exception: Exception) {
        exception.message?.let { requireActivity().showToast(it) }
    }
}