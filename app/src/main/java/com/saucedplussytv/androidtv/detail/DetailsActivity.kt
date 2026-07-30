package com.saucedplussytv.androidtv.detail

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.saucedplussytv.androidtv.R
import dagger.hilt.android.AndroidEntryPoint

/*
 * Details activity class that loads LeanbackDetailsFragment class
 */
@AndroidEntryPoint
class DetailsActivity : AppCompatActivity() {
    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Video)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        // See MainActivity: only add on a cold create, otherwise recreation stacks a
        // second details fragment on top of the restored one.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.details_fragment, VideoDetailsFragment())
                .commit()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_OK, Intent().putExtra("REFRESH", true))
                finish()
            }
        })
    }

    companion object {
        const val SHARED_ELEMENT_NAME = "hero"
        const val Video = "Video"
        const val Resume = "Resume"
    }
}