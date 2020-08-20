package com.simtop.billionbeers.presentation.beerdetail

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Button
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.simtop.billionbeers.R
import com.simtop.billionbeers.databinding.FragmentDetailBeerBinding
import com.simtop.billionbeers.presentation.MainActivity


class BeerDetailFragment : Fragment(R.layout.fragment_detail_beer) {

    private lateinit var beersDetailFragmentBinding: FragmentDetailBeerBinding

    private val args: BeerDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Setting view binding for Fragments
        val binding = FragmentDetailBeerBinding.bind(view)
        beersDetailFragmentBinding = binding


        (requireActivity() as MainActivity).setupToolbar("SecondFragment", true)


        binding.textviewSecond.text =
                getString(R.string.hello_second_fragment, args.myArg)

        binding.buttonSecond.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
    }
}