package com.example.hisma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.hisma.ui.theme.HismaTheme
import com.example.hisma.ui.navigation.AppNavigation
import com.example.hisma.ui.screen.SubscriptionRequiredScreen
import com.example.hisma.utils.SubscriptionController
import com.example.hisma.utils.SubscriptionStatus
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment




class MainActivity : ComponentActivity() {
    private lateinit var subscriptionController: SubscriptionController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscriptionController = SubscriptionController(this)

        setContent {
            HismaTheme {
                var subscriptionStatus by remember { mutableStateOf<SubscriptionStatus?>(null) }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    subscriptionStatus = subscriptionController.checkSubscriptionStatus()
                    isLoading = false
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    AppNavigation(
                        subscriptionController = subscriptionController,
                        subscriptionStatus = subscriptionStatus
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HismaTheme {
        Greeting("Android")
    }
}