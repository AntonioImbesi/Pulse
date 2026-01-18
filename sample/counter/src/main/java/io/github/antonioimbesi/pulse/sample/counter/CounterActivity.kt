package io.github.antonioimbesi.pulse.sample.counter

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.antonioimbesi.pulse.android.collectSideEffect
import io.github.antonioimbesi.pulse.android.collectState
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterIntent
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterSideEffect

@AndroidEntryPoint
class CounterActivity : ComponentActivity() {

    private val viewModel: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val state by viewModel.collectState()

            viewModel.collectSideEffect { sideEffect ->
                when (sideEffect) {
                    is CounterSideEffect.BelowZero ->
                        Toast.makeText(context, "Below zero", Toast.LENGTH_SHORT).show()
                }
            }

            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(
                            space = 12.dp,
                            alignment = Alignment.CenterHorizontally
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        Button(
                            onClick = { viewModel dispatch CounterIntent.Decrease },
                        ) { Text(text = "-") }

                        Text(text = state.counter.toString())

                        Button(
                            onClick = { viewModel dispatch CounterIntent.Increase },
                        ) { Text("+") }
                    }
                }
            }
        }
    }
}
