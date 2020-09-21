package com.simtop.billionbeers.presentation.beerslist

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.simtop.billionbeers.R
import com.simtop.billionbeers.appComponent
import com.simtop.billionbeers.core.showToast
import com.simtop.billionbeers.databinding.FragmentListBeersBinding
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.presentation.beerslist.paging.BeersLoadStateAdapter
import com.simtop.billionbeers.presentation.beerslist.paging.PagedBeersAdapter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import javax.inject.Inject

class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val beersViewModel by viewModels<BeersListViewModel> { viewModelFactory }

    private lateinit var fragmentListBeersBinding: FragmentListBeersBinding

    lateinit var beersAdapter: PagedBeersAdapter

    @ExperimentalPagingApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appComponent.inject(this)

        val binding = FragmentListBeersBinding.bind(view)
        fragmentListBeersBinding = binding

        setUpBeersRecyclerView()

        lifecycleScope.launch {
            beersViewModel.getData().collectLatest {
                beersAdapter.submitData(it)
            }
        }

        initAdapter()

        lifecycleScope.launch {
            beersAdapter.loadStateFlow
                // Only emit when REFRESH LoadState for RemoteMediator changes.
                .distinctUntilChangedBy { it.refresh }
                // Only react to cases where Remote REFRESH completes i.e., NotLoading.
                .filter { it.refresh is LoadState.NotLoading }
                .collect { fragmentListBeersBinding.beersRecyclerview.scrollToPosition(0) }
        }

        fragmentListBeersBinding.retryButton.setOnClickListener { beersAdapter.retry() }

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        (requireActivity() as MainActivity)
            .setupToolbar(
                requireContext()
                    .getString(R.string.billion_beers_list), false
            )
    }

    private fun initAdapter() {
        fragmentListBeersBinding.beersRecyclerview.adapter = beersAdapter.withLoadStateFooter(
            footer = BeersLoadStateAdapter { beersAdapter.retry() }
        )

        beersAdapter.addLoadStateListener { loadState ->
            // Only show the list if refresh succeeds.
            fragmentListBeersBinding.beersRecyclerview.isVisible =
                loadState.source.refresh is LoadState.NotLoading
            // Show loading spinner during initial load or refresh.
            fragmentListBeersBinding.progressBar.isVisible =
                loadState.source.refresh is LoadState.Loading
            // Show the retry state if initial load or refresh fails.
            fragmentListBeersBinding.retryButton.isVisible =
                loadState.source.refresh is LoadState.Error

            // Toast on any error, regardless of whether it came from RemoteMediator or PagingSource
            val errorState = loadState.source.append as? LoadState.Error
                ?: loadState.source.prepend as? LoadState.Error
                ?: loadState.append as? LoadState.Error
                ?: loadState.prepend as? LoadState.Error
            errorState?.let {
                requireActivity().showToast("${it.error}", Toast.LENGTH_LONG)
            }
        }

    }


    private fun setUpBeersRecyclerView() {
        beersAdapter = PagedBeersAdapter(
            listener = ::onBeerClicked,
        )
        fragmentListBeersBinding.beersRecyclerview.apply {
            adapter = beersAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun onBeerClicked(beer: Beer) {
        val action = BeersListFragmentDirections.actionBeersListFragmentToBeerDetailFragment(beer)
        findNavController().navigate(action)
    }
}



