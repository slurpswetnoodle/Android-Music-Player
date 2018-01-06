package denis.musicplayer.ui.main.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import denis.musicplayer.R
import denis.musicplayer.ui.base.BaseFragment
import javax.inject.Inject

/**
 * Created by denis on 06/01/2018.
 */
class SelectFragment : BaseFragment(), SelectMvpView {

    companion object {
        fun newInstance(): SelectFragment {
            val args = Bundle()
            val fragment = SelectFragment()
            fragment.arguments = args
            return fragment
        }
    }

    @Inject lateinit var presenter: SelectMvpPresenter<SelectMvpView>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_select, container, false)

        activityComponent?.inject(this)
        presenter.onAttach(this)

        return view
    }

    override fun setUp(view: View?) {

    }
}