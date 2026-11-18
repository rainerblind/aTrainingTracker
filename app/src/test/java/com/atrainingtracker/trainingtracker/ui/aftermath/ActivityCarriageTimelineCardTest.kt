package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

/**
 * Behavioral Compose UI verification suite for [ActivityCarriageTimelineCard] and
 * [WorkoutDetailTabbedContent] `DETAILS` slotting (`TST-PER-053.4`, `ATT-8916`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class ActivityCarriageTimelineCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
    }

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
    }

    private fun bakeryTourTimeline(): ActivityCarriageTimeline =
        ActivityCarriageTimeline(
            segments = listOf(
                ActivityCarriageSegment(
                    mode = PhoneCarriageMode.BIKE_HANDLEBAR,
                    startSec = 0L,
                    endSec = 600L,
                    durationSeconds = 600L,
                    distanceMeters = 3500.0,
                    durationPercentage = 50.0f,
                    distancePercentage = 50.0f
                ),
                ActivityCarriageSegment(
                    mode = PhoneCarriageMode.WALKING_TRANSITION,
                    startSec = 600L,
                    endSec = 720L,
                    durationSeconds = 120L,
                    distanceMeters = 150.0,
                    durationPercentage = 10.0f,
                    distancePercentage = 2.14f
                ),
                ActivityCarriageSegment(
                    mode = PhoneCarriageMode.STATIONARY,
                    startSec = 720L,
                    endSec = 840L,
                    durationSeconds = 120L,
                    distanceMeters = 0.0,
                    durationPercentage = 10.0f,
                    distancePercentage = 0.0f
                ),
                ActivityCarriageSegment(
                    mode = PhoneCarriageMode.BIKE_HANDLEBAR,
                    startSec = 840L,
                    endSec = 1080L,
                    durationSeconds = 240L,
                    distanceMeters = 2150.0,
                    durationPercentage = 20.0f,
                    distancePercentage = 30.71f
                ),
                ActivityCarriageSegment(
                    mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                    startSec = 1080L,
                    endSec = 1200L,
                    durationSeconds = 120L,
                    distanceMeters = 1200.0,
                    durationPercentage = 10.0f,
                    distancePercentage = 17.15f
                )
            ),
            modeSummaries = listOf(
                ActivityCarriageModeSummary(
                    mode = PhoneCarriageMode.BIKE_HANDLEBAR,
                    durationSeconds = 840L,
                    distanceMeters = 5650.0,
                    durationPercentage = 70.0f,
                    distancePercentage = 80.71f,
                    segmentCount = 2
                ),
                ActivityCarriageModeSummary(
                    mode = PhoneCarriageMode.WALKING_TRANSITION,
                    durationSeconds = 120L,
                    distanceMeters = 150.0,
                    durationPercentage = 10.0f,
                    distancePercentage = 2.14f,
                    segmentCount = 1
                ),
                ActivityCarriageModeSummary(
                    mode = PhoneCarriageMode.STATIONARY,
                    durationSeconds = 120L,
                    distanceMeters = 0.0,
                    durationPercentage = 10.0f,
                    distancePercentage = 0.0f,
                    segmentCount = 1
                ),
                ActivityCarriageModeSummary(
                    mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                    durationSeconds = 120L,
                    distanceMeters = 1200.0,
                    durationPercentage = 10.0f,
                    distancePercentage = 17.15f,
                    segmentCount = 1
                )
            ),
            totalDurationSeconds = 1200L,
            totalDistanceMeters = 7000.0,
            startSec = 0L,
            endSec = 1200L
        )

    private fun zeroDistanceIndoorTimeline(): ActivityCarriageTimeline =
        ActivityCarriageTimeline(
            segments = listOf(
                ActivityCarriageSegment(
                    mode = PhoneCarriageMode.RUN_WAIST_HIP,
                    startSec = 0L,
                    endSec = 180L,
                    durationSeconds = 180L,
                    distanceMeters = 0.0,
                    durationPercentage = 75.0f,
                    distancePercentage = 0.0f
                ),
                ActivityCarriageSegment(
                    mode = PhoneCarriageMode.STATIONARY,
                    startSec = 180L,
                    endSec = 240L,
                    durationSeconds = 60L,
                    distanceMeters = 0.0,
                    durationPercentage = 25.0f,
                    distancePercentage = 0.0f
                )
            ),
            modeSummaries = listOf(
                ActivityCarriageModeSummary(
                    mode = PhoneCarriageMode.RUN_WAIST_HIP,
                    durationSeconds = 180L,
                    distanceMeters = 0.0,
                    durationPercentage = 75.0f,
                    distancePercentage = 0.0f,
                    segmentCount = 1
                ),
                ActivityCarriageModeSummary(
                    mode = PhoneCarriageMode.STATIONARY,
                    durationSeconds = 60L,
                    distanceMeters = 0.0,
                    durationPercentage = 25.0f,
                    distancePercentage = 0.0f,
                    segmentCount = 1
                )
            ),
            totalDurationSeconds = 240L,
            totalDistanceMeters = 0.0,
            startSec = 0L,
            endSec = 240L
        )

    private fun sampleWorkoutData(): WorkoutData = WorkoutData(
        id = 8916L,
        finished = true,
        fileBaseName = "2026-11-18-09-00-00",
        workoutName = "Bakery Tour",
        sportId = 1L,
        sportName = "Cycling",
        bSportType = BSportType.BIKE,
        startTimeS = 1700000000L,
        formattedDate = "2026-11-18",
        formattedTime = "09:00",
        localDateTime = LocalDateTime.of(2026, 11, 18, 9, 0),
        equipmentName = "Gravel",
        equipmentId = 1L,
        commute = false,
        trainer = false,
        mapPolyline = "",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 7000.0,
        maxDisplacement = 2500.0,
        activeTimeSec = 1200L,
        totalTimeSec = 1200L,
        avgSpeedMps = 5.83,
        ascentMeters = 45L,
        descentMeters = 45L,
        minAltitude = 510.0,
        maxAltitude = 540.0,
        startLatLng = null,
        endLatLng = null,
        maxDisplacementLatLng = null,
        description = null,
        goal = null,
        method = null,
        stravaSportName = null
    )

    @Test
    fun testCard_rendersProportionalBarSummaryAndTogglesChronologicalBreakdown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val timeline = bakeryTourTimeline()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.width(420.dp)) {
                    ActivityCarriageTimelineCard(timeline = timeline)
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.carriage_timeline_card_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.carriage_timeline_phase_count, 5))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("carriage_timeline_bar").assertIsDisplayed()

        // Proportional bar check: segment 0 (600s) is strictly wider than segment 1 (120s)
        val seg0Bounds = composeTestRule.onNodeWithTag("carriage_timeline_segment_0").fetchSemanticsNode().boundsInRoot
        val seg1Bounds = composeTestRule.onNodeWithTag("carriage_timeline_segment_1").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "600s segment (${seg0Bounds.width}) must be wider than 120s segment (${seg1Bounds.width})",
            seg0Bounds.width > seg1Bounds.width * 3f
        )

        // Repeat badge for BIKE_HANDLEBAR (2 visits)
        composeTestRule.onNodeWithText(context.getString(R.string.carriage_timeline_segment_repeats, 2))
            .assertIsDisplayed()

        // Initially collapsed: chronological phase rows must not exist
        composeTestRule.onNodeWithTag("carriage_phase_row_0").assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.carriage_timeline_show_phases))
            .assertIsDisplayed()

        // Click expand toggle -> shows all 5 chronological rows + Hide label
        composeTestRule.onNodeWithTag("carriage_breakdown_toggle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.carriage_timeline_hide_phases))
            .assertIsDisplayed()
        for (idx in 0..4) {
            composeTestRule.onNodeWithTag("carriage_phase_row_$idx").assertIsDisplayed()
        }
        composeTestRule.onNodeWithText(
            context.getString(R.string.carriage_timeline_time_range, "10:00", "12:00")
        ).assertIsDisplayed()
    }

    @Test
    fun testCard_zeroDistanceIndoorWorkout_rendersSafelyWithDurationWeights() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val timeline = zeroDistanceIndoorTimeline()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.width(420.dp)) {
                    ActivityCarriageTimelineCard(timeline = timeline, initiallyExpanded = true)
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.carriage_timeline_card_title))
            .assertIsDisplayed()
        val seg0Bounds = composeTestRule.onNodeWithTag("carriage_timeline_segment_0").fetchSemanticsNode().boundsInRoot
        val seg1Bounds = composeTestRule.onNodeWithTag("carriage_timeline_segment_1").fetchSemanticsNode().boundsInRoot
        assertTrue(seg0Bounds.width > seg1Bounds.width * 2f)
        composeTestRule.onNodeWithTag("carriage_phase_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("carriage_phase_row_1").assertIsDisplayed()
    }

    private fun setWorkoutDetailContent(activityCarriageTimeline: ActivityCarriageTimeline?) {
        val tabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_overview",
                preset = WorkoutDetailTabPreset.OVERVIEW,
                sections = listOf(WorkoutDetailSectionType.DETAILS)
            )
        )
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    WorkoutDetailTabbedContent(
                        workoutData = sampleWorkoutData(),
                        tabs = tabs,
                        availability = WorkoutDetailSectionAvailability(hasDetails = true),
                        detailPreferences = WorkoutDetailPreferences(),
                        tuningConfig = TuningConfig(),
                        activeScrubPath = null,
                        hasAltitudeData = false,
                        splitChartData = null,
                        selectedLapNr = null,
                        onSelectLapNr = {},
                        hrZoneDistribution = null,
                        powerZoneDistribution = null,
                        effectiveHrZoneDisplayMode = com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode.HISTOGRAM,
                        effectivePowerZoneDisplayMode = com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode.HISTOGRAM,
                        onHrZoneDisplayModeChange = {},
                        onPowerZoneDisplayModeChange = {},
                        useStatusBarsPadding = false,
                        onClusterClick = null,
                        onEditWorkout = null,
                        headerActions = {},
                        onOpenTabsConfig = {},
                        mapContent = {},
                        mapOverlay = {},
                        activityCarriageTimeline = activityCarriageTimeline
                    )
                }
            }
        }
    }

    @Test
    fun testWorkoutDetailTabbedContent_detailsSectionMountsTimelineCard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setWorkoutDetailContent(bakeryTourTimeline())
        composeTestRule.onNodeWithText(context.getString(R.string.carriage_timeline_card_title))
            .assertIsDisplayed()
    }

    @Test
    fun testWorkoutDetailTabbedContent_detailsSectionOmitsCardWhenNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setWorkoutDetailContent(null)
        composeTestRule.onNodeWithText(context.getString(R.string.carriage_timeline_card_title))
            .assertDoesNotExist()
    }

    @Test
    fun testCard_generateLightAndDarkPreviewPng() {
        val timeline = bakeryTourTimeline()

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(880.dp)
                    .testTag("CarriageTimelineLightDarkPreview"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            ActivityCarriageTimelineCard(
                                timeline = timeline,
                                initiallyExpanded = true
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            ActivityCarriageTimelineCard(
                                timeline = timeline,
                                initiallyExpanded = true
                            )
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("CarriageTimelineLightDarkPreview")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8916_carriage_timeline.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
