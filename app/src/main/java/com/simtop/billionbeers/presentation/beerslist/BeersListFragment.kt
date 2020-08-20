package com.simtop.billionbeers.presentation.beerslist


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.simtop.billionbeers.R
import com.simtop.billionbeers.appComponent
import com.simtop.billionbeers.databinding.FragmentListBeersBinding
import com.simtop.billionbeers.presentation.MainActivity
import javax.inject.Inject

class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    //TODO: Decide if they will share the view model or not
    private val beersViewModel by activityViewModels<BeersViewModel> { viewModelFactory }

    private lateinit var fragmentListBeersBinding: FragmentListBeersBinding


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appComponent.inject(this)

        val binding = FragmentListBeersBinding.bind(view)
        fragmentListBeersBinding = binding

        (requireActivity() as MainActivity).setupToolbar("FirstFragment", false)

        binding.buttonFirst.setOnClickListener {
            val action =
                BeersListFragmentDirections.actionFirstFragmentToSecondFragment(
                    "From FirstFragment"
                )
            findNavController().navigate(action)
        }
    }

}