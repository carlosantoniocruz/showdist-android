package com.showconfigs.showdist.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.BackBar
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ReadingSides
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.Type

/** The faint line drawings beside formulas: secondary to the text but visible outdoors. */
private val Faint = Palette.Text.copy(alpha = 0.38f)

/** A small line drawing beside a formula. */
private enum class Sketch { Rectangle, Triangle, Circle, Box, RightTriangle, ThreeFourFive, Percent, Thermometer }

private class Formula(val name: String, val formula: String, val example: String, val sketch: Sketch? = null)

private class Section(val title: String, val rows: List<Formula>)

/**
 * The formulas and examples (SPEC.md §1.8), examples in the user's units. Every formula and
 * conversion factor here was checked: π × 2² = 12.57; 1 in = 2.54 cm exactly; 1 km = 0.621 mi;
 * 1 kg = 2.205 lb; °F = °C × 1.8 + 32.
 */
private fun sections(units: UnitSystem): List<Section> {
    val metric = units == UnitSystem.Metric
    return listOf(
        Section(
            "SHAPES",
            listOf(
                Formula(
                    "Rectangle area", "length × width",
                    if (metric) "3 m × 4 m = 12 m²" else "10 ft × 12 ft = 120 sq ft", Sketch.Rectangle,
                ),
                Formula(
                    "Triangle area", "½ × base × height",
                    if (metric) "½ × 6 m × 4 m = 12 m²" else "½ × 6 ft × 4 ft = 12 sq ft", Sketch.Triangle,
                ),
                Formula(
                    "Circle area", "π × radius²",
                    if (metric) "π × (2 m)² ≈ 12.57 m²" else "π × (2 ft)² ≈ 12.57 sq ft", Sketch.Circle,
                ),
                Formula(
                    "Circumference", "2 × π × radius (π ≈ 3.14)",
                    if (metric) "2 × π × 2 m ≈ 12.57 m" else "2 × π × 2 ft ≈ 12.57 ft", Sketch.Circle,
                ),
                Formula(
                    "Box volume", "length × width × height",
                    if (metric) "3 m × 4 m × 2.5 m = 30 m³" else "10 ft × 12 ft × 8 ft = 960 cu ft", Sketch.Box,
                ),
            ),
        ),
        Section(
            "SQUARE CORNERS",
            listOf(
                Formula("Pythagorean theorem", "a² + b² = c²", "3² + 4² = 9 + 16 = 25 = 5²", Sketch.RightTriangle),
                Formula(
                    "The 3-4-5 trick", "sides 3 and 4, diagonal 5: square",
                    if (metric) "mark 60 cm and 80 cm; 1 m across means square" else "mark 3 ft and 4 ft; 5 ft across means square",
                    Sketch.ThreeFourFive,
                ),
            ),
        ),
        Section(
            "PERCENTAGES AND TIPS",
            listOf(
                Formula("10 percent", "move the decimal one place left", "10% of 45.00 = 4.50", Sketch.Percent),
                Formula("15 percent tip", "10% plus half of that", "on 40: 4 + 2 = 6"),
                Formula("Percent change", "(new − original) ÷ original × 100", "50 to 60: 10 ÷ 50 = 20% up"),
            ),
        ),
        Section(
            "CONVERSIONS",
            listOf(
                Formula("Inches to centimetres", "× 2.54 (exact)", "12 in × 2.54 = 30.48 cm"),
                Formula("Kilometres to miles", "× 0.62 (about)", "10 km ≈ 6.2 mi"),
                Formula("Kilograms to pounds", "× 2.2 (about)", "5 kg ≈ 11 lb"),
                Formula(
                    "Celsius to Fahrenheit", "× 1.8 + 32 (quick: double + 30)",
                    "20 °C × 1.8 + 32 = 68 °F (quick: 70 °F)", Sketch.Thermometer,
                ),
            ),
        ),
        Section(
            "SPEED",
            listOf(
                Formula(
                    "Speed", "distance ÷ time",
                    if (metric) "100 km in 2 h = 50 km/h" else "120 mi in 2 h = 60 mph",
                ),
            ),
        ),
    )
}

/**
 * Handy formulas (SPEC.md §1.8), from Settings → Additional Info: clean tables of everyday
 * formulas with an example each and, where a shape fits, a faint line drawing labelled to
 * match. Static (no animation); the drawings are built once per size and reused.
 */
@Composable
fun FormulasScreen(units: UnitSystem, onBack: () -> Unit) {
    val all = sections(units)
    val measurer = rememberTextMeasurer()
    Scaffold(topBar = { ShowdistAppBar("HANDY FORMULAS") }, bottomBar = { BackBar(onBack) }, containerColor = Palette.Black) { padding ->
        ReadingSides(Modifier.fillMaxSize().padding(padding)) { side ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp)) {
                for ((si, section) in all.withIndex()) {
                    item(key = section.title) {
                        if (si > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), thickness = 1.dp, color = Palette.Divider)
                        Text(
                            section.title,
                            Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 4.dp).semantics { heading() },
                            style = Type.Caption,
                        )
                    }
                    items(section.rows, key = { section.title + it.name }) { f -> FormulaRow(f, measurer) }
                }
            }
        }
    }
}

@Composable
private fun FormulaRow(f: Formula, measurer: TextMeasurer) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name, style = Type.Label.copy(fontWeight = FontWeight.Bold))
            Text(f.formula, style = Type.Label.copy(color = Palette.Sandstone))
            Spacer(Modifier.height(2.dp))
            Text(f.example, style = Type.Secondary)
        }
        if (f.sketch != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(64.dp).clearAndSetSemantics {}.sketch(f.sketch, measurer))
        }
    }
}

/** Draws [s] in thin faint strokes, with small dimension letters; paths are built once per size. */
private fun Modifier.sketch(s: Sketch, measurer: TextMeasurer) = drawWithCache {
    val stroke = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    val letter = Type.Secondary.copy(color = Faint, fontSize = 11.sp)
    fun DrawScope.label(text: String, x: Float, y: Float) {
        val layout = measurer.measure(text, letter)
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
    }
    val path = Path()
    when (s) {
        Sketch.Rectangle -> path.addRect(androidx.compose.ui.geometry.Rect(w * 0.12f, h * 0.25f, w * 0.88f, h * 0.7f))
        Sketch.Triangle, Sketch.RightTriangle, Sketch.ThreeFourFive -> {
            if (s == Sketch.Triangle) {
                path.moveTo(w * 0.1f, h * 0.78f); path.lineTo(w * 0.9f, h * 0.78f); path.lineTo(w * 0.42f, h * 0.18f); path.close()
            } else {
                path.moveTo(w * 0.18f, h * 0.82f); path.lineTo(w * 0.82f, h * 0.82f); path.lineTo(w * 0.18f, h * 0.2f); path.close()
            }
        }
        Sketch.Circle -> path.addOval(androidx.compose.ui.geometry.Rect(w * 0.14f, h * 0.14f, w * 0.86f, h * 0.86f))
        Sketch.Box -> {
            // A front face and a back face offset up and right, joined at the corners.
            val dx = w * 0.18f
            val dy = h * 0.16f
            path.addRect(androidx.compose.ui.geometry.Rect(w * 0.1f, h * 0.36f, w * 0.68f, h * 0.84f))
            path.moveTo(w * 0.1f, h * 0.36f); path.lineTo(w * 0.1f + dx, h * 0.36f - dy)
            path.lineTo(w * 0.68f + dx, h * 0.36f - dy); path.lineTo(w * 0.68f + dx, h * 0.84f - dy); path.lineTo(w * 0.68f, h * 0.84f)
            path.moveTo(w * 0.68f, h * 0.36f); path.lineTo(w * 0.68f + dx, h * 0.36f - dy)
        }
        Sketch.Thermometer -> {
            path.addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.42f, h * 0.1f, w * 0.58f, h * 0.68f, w * 0.08f, w * 0.08f))
            path.addOval(androidx.compose.ui.geometry.Rect(w * 0.34f, h * 0.62f, w * 0.66f, h * 0.9f))
        }
        Sketch.Percent -> {
            path.moveTo(w * 0.75f, h * 0.2f); path.lineTo(w * 0.25f, h * 0.8f)
            path.addOval(androidx.compose.ui.geometry.Rect(w * 0.22f, h * 0.2f, w * 0.4f, h * 0.38f))
            path.addOval(androidx.compose.ui.geometry.Rect(w * 0.6f, h * 0.62f, w * 0.78f, h * 0.8f))
        }
    }
    onDrawBehind {
        drawPath(path, Faint, style = stroke)
        when (s) {
            Sketch.Rectangle -> { label("l", w * 0.5f, h * 0.84f); label("w", w * 0.96f, h * 0.48f) }
            Sketch.Triangle -> {
                drawLine(Faint, Offset(w * 0.42f, h * 0.18f), Offset(w * 0.42f, h * 0.78f), 1.dp.toPx(), StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                label("b", w * 0.66f, h * 0.9f); label("h", w * 0.5f, h * 0.52f)
            }
            Sketch.Circle -> {
                drawLine(Faint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), 1.4.dp.toPx(), StrokeCap.Round)
                drawCircle(Faint, 1.6.dp.toPx(), Offset(w * 0.5f, h * 0.5f))
                label("r", w * 0.68f, h * 0.4f)
            }
            Sketch.Box -> { label("l", w * 0.39f, h * 0.94f); label("h", w * 0.02f + 2f, h * 0.6f); label("w", w * 0.92f, h * 0.84f - h * 0.12f) }
            Sketch.RightTriangle, Sketch.ThreeFourFive -> {
                // The square-corner mark.
                val m = w * 0.1f
                drawPath(Path().apply { moveTo(w * 0.18f + m, h * 0.82f); lineTo(w * 0.18f + m, h * 0.82f - m); lineTo(w * 0.18f, h * 0.82f - m) }, Faint, style = Stroke(1.dp.toPx()))
                val (a, b, c) = if (s == Sketch.RightTriangle) Triple("a", "b", "c") else Triple("3", "4", "5")
                label(a, w * 0.08f, h * 0.51f); label(b, w * 0.5f, h * 0.93f); label(c, w * 0.6f, h * 0.44f)
            }
            Sketch.Thermometer -> drawLine(Faint, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.7f), 3.dp.toPx(), StrokeCap.Round)
            Sketch.Percent -> Unit
        }
    }
}
