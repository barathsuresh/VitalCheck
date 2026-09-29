package com.barathsuresh.vitalcheck.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Star Rating composable widget
 * @author barathsuresh
 */
@Composable
fun StarRating(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxStars: Int = 5,
) {
    // Using the loop we can create a star rating widget
    Row(modifier = modifier) {
        for (i in 1..maxStars) {
            Text(
                text = if (i <= rating) "★" else "☆",
                fontSize = 32.sp,
                color = if (i <= rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .clickable { onRatingChange(i) }
                    .padding(2.dp),
            )
        }
    }
}