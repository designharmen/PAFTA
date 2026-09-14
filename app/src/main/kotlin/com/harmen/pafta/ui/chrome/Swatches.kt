package com.harmen.pafta.ui.chrome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harmen.pafta.project.FinishFamily
import com.harmen.pafta.project.FloorFinish
import com.harmen.pafta.project.WallMaterial
import com.harmen.pafta.ui.theme.HarmenColours

/**
 * The little drawn square beside a material's name.
 *
 * PAFTA's interface was words all the way down: four wall materials and twenty
 * floor finishes, each of them a line of Turkish and nothing else. A material
 * is a thing you recognise by looking at it, and a list of twenty names is a
 * list nobody reads twice. So each one gets the mark a drawing would give it.
 *
 * Drawn rather than drawn-from-images: these are hatch patterns, the same ones
 * the plan itself uses, so the square beside the word and the fill inside the
 * wall are the same idea at two sizes. No new colours are introduced — the
 * brand palette allows none — so what separates one from another is the
 * pattern: its direction, its spacing, and what is laid over it.
 */
@Composable
public fun MaterialSwatch(
    material: WallMaterial,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = HarmenColours.TextMuted,
) {
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(2.dp))
            .background(HarmenColours.Ground),
    ) {
        val edge = this.size.width
        val stroke = edge * 0.055f
        when (material) {
            // Brick: courses with the perpends staggered, which is what a
            // running bond looks like and what everyone draws.
            WallMaterial.BRICK -> {
                for (i in 1..2) {
                    val y = edge * i / 3f
                    drawLine(tint, Offset(0f, y), Offset(edge, y), stroke)
                }
                drawLine(tint, Offset(edge / 2f, 0f), Offset(edge / 2f, edge / 3f), stroke)
                drawLine(tint, Offset(edge / 4f, edge / 3f), Offset(edge / 4f, edge * 2 / 3f), stroke)
                drawLine(
                    tint,
                    Offset(edge * 3 / 4f, edge / 3f),
                    Offset(edge * 3 / 4f, edge * 2 / 3f),
                    stroke,
                )
                drawLine(tint, Offset(edge / 2f, edge * 2 / 3f), Offset(edge / 2f, edge), stroke)
            }

            // Concrete: the aggregate-and-sand mark — scattered stones over a
            // stippling, which is the standard hatch on any structural drawing.
            WallMaterial.CONCRETE -> {
                drawCircle(tint, edge * 0.09f, Offset(edge * 0.28f, edge * 0.30f))
                drawCircle(tint, edge * 0.07f, Offset(edge * 0.70f, edge * 0.24f))
                drawCircle(tint, edge * 0.08f, Offset(edge * 0.52f, edge * 0.62f))
                drawCircle(tint, edge * 0.055f, Offset(edge * 0.22f, edge * 0.74f))
                drawCircle(tint, edge * 0.05f, Offset(edge * 0.80f, edge * 0.72f))
            }

            // Aerated block: big units, laid thin-jointed, with the diagonal
            // that tells you it is a block and not poured.
            WallMaterial.AERATED -> {
                drawLine(tint, Offset(0f, edge / 2f), Offset(edge, edge / 2f), stroke)
                drawLine(tint, Offset(edge / 2f, 0f), Offset(edge / 2f, edge), stroke)
                drawLine(tint, Offset(0f, edge), Offset(edge, 0f), stroke * 0.8f)
            }

            // Timber: the grain, running the length of the member.
            WallMaterial.TIMBER -> {
                for (i in 1..3) {
                    val x = edge * i / 4f
                    drawLine(tint, Offset(x, edge * 0.08f), Offset(x, edge * 0.92f), stroke)
                }
                drawLine(
                    tint,
                    Offset(edge * 0.25f, edge * 0.35f),
                    Offset(edge * 0.75f, edge * 0.45f),
                    stroke * 0.7f,
                )
            }
        }
    }
}

/**
 * The little drawn square beside a floor finish's name.
 *
 * Four families, four marks — planks, tiles, a poured sheet, a pile — because
 * that is genuinely how many different things these twenty are on a plan;
 * laminat and parke are the same hatch to any draughtsman. What tells the
 * twenty apart inside a family is the spacing and the overlay, which changes
 * with each one, so no two of the twenty are drawn the same.
 */
@Composable
public fun FinishSwatch(
    finish: FloorFinish,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = HarmenColours.TextMuted,
) {
    // Where this finish sits inside its own family, which is what varies the
    // drawing so that all five timbers are not one picture repeated.
    val within = FloorFinish.entries.filter { it.family == finish.family }.indexOf(finish)

    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(2.dp))
            .background(HarmenColours.Ground),
    ) {
        val edge = this.size.width
        val stroke = edge * 0.055f
        when (finish.family) {
            // Boards, laid the long way, with the end joints staggered further
            // along for each finish in the family.
            FinishFamily.TIMBER -> {
                val boards = 3 + within % 3
                for (i in 1 until boards) {
                    val y = edge * i / boards
                    drawLine(tint, Offset(0f, y), Offset(edge, y), stroke)
                }
                val joint = edge * (0.25f + 0.15f * (within % 4))
                drawLine(tint, Offset(joint, 0f), Offset(joint, edge / boards), stroke)
                drawLine(
                    tint,
                    Offset(edge - joint, edge * (boards - 1) / boards),
                    Offset(edge - joint, edge),
                    stroke,
                )
            }

            // Tiles: a grid, coarser or finer, and squared up or laid diagonally
            // depending on which of the seven it is.
            FinishFamily.STONE -> {
                val squares = 2 + within % 3
                for (i in 1 until squares) {
                    val at = edge * i / squares
                    drawLine(tint, Offset(0f, at), Offset(edge, at), stroke)
                    drawLine(tint, Offset(at, 0f), Offset(at, edge), stroke)
                }
                if (within % 2 == 1) {
                    drawLine(tint, Offset(0f, edge), Offset(edge, 0f), stroke * 0.7f)
                }
            }

            // Poured or sheet: unbroken, with one or two scored lines across it.
            FinishFamily.RESILIENT -> {
                drawLine(
                    tint,
                    Offset(edge * 0.1f, edge * 0.75f),
                    Offset(edge * 0.9f, edge * 0.75f),
                    stroke,
                )
                for (i in 0..(within % 3)) {
                    val x = edge * (0.25f + 0.25f * i)
                    drawLine(tint, Offset(x, edge * 0.2f), Offset(x, edge * 0.6f), stroke * 0.8f)
                }
            }

            // Pile: the little tufts a carpet is drawn with.
            FinishFamily.TEXTILE -> {
                val tufts = 4 + within
                for (i in 0 until tufts) {
                    val x = edge * (i + 0.5f) / tufts
                    drawLine(tint, Offset(x, edge * 0.25f), Offset(x, edge * 0.75f), stroke)
                }
                drawLine(
                    tint,
                    Offset(0f, edge * 0.85f),
                    Offset(edge, edge * 0.85f),
                    stroke,
                )
            }
        }
    }
}
