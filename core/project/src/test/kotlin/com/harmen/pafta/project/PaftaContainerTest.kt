package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.measure.MeasurementKind
import com.harmen.pafta.measure.label
import com.harmen.pafta.units.LengthUnit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun sampleProject(): PaftaProject {
    val payload = "0\nSECTION\n2\nENTITIES\n0\nENDSEC\n0\nEOF\n".toByteArray()
    return newProject(
        projectName = "Unit 101 - Lvl 2",
        fileName = "ground-floor.dxf",
        payload = payload,
        nowEpochMs = 1_700_000_000_000,
        importedFrom = "content://downloads/42",
        units = UnitPreferences(lengthUnit = LengthUnit.MILLIMETRE.name),
    ).copy(
        annotations = listOf(
            Annotation.Text("a1", Vec3(100.0, 200.0, 0.0), "LIVING", heightMm = 150.0),
            Annotation.Arrow("a2", Vec3.ZERO, Vec3(500.0, 500.0, 0.0)),
            Annotation.Pin("a3", Vec3(10.0, 20.0, 0.0), "check sill height"),
            Annotation.Dimension("a4", Vec3.ZERO, Vec3(5500.0, 0.0, 0.0), offsetMm = 300.0),
            Annotation.Callout("a5", Vec3.ZERO, Vec3(900.0, 900.0, 0.0), "120mm blockwork"),
            Annotation.Stamp("a6", Vec3(50.0, 50.0, 0.0), "APPROVED", rotationDegrees = 15.0),
            Annotation.Comment("a7", Vec3(5.0, 5.0, 0.0), "Client asked for a wider opening"),
        ),
        measurements = listOf(
            StoredMeasurement.from(Measurement.Distance("m1", Vec3.ZERO, Vec3(5500.0, 0.0, 0.0))),
            StoredMeasurement.from(
                Measurement.Area(
                    "m2",
                    listOf(
                        Vec3(0.0, 0.0, 0.0),
                        Vec3(5500.0, 0.0, 0.0),
                        Vec3(5500.0, 4500.0, 0.0),
                        Vec3(0.0, 4500.0, 0.0),
                    ),
                ),
                note = "living room",
            ),
        ),
        layers = listOf(
            LayerState("l1", "Walls", visible = true, opacity = 1.0, colour = "#C97D5D"),
            LayerState("l2", "Furniture", visible = false, opacity = 0.45),
        ),
        materials = listOf(
            MaterialOverride("mesh-7", "oak", "Oak", "#9A7B4F", roughness = 0.5),
        ),
        cameras = listOf(
            CameraPreset("c1", "Plan", Vec3(0.0, 0.0, 10_000.0), Vec3.ZERO, fovDegrees = null, orthoHeight = 6000.0),
            CameraPreset("c2", "Isometric", Vec3(8000.0, -8000.0, 6000.0), Vec3.ZERO),
        ),
        thumbnail = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
    )
}

class PaftaContainerTest {

    @Test
    fun `a project round trips through the container`() {
        val original = sampleProject()
        val bytes = ByteArrayOutputStream().also { PaftaContainer.write(original, it) }.toByteArray()
        val reread = PaftaContainer.read(ByteArrayInputStream(bytes))
        assertEquals(original, reread)
    }

    @Test
    fun `the payload survives byte for byte`() {
        val original = sampleProject()
        val bytes = ByteArrayOutputStream().also { PaftaContainer.write(original, it) }.toByteArray()
        val reread = PaftaContainer.read(ByteArrayInputStream(bytes))
        assertTrue(original.payload.contentEquals(reread.payload))
        assertEquals("ground-floor.dxf", reread.manifest.source.fileName)
        assertEquals("dxf", reread.manifest.source.format)
        assertEquals(original.payload.size.toLong(), reread.manifest.source.sizeBytes)
    }

    @Test
    fun `every annotation kind survives the round trip with its subtype intact`() {
        val bytes = ByteArrayOutputStream()
            .also { PaftaContainer.write(sampleProject(), it) }.toByteArray()
        val reread = PaftaContainer.read(ByteArrayInputStream(bytes))

        assertEquals(
            AnnotationKind.entries.toSet(),
            reread.annotations.map { it.kind }.toSet(),
        )
        assertIs<Annotation.Text>(reread.annotations.first { it.id == "a1" })
        assertIs<Annotation.Stamp>(reread.annotations.first { it.id == "a6" })
        val dim = reread.annotations.first { it.id == "a4" }
        assertIs<Annotation.Dimension>(dim)
        assertEquals(5500.0, dim.to.x, 1e-9)
        assertEquals(300.0, dim.offsetMm, 1e-9)
    }

    @Test
    fun `measurements rebuild into domain objects`() {
        val bytes = ByteArrayOutputStream()
            .also { PaftaContainer.write(sampleProject(), it) }.toByteArray()
        val reread = PaftaContainer.read(ByteArrayInputStream(bytes))

        val distance = reread.measurements.first { it.id == "m1" }.toMeasurement()
        assertIs<Measurement.Distance>(distance)
        assertEquals(5500.0, distance.rawValue, 1e-9)
        assertEquals("5500mm", distance.label())

        val area = reread.measurements.first { it.id == "m2" }.toMeasurement()
        assertIs<Measurement.Area>(area)
        assertEquals("24.75m²", area.label())
        assertEquals("living room", reread.measurements.first { it.id == "m2" }.note)
    }

    @Test
    fun `a malformed measurement record is dropped rather than crashing the open`() {
        assertNull(StoredMeasurement("x", "AREA", listOf(Vec3.ZERO, Vec3.X)).toMeasurement())
        assertNull(StoredMeasurement("x", "NOT_A_KIND", listOf(Vec3.ZERO, Vec3.X)).toMeasurement())
        assertNull(StoredMeasurement("x", "DISTANCE", listOf(Vec3.ZERO)).toMeasurement())
        assertEquals(
            MeasurementKind.POLYLINE,
            StoredMeasurement("x", "POLYLINE", listOf(Vec3.ZERO, Vec3.X)).toMeasurement()?.kind,
        )
    }

    @Test
    fun `layer opacity is exposed as a percentage for the palette`() {
        assertEquals(45, LayerState("l", "Furniture", opacity = 0.45).opacityPercent)
        assertEquals(100, LayerState("l", "Walls").opacityPercent)
    }

    @Test
    fun `an out of range opacity is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { LayerState("l", "x", opacity = 1.5) }
        assertFailsWith<IllegalArgumentException> { LayerState("l", "x", opacity = -0.1) }
    }

    @Test
    fun `camera presets keep their projection kind`() {
        val bytes = ByteArrayOutputStream()
            .also { PaftaContainer.write(sampleProject(), it) }.toByteArray()
        val reread = PaftaContainer.read(ByteArrayInputStream(bytes))
        val plan = reread.cameras.first { it.name == "Plan" }
        assertTrue(plan.isOrthographic)
        assertEquals(6000.0, plan.orthoHeight)
        assertTrue(!reread.cameras.first { it.name == "Isometric" }.isOrthographic)
    }

    @Test
    fun `writing to disk is atomic and leaves no temp file behind`() {
        val dir = createTempDirectory("pafta-test").toFile()
        try {
            val file = File(dir, "project.pafta")
            PaftaContainer.write(sampleProject(), file)
            assertTrue(file.exists())
            assertEquals(
                emptyList(),
                dir.listFiles()!!.filter { it.name.endsWith(".tmp") }.map { it.name },
            )
            assertEquals(sampleProject(), PaftaContainer.read(file))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a save over an existing project replaces it`() {
        val dir = createTempDirectory("pafta-test").toFile()
        try {
            val file = File(dir, "project.pafta")
            PaftaContainer.write(sampleProject(), file)
            val edited = sampleProject().let {
                it.copy(manifest = it.manifest.copy(projectName = "Unit 102 - Lvl 3"))
            }
            PaftaContainer.write(edited, file)
            assertEquals("Unit 102 - Lvl 3", PaftaContainer.readManifest(file).projectName)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the manifest can be read alone for the file list`() {
        val dir = createTempDirectory("pafta-test").toFile()
        try {
            val file = File(dir, "project.pafta")
            PaftaContainer.write(sampleProject(), file)
            val m = PaftaContainer.readManifest(file)
            assertEquals("Unit 101 - Lvl 2", m.projectName)
            assertEquals(PAFTA_FORMAT_VERSION, m.formatVersion)
            assertEquals(LengthUnit.MILLIMETRE, m.units.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a project with no overlays still opens`() {
        val bare = newProject("Bare", "model.glb", byteArrayOf(1, 2, 3), 0)
        val bytes = ByteArrayOutputStream().also { PaftaContainer.write(bare, it) }.toByteArray()
        val reread = PaftaContainer.read(ByteArrayInputStream(bytes))
        assertEquals(bare, reread)
        assertTrue(reread.annotations.isEmpty())
        assertNull(reread.thumbnail)
        assertEquals("glb", reread.manifest.source.format)
    }

    @Test
    fun `a zip that is not a pafta project is reported clearly`() {
        val notPafta = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val e = assertFailsWith<PaftaFormatException> {
            PaftaContainer.read(ByteArrayInputStream(notPafta))
        }
        assertTrue(e.message!!.contains("manifest.json"), "message was: ${e.message}")
    }

    @Test
    fun `a project missing its payload is reported rather than opened half empty`() {
        val noPayload = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(
                    """
                    {"formatVersion":1,"projectName":"x",
                     "source":{"fileName":"a.dxf","format":"dxf"}}
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }
        }.toByteArray()

        val e = assertFailsWith<PaftaFormatException> {
            PaftaContainer.read(ByteArrayInputStream(noPayload))
        }
        assertTrue(e.message!!.contains("payload"), "message was: ${e.message}")
    }

    @Test
    fun `a file from a newer format version is refused with an explanation`() {
        val future = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(
                    """
                    {"formatVersion":99,"projectName":"x",
                     "source":{"fileName":"a.dxf","format":"dxf"}}
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("payload/a.dxf"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }.toByteArray()

        val e = assertFailsWith<PaftaFormatException> {
            PaftaContainer.read(ByteArrayInputStream(future))
        }
        assertTrue(e.message!!.contains("newer version"), "message was: ${e.message}")
    }

    @Test
    fun `corrupt overlay json names the entry that failed`() {
        val corrupt = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("{ this is not json".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val e = assertFailsWith<PaftaFormatException> {
            PaftaContainer.read(ByteArrayInputStream(corrupt))
        }
        assertTrue(e.message!!.contains("manifest.json"), "message was: ${e.message}")
    }

    @Test
    fun `unknown json fields from a future build are ignored rather than fatal`() {
        val withExtra = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(
                    """
                    {"formatVersion":1,"projectName":"x","somethingNew":true,
                     "source":{"fileName":"a.dxf","format":"dxf","alsoNew":7}}
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("payload/a.dxf"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }.toByteArray()

        val p = PaftaContainer.read(ByteArrayInputStream(withExtra))
        assertEquals("x", p.manifest.projectName)
    }
}
