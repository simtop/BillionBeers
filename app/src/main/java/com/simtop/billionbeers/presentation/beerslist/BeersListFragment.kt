package com.simtop.billionbeers.presentation.beerslist


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.simtop.billionbeers.R
import com.simtop.billionbeers.appComponent
import com.simtop.billionbeers.core.observe
import com.simtop.billionbeers.core.showToast
import com.simtop.billionbeers.databinding.FragmentListBeersBinding
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.presentation.MainActivity
import javax.inject.Inject

class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val beersViewModel by viewModels<BeersListViewModel> { viewModelFactory }

    private lateinit var fragmentListBeersBinding: FragmentListBeersBinding

    lateinit var beersAdapter: BeersAdapter


    override fun onResume() {
        if (beersViewModel.beerListViewState.value is BeersListViewState.EmptyState) beersViewModel.getAllBeers()
        super.onResume()
    }

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

        setUpBeersRecyclerView()

        observe(
            beersViewModel.beerListViewState,
            { viewState -> viewState?.let { treatViewState2(it) } })

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

    private fun treatViewState2(it: BeersListViewState<List<Beer>>) {
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
        val action = BeersListFragmentDirections.actionBeersListFragmentToBeerDetailFragment(beer)
        findNavController().navigate(action)
    }

    private fun treatError(exception: Exception) {
        if (fragmentListBeersBinding.beersRecyclerview.visibility != VISIBLE) beersViewModel.showEmptyState()
        exception.message?.let { requireActivity().showToast(it) }
    }
}