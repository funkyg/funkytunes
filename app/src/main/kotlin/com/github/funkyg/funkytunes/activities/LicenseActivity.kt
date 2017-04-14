package com.github.funkyg.funkytunes.activities

import android.content.Intent
import android.databinding.DataBindingUtil
import android.graphics.PorterDuff
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import com.github.funkyg.funkytunes.R
import com.github.funkyg.funkytunes.databinding.ActivityLicenseBinding


class LicenseActivity : AppCompatActivity() {

    private val PREF_LICENSE_ACCEPTED = "license_accepted"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        if (pref.getBoolean(PREF_LICENSE_ACCEPTED, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        val binding =
                DataBindingUtil.setContentView<ActivityLicenseBinding>(this, R.layout.activity_license)
        val accentColor = ContextCompat.getColor(this, R.color.colorAccent)
        binding.accept.background.setColorFilter(accentColor, PorterDuff.Mode.MULTIPLY);
        binding.leave.background.setColorFilter(accentColor, PorterDuff.Mode.MULTIPLY);
        binding.accept.setOnClickListener {
            pref.edit().putBoolean(PREF_LICENSE_ACCEPTED, true).apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        binding.leave.setOnClickListener { finish() }
    }
}