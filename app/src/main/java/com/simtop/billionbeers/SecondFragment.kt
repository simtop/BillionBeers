package com.simtop.billionbeers

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.simtop.billionbeers.databinding.FragmentSecondBinding


class SecondFragment : Fragment(R.layout.fragment_second) {

    private lateinit var secondFragmentBinding: FragmentSecondBinding

    private val args: SecondFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Setting view binding for Fragments
        val binding = FragmentSecondBinding.bind(view)
        secondFragmentBinding = binding


        (requireActivity() as MainActivity).setupToolbar("SecondFragment", true)


        binding.textviewSecond.text =
                getString(R.string.hello_second_fragment, args.myArg)

        binding.buttonSecond.findViewById<Button>(R.id.button_second).setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
    }
}