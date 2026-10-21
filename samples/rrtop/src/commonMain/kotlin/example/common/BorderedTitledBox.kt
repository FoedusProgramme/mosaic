package example.common

import androidx.compose.runtime.Composable
import org.unitedsoftware.mosaic.layout.offset
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.text.withStyle
import org.unitedsoftware.mosaic.ui.Alignment
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.BoxScope
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.Text

@Composable
fun BorderedTitledBox(
	title: String,
	titleColor: Color,
	borderColor: Color,
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	Box(modifier = modifier.border(color = borderColor).padding(horizontal = 1)) {
		Text(
			buildAnnotatedString {
				append("┐ ")
				withStyle(SpanStyle(titleColor)) {
					append(title)
				}
				append(" ┌")
			},
			modifier = Modifier.align(Alignment.TopStart).offset(x = -1, y = -1),
			color = borderColor,
		)
		content()
	}
}
