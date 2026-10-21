package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import kotlin.jvm.JvmField
import org.unitedsoftware.mosaic.layout.DebugPolicy
import org.unitedsoftware.mosaic.layout.Measurable
import org.unitedsoftware.mosaic.layout.MeasurePolicy
import org.unitedsoftware.mosaic.layout.MeasureResult
import org.unitedsoftware.mosaic.layout.MeasureScope
import org.unitedsoftware.mosaic.layout.MosaicNode
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.modifier.materialize
import org.unitedsoftware.mosaic.ui.unit.Constraints

@Composable
@MosaicComposable
@Suppress("ktlint:compose:content-trailing-lambda") // Not for public use.
internal inline fun Node(
	measurePolicy: MeasurePolicy,
	debugPolicy: DebugPolicy,
	modifier: Modifier = Modifier,
	content:
	@Composable @MosaicComposable
	() -> Unit = {},
) {
	val materializedModifier = currentComposer.materialize(modifier)
	ComposeNode<MosaicNode, Applier<Any>>(
		factory = NodeFactory,
		update = {
			set(measurePolicy, SetMeasurePolicy)
			set(materializedModifier, SetModifier)
			set(debugPolicy, SetDebugPolicy)
		},
		content = content,
	)
}

@JvmField
internal val SetModifier: MosaicNode.(Modifier) -> Unit = { setModifier(it) }

@JvmField
internal val SetMeasurePolicy: MosaicNode.(MeasurePolicy) -> Unit = { measurePolicy = it }

@JvmField
internal val SetDebugPolicy: MosaicNode.(DebugPolicy) -> Unit = { debugPolicy = it }

@JvmField
internal val NodeFactory: () -> MosaicNode = {
	MosaicNode(
		measurePolicy = ThrowingPolicy,
		debugPolicy = ThrowingPolicy,
		isStatic = false,
	)
}

private val ThrowingPolicy = object : MeasurePolicy, DebugPolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult = throw AssertionError()

	override fun MosaicNode.renderDebug() = throw AssertionError()
}
