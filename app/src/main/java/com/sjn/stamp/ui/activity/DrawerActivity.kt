package com.sjn.stamp.ui.activity

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import com.github.sjnyag.forceanimateappbarlayout.ForceAnimateAppBarLayout
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.sjn.stamp.R
import com.sjn.stamp.ui.DrawerMenu
import com.sjn.stamp.ui.fragment.media.PagerFragment
import com.sjn.stamp.utils.*
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.multimoon.colorful.CAppCompatActivity
import io.multimoon.colorful.Colorful


abstract class DrawerActivity : CAppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    private var toolbar: Toolbar? = null
    protected var drawer: DrawerHelper.Drawer? = null

    abstract fun onOptionsItemSelected(itemId: Int): Boolean

    open fun setToolbarTitle(title: CharSequence?) = setTitle(title ?: drawer?.selectingDrawerName
    ?: "")

    open fun navigateToBrowser(fragment: Fragment, addToBackStack: Boolean, sharedElements: List<Pair<String, View>> = emptyList()) {
        try {
            if (!addToBackStack) {
                supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            supportFragmentManager.beginTransaction().apply {
                replace(R.id.container, fragment, FRAGMENT_TAG)
                if (addToBackStack) {
                    addToBackStack(null)
                }
                sharedElements.forEach { pair -> addSharedElement(pair.second, pair.first) }
            }.commit()
        } catch (ignored: IllegalStateException) {
            // There's no way to avoid getting this if saveInstanceState has already been called.
        }
    }

    fun expandSlide(): Boolean {
        val slidingUpPanelLayout = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout)
        if (slidingUpPanelLayout != null && slidingUpPanelLayout.panelState != SlidingUpPanelLayout.PanelState.EXPANDED) {
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
            return true
        }
        return false
    }

    fun collapseSlide(): Boolean {
        val slidingUpPanelLayout = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout)
        if (slidingUpPanelLayout != null && slidingUpPanelLayout.panelState != SlidingUpPanelLayout.PanelState.COLLAPSED) {
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            return true
        }
        return false
    }

    fun animateAppbar(title: String?, updateImageView: (activity: Activity, imageView: ImageView) -> Unit) {
        findViewById<Toolbar>(R.id.toolbar)?.apply {
            setTitle(title)
        }
        findViewById<CollapsingToolbarLayout>(R.id.toolbar_layout)?.apply {
            isTitleEnabled = true
            setTitle(title)
        }
        findViewById<ImageView>(R.id.app_bar_image)?.also { imageView ->
            imageView.visibility = View.VISIBLE
            updateImageView(this, imageView)
        }
        findViewById<ForceAnimateAppBarLayout>(R.id.app_bar).run {
            animatedExpand()
        }
    }

    fun updateAppbar() {
        val hasShadow = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) !is PagerFragment
        findViewById<AppBarLayout>(R.id.app_bar).run {
            afterMeasured {
                if (CompatibleHelper.hasLollipop()) {
                    elevation = if (hasShadow) 8F else 0F
                }
            }
        }
        findViewById<CollapsingToolbarLayout>(R.id.toolbar_layout)?.apply {
            isTitleEnabled = false
        }
        findViewById<ImageView>(R.id.app_bar_image)?.apply {
            visibility = View.GONE
        }
    }

    protected fun initializeToolbar() {
        toolbar = findViewById<View>(R.id.toolbar) as Toolbar?
        toolbar?.let {
            it.inflateMenu(R.menu.main)
            setSupportActionBar(it)
            drawer = DrawerHelper.Drawer(this, it, object : DrawerHelper.Drawer.Listener {
                override fun changeFragmentByDrawer(menu: Long) {
                    val drawerMenu = DrawerMenu.of(menu) ?: return
                    navigateToBrowser(drawerMenu.fragment, false)
                    setToolbarTitle(null)
                }
            })
            drawer?.updateColor(
                    ViewHelper.getThemeColor(this, android.R.attr.textColorPrimary, Color.DKGRAY),
                    if (Colorful().getDarkTheme()) Color.parseColor("#484848") else Color.parseColor("#E8E8E8")
            )
            ToolbarColorizeHelper.colorizeToolbar(it, ViewHelper.getThemeColor(this, android.R.attr.textColorPrimary, Color.DKGRAY), this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)
        try {
            CastButtonFactory.setUpMediaRouteButton(applicationContext, menu, R.id.media_route_menu_item)
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
        return true
    }

    override fun onBackStackChanged() {
        drawer?.updateDrawerToggleState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "Activity onCreate")
        if (CompatibleHelper.hasLollipop()) {
            findViewById<View>(android.R.id.content).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        try {
            CastContext.getSharedInstance(this)
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        supportFragmentManager.addOnBackStackChangedListener(this)
    }

    override fun onPause() {
        super.onPause()
        supportFragmentManager.removeOnBackStackChangedListener(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawer?.sync()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawer?.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        drawer?.let {
            if (it.onOptionItemSelected(item)) {
                return true
            }
        }
        if (item != null && item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (item != null) {
            if (!onOptionsItemSelected(item.itemId)) {
                return super.onOptionsItemSelected(item)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        drawer?.let {
            if (it.closeDrawer()) {
                return
            }
        }
        val fragmentManager = supportFragmentManager
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        } else {
            moveTaskToBack(true)
        }
    }

    override fun setTitle(title: CharSequence) {
        super.setTitle(title)
        toolbar?.title = title
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        toolbar?.setTitle(titleId)
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(DrawerActivity::class.java)
        const val FRAGMENT_TAG = "fragment_container"
    }
}
