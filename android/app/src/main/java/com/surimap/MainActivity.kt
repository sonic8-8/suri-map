package com.surimap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.surimap.ui.SuriMapApp
import com.surimap.ui.theme.SuriMapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SuriMapTheme {
                SuriMapApp()
            }
        }
    }
}
