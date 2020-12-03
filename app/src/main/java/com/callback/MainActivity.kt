package com.callback

import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.callback.databinding.ActivityMainBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.connect()
        viewModel.connect()
    }

    private fun ActivityMainBinding.connect() {
        buttonDownload.onClick { viewModel.download() }
        buttonCancel.onClick { viewModel.cancel() }
        buttonPause.onClick { viewModel.pause() }
        buttonResume.onClick { viewModel.resume() }
    }

    private fun Button.onClick(block: () -> Unit) {
        lifecycleScope.launchWhenResumed {
            clickFlow()
                .debounce(500)
                .collectLatest {
                delay(1000)
                block()
            }
        }
    }

    private fun MainViewModel.connect() {
        bind(buttonCancelVisible) {
            binding.buttonCancel.isVisible = it
        }
        bind(buttonPauseVisible) {
            binding.buttonPause.isVisible = it
        }
        bind(buttonStartVisible) {
            binding.buttonDownload.isVisible = it
        }
        bind(buttonResumeVisible) {
            binding.buttonResume.isVisible = it
        }
        bind(text) {
            binding.textView.text = it
        }
        bind(progress) {
            binding.progressBar.progress = it
        }
    }
}

@ExperimentalCoroutinesApi
fun Button.clickFlow(): Flow<Unit> =
    callbackFlow {
        setOnClickListener {
            offer(Unit)
        }
        awaitClose {
            setOnClickListener(null)
        }
    }