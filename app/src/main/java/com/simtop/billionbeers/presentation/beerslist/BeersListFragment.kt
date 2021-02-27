package com.simtop.billionbeers.presentation.beerslist
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.R
import com.simtop.billionbeers.core.observe
import com.simtop.billionbeers.core.showToast
import com.simtop.billionbeers.databinding.FragmentListBeersBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    private val beersViewModel : BeersListViewModel by viewModels()

    private var _fragmentListBeersBinding: FragmentListBeersBinding? = null
    private val fragmentListBeersBinding get() = _fragmentListBeersBinding

    lateinit var beersAdapter: BeersAdapter


    override fun onResume() {
        if (beersViewModel.beerListViewState.value is BeersListViewState.EmptyState) beersViewModel.getAllBeers()
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentListBeersBinding.bind(view)
        _fragmentListBeersBinding = binding

        setUpToolbar()

        setUpBeersRecyclerView()

        observe(
            beersViewModel.beerListViewState,
            { viewState -> viewState?.let { treatViewState(it) } })

    }

    private fun setUpToolbar() {
        fragmentListBeersBinding?.toolbar?.title =
            requireContext().getString(R.string.billion_beers_list)
    }


    private fun setUpBeersRecyclerView() {
        beersAdapter = BeersAdapter(
            listener = ::onBeerClicked
        )
        fragmentListBeersBinding?.beersRecyclerview?.apply {
            adapter = beersAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun treatViewState(it: BeersListViewState<List<Beer>>) {
        when (it) {
            is BeersListViewState.Success -> treatSuccess(it.result)
            is BeersListViewState.Error -> treatError(it.result)
            BeersListViewState.Loading -> {
                fragmentListBeersBinding?.progressBar?.visibility = VISIBLE
                fragmentListBeersBinding?.beersRecyclerview?.visibility = GONE
                fragmentListBeersBinding?.emptyState?.visibility = GONE
            }
            BeersListViewState.EmptyState -> {
                fragmentListBeersBinding?.beersRecyclerview?.visibility = GONE
                fragmentListBeersBinding?.emptyState?.visibility = VISIBLE
                fragmentListBeersBinding?.progressBar?.visibility = GONE
            }
        }
    }

    private fun treatSuccess(list: List<Beer>) {

        beersAdapter.submitList(list)

        fragmentListBeersBinding?.progressBar?.visibility = GONE
        fragmentListBeersBinding?.beersRecyclerview?.visibility = VISIBLE
        fragmentListBeersBinding?.emptyState?.visibility = GONE
    }

    private fun onBeerClicked(beer: Beer) {
        val action = BeersListFragmentDirections.actionBeersListFragmentToBeerDetailFragment(beer)
        findNavController().navigate(action)
    }

    private fun treatError(exception: String?) {
        if (fragmentListBeersBinding?.beersRecyclerview?.visibility != VISIBLE) beersViewModel.showEmptyState()
        exception?.let { requireActivity().showToast(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _fragmentListBeersBinding = null
    }
}