package com.simtop.billionbeers


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.navigation.fragment.findNavController
import com.simtop.billionbeers.databinding.FragmentListBeersBinding

class BeersListFragment : Fragment(R.layout.fragment_list_beers) {

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