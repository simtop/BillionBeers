package com.simtop.billionbeers


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.navigation.fragment.findNavController
import com.simtop.billionbeers.databinding.FragmentFirstBinding

class FirstFragment : Fragment(R.layout.fragment_first) {

    private lateinit var firstFragmentBinding: FragmentFirstBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentFirstBinding.bind(view)
        firstFragmentBinding = binding


        (requireActivity() as MainActivity).setupToolbar("FirstFragment", false)


        binding.buttonFirst.setOnClickListener {
            val action =
                FirstFragmentDirections.actionFirstFragmentToSecondFragment(
                    "From FirstFragment"
                )
            findNavController().navigate(action)
        }
    }
}