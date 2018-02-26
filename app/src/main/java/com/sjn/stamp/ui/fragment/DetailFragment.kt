package com.sjn.stamp.ui.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.sjn.stamp.R
import com.sjn.stamp.utils.CompatibleHelper
import com.sjn.stamp.utils.ViewHelper

class DetailFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_detail, container, false).also { view ->
                arguments?.also {
                    it.getString("TITLE")?.let { activity?.title = it }
                    view.findViewById<TextView>(R.id.textView)?.also { textView ->
                        if (CompatibleHelper.hasLollipop()) textView.transitionName = it.getString("TRANS_TITLE")
                        textView.text = it.getString("TITLE")
                    }
                    view.findViewById<ImageView>(R.id.image)?.also { imageView ->
                        if (CompatibleHelper.hasLollipop()) imageView.transitionName = it.getString("TRANS_IMAGE")
                        activity?.let { activity ->
                            ViewHelper.loadAlbumArt(activity, imageView, it.getParcelable("IMAGE_BITMAP"), it.getString("IMAGE_TYPE"), it.getString("IMAGE_URL"), it.getString("IMAGE_TEXT"))
                        }
                    }
                }
            }
}