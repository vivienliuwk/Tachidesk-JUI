package ca.gosyer.jui.uicore.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BadgeGroup(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    content: @Composable RowScope.() -> Unit
) {
    Row(modifier = modifier.clip(shape)) {
        content()
    }
}

@Composable
fun Badge(
    text: String,
    color: Color = MaterialTheme.colors.secondary,
    textColor: Color = MaterialTheme.colors.onSecondary,
    shape: Shape = RectangleShape
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(shape)
            .background(color)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        color = textColor,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        style = MaterialTheme.typography.caption
    )
}
