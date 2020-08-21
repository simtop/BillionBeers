package com.simtop.billionbeers.presentation.beerslist


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
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

    private val beersViewModel by activityViewModels<BeersViewModel> { viewModelFactory }

    private lateinit var fragmentListBeersBinding: FragmentListBeersBinding

    lateinit var beersAdapter: BeersAdapter


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appComponent.inject(this)

        val binding = FragmentListBeersBinding.bind(view)
        fragmentListBeersBinding = binding

        (requireActivity() as MainActivity)
            .setupToolbar(
                requireContext()
                    .getString(R.string.billion_beers_list), false
            )

        beersViewModel.getAllBeers()

        observe(beersViewModel.myViewState, { viewState -> viewState?.let { treatViewState(it) } })

    }

    private fun treatViewState(viewState: ViewState<Exception, List<Beer>>) {
        when (viewState) {
            is ViewState.Result -> {
                viewState.result.either(::treatError, ::treatSuccess)
            }
            ViewState.Loading -> {
                fragmentListBeersBinding.progressBar.visibility = VISIBLE
                fragmentListBeersBinding.beersRecyclerview.visibility = GONE
                fragmentListBeersBinding.emptyState.visibility = GONE
            }
            ViewState.EmptyState -> {
                fragmentListBeersBinding.beersRecyclerview.visibility = GONE
                fragmentListBeersBinding.emptyState.visibility = VISIBLE
                fragmentListBeersBinding.progressBar.visibility = GONE
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
        fragmentListBeersBinding.progressBar.visibility = GONE
        fragmentListBeersBinding.beersRecyclerview.visibility = VISIBLE
        fragmentListBeersBinding.emptyState.visibility = GONE
    }

    private fun onBeerClicked(beer: Beer) {
        beersViewModel.saveBeerDetail(beer)

        val action = BeersListFragmentDirections.actionBeersListFragmentToBeerDetailFragment(beer)
        findNavController().navigate(action)
    }

    private fun treatError(exception: Exception) {
        if (fragmentListBeersBinding.beersRecyclerview.visibility != VISIBLE) beersViewModel.showEmptyState()
        exception.message?.let { requireActivity().showToast(it) }
    }
}