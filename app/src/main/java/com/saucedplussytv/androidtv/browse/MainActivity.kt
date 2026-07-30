package com.saucedplussytv.androidtv.browse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.saucedplussytv.androidtv.R
import com.saucedplussytv.androidtv.browse.MainFragment
import dagger.hilt.android.AndroidEntryPoint

/*
 * Main Activity class that loads {@link MainFragment}.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Browse)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Only add on a cold create. On recreation (process death, config change) the
        // FragmentManager restores the existing MainFragment; adding another would
        // duplicate the browse UI and re-run the whole login/subscription load.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, MainFragment())
                .commit()
        }
    }
}