package zeroxfourf.wristkey

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import wristkey.R
import java.util.Timer
import java.util.TimerTask

class SteamImportActivity : AppCompatActivity() {

    lateinit var utilities: Utilities

    private lateinit var clock: TextView
    lateinit var mfaCodesTimer: Timer

    private lateinit var issuerInput: TextInputEditText
    private lateinit var accountInput: TextInputEditText
    private lateinit var labelInput: TextInputEditText
    private lateinit var secretInput: TextInputEditText

    private lateinit var doneButton: Button
    private lateinit var backButton: Button

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_steam_import)

        utilities = Utilities(applicationContext)
        mfaCodesTimer = Timer()

        initializeUI()
    }

    private fun startClock() {
        if (!utilities.db.getBoolean(utilities.SETTINGS_CLOCK_ENABLED, true)) clock.visibility = View.GONE

        try {
            mfaCodesTimer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread { clock.text = utilities.getTime() }
                }
            }, 0, 1000)
        } catch (_: IllegalStateException) { }
    }

    override fun onStop() {
        super.onStop()
        mfaCodesTimer.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        mfaCodesTimer.cancel()
        finish()
    }

    override fun onStart() {
        super.onStart()
        mfaCodesTimer = Timer()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun initializeUI() {

        clock = findViewById(R.id.clock)
        startClock()

        issuerInput = findViewById(R.id.issuerInput)
        accountInput = findViewById(R.id.accountInput)
        labelInput = findViewById(R.id.labelInput)
        secretInput = findViewById(R.id.secretInput)
        secretInput.transformationMethod = PasswordTransformationMethod()

        doneButton = findViewById(R.id.doneButton)
        backButton = findViewById(R.id.backButton)

        doneButton.setOnClickListener {

            val issuer = (issuerInput.text?.toString() ?: "").ifBlank { "Steam" }
            val account = (accountInput.text?.toString() ?: "").trim()
            val label = (labelInput.text?.toString() ?: "").trim()
            val sharedSecretB64 = (secretInput.text?.toString() ?: "").trim()

            // 仅校验密钥非空（并允许任意长度；Base64 合法性由生成函数内部处理）
            if (sharedSecretB64.isEmpty()) {
                CustomFullscreenDialogFragment(
                    title = "Invalid secret",
                    message = getString(R.string.secret_empty),
                    positiveButtonText = null,
                    positiveButtonIcon = null,
                    negativeButtonText = "Go back",
                    negativeButtonIcon = getDrawable(R.drawable.ic_prev)!!,
                ).show(supportFragmentManager, "CustomFullscreenDialog")
                return@setOnClickListener
            }

            val data = Utilities.MfaCode(
                mode = utilities.MFA_TIME_MODE,       // Steam Guard 是基于时间
                issuer = issuer,
                account = account,
                secret = sharedSecretB64,              // Base64 的 shared_secret
                algorithm = utilities.ALGO_STEAM,      // 自定义算法标识
                digits = 5,                            // 5 位（Steam 字母表）
                period = 30,                           // 30 秒步长
                lock = false,
                counter = 0,
                label = label
            )

            val dataUrl = utilities.encodeOtpAuthURL(data)
            utilities.overwriteLogin(dataUrl)

            finishAffinity()
            startActivity(Intent(applicationContext, MainActivity::class.java))
        }

        backButton.setOnClickListener { finish() }
    }
}
