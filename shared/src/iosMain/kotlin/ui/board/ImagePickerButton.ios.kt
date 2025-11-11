package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.pickImage
import kotlinx.coroutines.launch

@Composable
actual fun ImagePickerButton(
    onImageSelected: (ImageData) -> Unit
) {
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            scope.launch {
                val imageData = pickImage()
                imageData?.let(onImageSelected)
            }
        }
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("画像を選択")
    }
}
