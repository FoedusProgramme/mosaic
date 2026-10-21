@file:Suppress("NOTHING_TO_INLINE")

package org.unitedsoftware.mosaic

import androidx.compose.runtime.Composable
import kotlin.math.max
import org.unitedsoftware.mosaic.layout.IntrinsicMeasurable
import org.unitedsoftware.mosaic.layout.Measurable
import org.unitedsoftware.mosaic.layout.MeasurePolicy
import org.unitedsoftware.mosaic.layout.MeasureResult
import org.unitedsoftware.mosaic.layout.MeasureScope
import org.unitedsoftware.mosaic.layout.MosaicNode
import org.unitedsoftware.mosaic.layout.Placeable
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.AnsiLevel.NONE
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Alignment
import org.unitedsoftware.mosaic.ui.Filler
import org.unitedsoftware.mosaic.ui.Layout
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.IntOffset
import org.unitedsoftware.mosaic.ui.unit.IntSize
import org.unitedsoftware.mosaic.ui.unit.constrain
import org.unitedsoftware.mosaic.ui.unit.constrainHeight
import org.unitedsoftware.mosaic.ui.unit.constrainWidth

const val s = " "

const val TestChar = 'X'

fun String.replaceLineEndingsWithCRLF(): String {
	return this.replace("\n", "\r\n")
}

fun String.wrapWithAnsiSynchronizedUpdate(): String {
	return "$synchronizedOutputEnable$this$synchronizedOutputDisable"
}

fun cursorUp(lines: Int): String = "${CSI}${lines}F"

internal val MosaicNode.size: IntSize
	get() = IntSize(width, height)

internal val MosaicNode.position: IntOffset
	get() = IntOffset(x, y)

class Holder<T>(var value: T)

internal fun MosaicNode.nodeByTestTag(tag: String): MosaicNode {
	val queue = ArrayDeque<MosaicNode>().also { it.add(this) }
	while (queue.isNotEmpty()) {
		val node = queue.removeFirst()
		if (node.testTag == tag) {
			return node
		}
		queue.addAll(node.children)
	}
	throw AssertionError("No node with test tag '$tag'")
}

@Composable
inline fun TestFiller(modifier: Modifier = Modifier) {
	Filler(TestChar, modifier = modifier)
}

@Composable
internal fun Container(
	modifier: Modifier = Modifier,
	alignment: Alignment = Alignment.Center,
	expanded: Boolean = false,
	constraints: Constraints = Constraints(),
	width: Int? = null,
	height: Int? = null,
	content: @Composable () -> Unit = {},
) {
	Layout(content, modifier, debugInfo = { "Container()" }) { measurables, incomingConstraints ->
		val containerConstraints = incomingConstraints.constrain(
			Constraints(constraints.value)
				.copy(
					width ?: constraints.minWidth,
					width ?: constraints.maxWidth,
					height ?: constraints.minHeight,
					height ?: constraints.maxHeight,
				),
		)
		val childConstraints = containerConstraints.copy(minWidth = 0, minHeight = 0)
		var placeable: Placeable? = null
		val containerWidth = if ((containerConstraints.hasFixedWidth || expanded) &&
			containerConstraints.hasBoundedWidth
		) {
			containerConstraints.maxWidth
		} else {
			placeable = measurables.firstOrNull()?.measure(childConstraints)
			max(placeable?.width ?: 0, containerConstraints.minWidth)
		}
		val containerHeight = if ((containerConstraints.hasFixedHeight || expanded) &&
			containerConstraints.hasBoundedHeight
		) {
			containerConstraints.maxHeight
		} else {
			if (placeable == null) {
				placeable = measurables.firstOrNull()?.measure(childConstraints)
			}
			max(placeable?.height ?: 0, containerConstraints.minHeight)
		}
		layout(containerWidth, containerHeight) {
			val p = placeable ?: measurables.firstOrNull()?.measure(childConstraints)
			p?.let {
				val position = alignment.align(
					IntSize(it.width, it.height),
					IntSize(containerWidth, containerHeight),
				)
				it.place(position.x, position.y)
			}
		}
	}
}

suspend fun testIntrinsics(
	vararg layouts: @Composable () -> Unit,
	test: ((Int) -> Int, (Int) -> Int, (Int) -> Int, (Int) -> Int) -> Unit,
) {
	layouts.forEach { layout ->
		runMosaicTest {
			setContent {
				val measurePolicy = object : MeasurePolicy {
					override fun MeasureScope.measure(
						measurables: List<Measurable>,
						constraints: Constraints,
					): MeasureResult {
						val measurable = measurables.first()
						test(
							{ h -> measurable.minIntrinsicWidth(h) },
							{ w -> measurable.minIntrinsicHeight(w) },
							{ h -> measurable.maxIntrinsicWidth(h) },
							{ w -> measurable.maxIntrinsicHeight(w) },
						)

						return layout(0, 0) {}
					}

					override fun minIntrinsicWidth(
						measurables: List<IntrinsicMeasurable>,
						height: Int,
					) = 0

					override fun minIntrinsicHeight(
						measurables: List<IntrinsicMeasurable>,
						width: Int,
					) = 0

					override fun maxIntrinsicWidth(
						measurables: List<IntrinsicMeasurable>,
						height: Int,
					) = 0

					override fun maxIntrinsicHeight(
						measurables: List<IntrinsicMeasurable>,
						width: Int,
					) = 0
				}
				Layout(
					content = layout,
					measurePolicy = measurePolicy,
				)
			}
		}
	}
}

@Composable
fun ConstrainedBox(
	constraints: Constraints,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit = {},
) {
	@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
	val measurePolicy = object : MeasurePolicy {
		override fun MeasureScope.measure(
			measurables: List<Measurable>,
			incomingConstraints: Constraints,
		): MeasureResult {
			val measurable = measurables.firstOrNull()
			val childConstraints = incomingConstraints.constrain(constraints)
			val placeable = measurable?.measure(childConstraints)

			val layoutWidth = placeable?.width ?: childConstraints.minWidth
			val layoutHeight = placeable?.height ?: childConstraints.minHeight
			return layout(layoutWidth, layoutHeight) {
				placeable?.place(0, 0)
			}
		}

		override fun minIntrinsicWidth(
			measurables: List<IntrinsicMeasurable>,
			height: Int,
		): Int {
			val width = measurables.firstOrNull()?.minIntrinsicWidth(height) ?: 0
			return constraints.constrainWidth(width)
		}

		override fun minIntrinsicHeight(
			measurables: List<IntrinsicMeasurable>,
			width: Int,
		): Int {
			val height = measurables.firstOrNull()?.minIntrinsicHeight(width) ?: 0
			return constraints.constrainHeight(height)
		}

		override fun maxIntrinsicWidth(
			measurables: List<IntrinsicMeasurable>,
			height: Int,
		): Int {
			val width = measurables.firstOrNull()?.maxIntrinsicWidth(height) ?: 0
			return constraints.constrainWidth(width)
		}

		override fun maxIntrinsicHeight(
			measurables: List<IntrinsicMeasurable>,
			width: Int,
		): Int {
			val height = measurables.firstOrNull()?.maxIntrinsicHeight(width) ?: 0
			return constraints.constrainHeight(height)
		}
	}
	Layout(
		content = content,
		modifier = modifier,
		measurePolicy = measurePolicy,
	)
}

@Composable
internal fun AtLeastSize(
	size: Int,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit = {},
) {
	Layout(
		measurePolicy = { measurables, constraints ->
			val newConstraints =
				Constraints(
					minWidth = max(size, constraints.minWidth),
					maxWidth =
					if (constraints.hasBoundedWidth) {
						max(size, constraints.maxWidth)
					} else {
						Constraints.Infinity
					},
					minHeight = max(size, constraints.minHeight),
					maxHeight =
					if (constraints.hasBoundedHeight) {
						max(size, constraints.maxHeight)
					} else {
						Constraints.Infinity
					},
				)
			val placeables = measurables.map { m -> m.measure(newConstraints) }
			var maxWidth = size
			var maxHeight = size
			placeables.forEach { child ->
				maxHeight = max(child.height, maxHeight)
				maxWidth = max(child.width, maxWidth)
			}
			layout(maxWidth, maxHeight) { placeables.forEach { child -> child.place(0, 0) } }
		},
		modifier = modifier,
		content = content,
	)
}

fun TextCanvas.render() = render(NONE, false)
