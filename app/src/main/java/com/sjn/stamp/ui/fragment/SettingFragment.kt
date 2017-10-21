package com.sjn.stamp.ui.fragment

import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.Toast
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.sjn.stamp.R
import com.sjn.stamp.controller.SongController
import com.sjn.stamp.controller.UserSettingController
import com.sjn.stamp.ui.DialogFacade
import com.sjn.stamp.utils.AnalyticsHelper
import com.sjn.stamp.utils.LogHelper
import com.sjn.stamp.utils.MediaRetrieveHelper
import com.sjn.stamp.utils.RealmHelper

class SettingFragment : PreferenceFragmentCompat() {

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        menu!!.clear()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        findPreference("song_db_refresh").onPreferenceClickListener = Preference.OnPreferenceClickListener {
            DialogFacade.createConfirmDialog(activity, R.string.dialog_confirm_song_db_refresh, MaterialDialog.SingleButtonCallback { dialog, which ->
                when (which) {
                    DialogAction.NEGATIVE -> return@SingleButtonCallback
                    DialogAction.POSITIVE -> {
                        AnalyticsHelper.trackSetting(context, "song_db_refresh")
                        Thread(Runnable { SongController(context).refreshAllSongs(MediaRetrieveHelper.allMediaMetadataCompat(context, null)) }).start()
                    }
                    else -> {
                    }
                }
            }, DialogInterface.OnDismissListener { }).show()
            true
        }

        findPreference("song_db_unknown").onPreferenceClickListener = Preference.OnPreferenceClickListener {

            val transaction = fragmentManager.beginTransaction()
            transaction.add(R.id.container, UnknownSongFragment())
            transaction.addToBackStack(null)
            transaction.commit()
            true
        }

        findPreference("import_backup").onPreferenceClickListener = Preference.OnPreferenceClickListener {
            DialogFacade.createConfirmDialog(activity, R.string.dialog_confirm_import, MaterialDialog.SingleButtonCallback { dialog, which ->
                when (which) {
                    DialogAction.NEGATIVE -> return@SingleButtonCallback
                    DialogAction.POSITIVE -> {
                        AnalyticsHelper.trackSetting(context, "import_backup")
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                        intent.type = "*/*"
                        startActivityForResult(intent, REQUEST_BACKUP)
                    }
                    else -> {
                    }
                }
            }, DialogInterface.OnDismissListener { }).show()
            true
        }

        findPreference("export_backup").onPreferenceClickListener = Preference.OnPreferenceClickListener {
            DialogFacade.createConfirmDialog(activity, R.string.dialog_confirm_export, MaterialDialog.SingleButtonCallback { dialog, which ->
                when (which) {
                    DialogAction.NEGATIVE -> return@SingleButtonCallback
                    DialogAction.POSITIVE -> {
                        AnalyticsHelper.trackSetting(context, "export_backup")
                        val progressDialog = ProgressDialog(activity)
                        progressDialog.setMessage(getString(R.string.message_processing))
                        progressDialog.show()
                        RealmHelper.exportBackUp(activity)
                        progressDialog.dismiss()
                    }
                    else -> {
                    }
                }
            }, DialogInterface.OnDismissListener { }).show()
            true
        }

        findPreference("licence").onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AnalyticsHelper.trackSetting(context, "licence")
            DialogFacade.createLicenceDialog(activity).show()
            true
        }

        findPreference("setting_songs_new_song_days").onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
            if (preference is EditTextPreference) {
                preference.text = UserSettingController().newSongDays.toString()
            }
            true
        }
        findPreference("setting_songs_new_song_days").onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            AnalyticsHelper.trackSetting(context, "setting_songs_new_song_days", newValue.toString())
            try {
                val newSongDays = Integer.parseInt(newValue.toString())
                if (newSongDays in 0..999) {
                    UserSettingController().newSongDays = newSongDays
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            false
        }

        findPreference("setting_songs_most_played_song_size").onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
            if (preference is EditTextPreference) {
                preference.text = UserSettingController().mostPlayedSongSize.toString()
            }
            true
        }
        findPreference("setting_songs_most_played_song_size").onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            AnalyticsHelper.trackSetting(context, "setting_songs_most_played_song_size", newValue.toString())
            try {
                val mostPlayedSongSize = Integer.parseInt(newValue.toString())
                if (mostPlayedSongSize in 0..999) {
                    UserSettingController().mostPlayedSongSize = mostPlayedSongSize
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            false
        }
    }

    override fun onStart() {
        super.onStart()
        val fab = activity.findViewById<View>(R.id.fab) as FloatingActionButton
        if (fab != null) {
            ViewCompat.animate(fab)
                    .scaleX(0f).scaleY(0f)
                    .alpha(0f).setDuration(100)
                    .start()
        }
    }

    override fun onStop() {
        super.onStop()
        val mFab = activity.findViewById<View>(R.id.fab) as FloatingActionButton
        if (mFab != null) {
            mFab.visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        LogHelper.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == REQUEST_BACKUP) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                if (data != null && data.data != null && data.data.path.endsWith(".realm")) {
                    val progressDialog = ProgressDialog(activity)
                    progressDialog.setMessage(getString(R.string.message_processing))
                    progressDialog.show()
                    RealmHelper.importBackUp(activity, data.data)
                    progressDialog.dismiss()
                    DialogFacade.createRestartDialog(activity) { dialog, which -> activity.recreate() }.show()
                } else {
                    Toast.makeText(context, R.string.invalid_backup_selected, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(SettingFragment::class.java)
        private val REQUEST_BACKUP = 1
    }

}
