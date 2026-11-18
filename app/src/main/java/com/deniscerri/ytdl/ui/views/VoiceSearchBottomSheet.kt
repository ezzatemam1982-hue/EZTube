package com.deniscerri.ytdl.ui.views

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.clipflow.app.player.AppLanguage
import com.deniscerri.ytdl.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Locale

class VoiceSearchBottomSheet : BottomSheetDialogFragment() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isOfflineSupported: Boolean = false
    var onQueryResult: ((String) -> Unit)? = null

    private lateinit var closeBtn: ImageButton
    private lateinit var languageBadge: TextView
    private lateinit var rippleView: VoiceRippleView
    private lateinit var micFab: FloatingActionButton
    private lateinit var statusText: TextView
    private lateinit var queryText: TextView
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorMsg: TextView
    private lateinit var retryBtn: MaterialButton

    private val mainHandler = Handler(Looper.getMainLooper())

    private val speechFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim()
            if (!spokenText.isNullOrEmpty()) {
                handleFinalSuccess(spokenText)
            } else {
                showTapToSpeakState(getString(R.string.voice_search_tap_to_speak))
            }
        } else {
            showTapToSpeakState(getString(R.string.voice_search_tap_to_speak))
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkNetworkAndStartRecognition()
        } else {
            handlePermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOfflineSupported = arguments?.getBoolean(ARG_OFFLINE_SUPPORTED, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_voice_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        closeBtn = view.findViewById(R.id.voice_search_close_btn)
        languageBadge = view.findViewById(R.id.voice_search_language_badge)
        rippleView = view.findViewById(R.id.voice_search_ripple_view)
        micFab = view.findViewById(R.id.voice_search_mic_fab)
        statusText = view.findViewById(R.id.voice_search_status_text)
        queryText = view.findViewById(R.id.voice_search_query_text)
        errorContainer = view.findViewById(R.id.voice_search_error_container)
        errorMsg = view.findViewById(R.id.voice_search_error_msg)
        retryBtn = view.findViewById(R.id.voice_search_retry_btn)

        // Ensure bottom sheet expands nicely
        dialog?.setOnShowListener {
            val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        val activeTag = getActiveLanguageTag()
        val displayLang = try {
            Locale.forLanguageTag(activeTag).language.uppercase(Locale.ROOT)
        } catch (e: Exception) {
            Locale.getDefault().language.uppercase(Locale.ROOT)
        }
        languageBadge.text = displayLang

        closeBtn.setOnClickListener {
            stopRecognition()
            dismissAllowingStateLoss()
        }

        micFab.setOnClickListener {
            checkNetworkAndStartRecognition()
        }

        retryBtn.setOnClickListener {
            checkNetworkAndStartRecognition()
        }

        // Start initial permission check and voice recognition
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            checkNetworkAndStartRecognition()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun getActiveLanguageTag(): String {
        return try {
            AppLanguage.locale.toLanguageTag()
        } catch (e: Throwable) {
            Locale.getDefault().toLanguageTag()
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNetworkAndStartRecognition() {
        if (!isAdded) return

        if (!isOfflineSupported && !isNetworkConnected()) {
            showError(getString(R.string.voice_search_error_offline), canRetry = true)
            return
        }

        hideError()
        startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        if (!isAdded) return

        val ctx = requireContext()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            launchFallbackSpeechIntent()
            return
        }

        stopRecognition()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                setRecognitionListener(createRecognitionListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                val langTag = getActiveLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                if (isOfflineSupported) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            statusText.text = getString(R.string.voice_search_listening)
            queryText.text = getString(R.string.voice_search_speak_now)
            val mutedColor = MaterialColors.getColor(queryText, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
            queryText.setTextColor(mutedColor)
            queryText.alpha = 0.7f
            queryText.typeface = Typeface.DEFAULT
            rippleView.setListening(true)
            micFab.isEnabled = true

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            launchFallbackSpeechIntent()
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isAdded) return
                rippleView.setListening(true)
                statusText.text = getString(R.string.voice_search_listening)
            }

            override fun onBeginningOfSpeech() {
                if (!isAdded) return
                rippleView.setListening(true)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isAdded) return
                rippleView.updateRms(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (!isAdded) return
                rippleView.setListening(false)
            }

            override fun onError(error: Int) {
                if (!isAdded) return
                rippleView.setListening(false)
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        showTapToSpeakState(getString(R.string.voice_search_tap_to_speak))
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        if (!isOfflineSupported) {
                            showError(getString(R.string.voice_search_error_offline), canRetry = true)
                        } else {
                            showTapToSpeakState(getString(R.string.voice_search_tap_to_speak))
                        }
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    else -> {
                        showTapToSpeakState(getString(R.string.voice_search_tap_to_speak))
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                if (!isAdded) return
                rippleView.setListening(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()?.trim()
                if (!spokenText.isNullOrEmpty()) {
                    handleFinalSuccess(spokenText)
                } else {
                    showTapToSpeakState(getString(R.string.voice_search_tap_to_speak))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isAdded) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()?.trim()
                if (!partialText.isNullOrEmpty()) {
                    queryText.text = partialText
                    val mutedColor = MaterialColors.getColor(queryText, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
                    queryText.setTextColor(mutedColor)
                    queryText.alpha = 0.7f
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleFinalSuccess(spokenText: String) {
        if (!isAdded) return
        rippleView.setListening(false)
        queryText.text = spokenText
        val brightColor = MaterialColors.getColor(queryText, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        queryText.setTextColor(brightColor)
        queryText.alpha = 1.0f
        queryText.typeface = Typeface.DEFAULT_BOLD

        // 250ms visual confirmation delay before returning result and dismissing
        mainHandler.postDelayed({
            if (isAdded) {
                onQueryResult?.invoke(spokenText)
                dismissAllowingStateLoss()
            }
        }, 250)
    }

    private fun showTapToSpeakState(msg: String) {
        if (!isAdded) return
        rippleView.setListening(false)
        statusText.text = getString(R.string.voice_search)
        queryText.text = msg
        val mutedColor = MaterialColors.getColor(queryText, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        queryText.setTextColor(mutedColor)
        queryText.alpha = 0.9f
        queryText.typeface = Typeface.DEFAULT
        micFab.isEnabled = true
    }

    private fun launchFallbackSpeechIntent() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val langTag = getActiveLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_speak_now))
                if (isOfflineSupported) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            speechFallbackLauncher.launch(intent)
        } catch (e: Exception) {
            showError(getString(R.string.voice_search_not_available), canRetry = false)
        }
    }

    private fun showError(message: String, canRetry: Boolean) {
        if (!isAdded) return
        rippleView.setListening(false)
        micFab.isEnabled = false
        errorContainer.isVisible = true
        errorMsg.text = message
        retryBtn.isVisible = canRetry
    }

    private fun hideError() {
        if (!isAdded) return
        errorContainer.isVisible = false
        micFab.isEnabled = true
    }

    private fun handlePermissionDenied() {
        if (!isAdded) return
        val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        if (permanentlyDenied) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.voice_search_permission_title)
                .setMessage(R.string.voice_search_permission_message)
                .setPositiveButton(R.string.voice_search_open_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                    startActivity(intent)
                    dismissAllowingStateLoss()
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    dismissAllowingStateLoss()
                }
                .show()
        } else {
            showTapToSpeakState(getString(R.string.voice_search_permission_message))
        }
    }

    private fun stopRecognition() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        speechRecognizer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        stopRecognition()
    }

    companion object {
        const val TAG = "VoiceSearchBottomSheet"
        private const val ARG_OFFLINE_SUPPORTED = "arg_offline_supported"

        @JvmStatic
        fun show(
            fragmentManager: FragmentManager,
            isOfflineSupported: Boolean = false,
            onResult: (String) -> Unit
        ): VoiceSearchBottomSheet {
            val existing = fragmentManager.findFragmentByTag(TAG) as? VoiceSearchBottomSheet
            if (existing != null) {
                existing.dismissAllowingStateLoss()
            }
            val sheet = VoiceSearchBottomSheet().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_OFFLINE_SUPPORTED, isOfflineSupported)
                }
                this.onQueryResult = onResult
            }
            sheet.show(fragmentManager, TAG)
            return sheet
        }
    }
}
