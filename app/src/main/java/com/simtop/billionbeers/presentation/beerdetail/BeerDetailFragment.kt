package com.simtop.billionbeers.presentation.beerdetail

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Button
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.simtop.billionbeers.R
import com.simtop.billionbeers.appComponent
import com.simtop.billionbeers.core.showToast
import com.simtop.billionbeers.databinding.FragmentDetailBeerBinding
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.presentation.beerslist.BeersViewModel
import javax.inject.Inject


class BeerDetailFragment : Fragment(R.layout.fragment_detail_beer) {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val beersViewModel by activityViewModels<BeersViewModel> { viewModelFactory }

    private lateinit var beersDetailFragmentBinding: FragmentDetailBeerBinding

    private val args: BeerDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appComponent.inject(this)

        //Setting view binding for Fragments
        val binding = FragmentDetailBeerBinding.bind(view)
        beersDetailFragmentBinding = binding


        (requireActivity() as MainActivity).setupToolbar("SecondFragment", true)

        //More than one way to do this, for this simple case with getting the beer from ViewModel was enough
        binding.textviewBeerDetail.text = args.myArg.toString()//beersViewModel.detailBeer.toString()

        binding.buttonSecond.setOnClickListener {
            requireActivity().showToast("change availability")
        }
    }
}