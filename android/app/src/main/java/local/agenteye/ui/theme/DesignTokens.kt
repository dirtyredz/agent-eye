package local.agenteye.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val ContainerShape = RoundedCornerShape(16.dp)

internal val AgentEyeShapes = Shapes(
    small = ContainerShape,
    medium = ContainerShape,
    large = ContainerShape
)

internal object AgentEyeSpacing {
    val screen = 16.dp

    // Section separates peers; content pads children inside a container.
    val section = 12.dp
    val content = 12.dp
    val compact = 8.dp
    val touchTarget = 48.dp
}

internal object AgentEyeElevation {
    val container = 2.dp
}
