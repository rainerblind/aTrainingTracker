package com.atrainingtracker.trainingtracker.routes

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [WaypointType.fromGpx] and [WaypointType.fromTcx] classification heuristics,
 * verifying description parsing and outdoor keyword recognition (REQ-MAP-026, TST-MAP-028.3).
 */
class WaypointTypeClassificationTest {

    @Test
    fun testFromGpx_withDescriptionOnly_classifiesCorrectly() {
        // Name is generic, but description has "Unterstand"
        val type = WaypointType.fromGpx(sym = "Circle, Red", type = "user", name = "Start Tour 13", desc = "Unterstand")
        assertEquals(WaypointType.POI_BENCH, type)
    }

    @Test
    fun testFromGpx_picnicAndBenches_classifiesAsPoiBench() {
        val type1 = WaypointType.fromGpx(sym = "Picnic Area", type = null, name = "Holz-Sitzgelegenheit am Gansteichweg")
        assertEquals(WaypointType.POI_BENCH, type1)

        val type2 = WaypointType.fromGpx(sym = "Flag, Blue", type = null, name = "Rastplatz mit Aussichtsbank")
        assertEquals(WaypointType.POI_BENCH, type2)
    }

    @Test
    fun testFromGpx_summitsAndPasses_classifiesAsPoiSummit() {
        val type1 = WaypointType.fromGpx(sym = "Summit", type = null, name = "Gipfelkreuz auf dem Jusi")
        assertEquals(WaypointType.POI_SUMMIT, type1)

        val type2 = WaypointType.fromGpx(sym = "Flag, Blue", type = null, name = "Passhöhe Stilfser Joch")
        assertEquals(WaypointType.POI_SUMMIT, type2)
    }

    @Test
    fun testFromGpx_foodAndShelters_classifiesAsPoiFood() {
        val type1 = WaypointType.fromGpx(sym = "Fishing Hot Spot Facility", type = null, name = "Schutzhütte und Grillplatz")
        assertEquals(WaypointType.POI_FOOD, type1)

        val type2 = WaypointType.fromGpx(sym = "Restaurant", type = null, name = "Gasthof Hirsch")
        assertEquals(WaypointType.POI_FOOD, type2)
    }

    @Test
    fun testFromGpx_viewpoints_classifiesAsPoiViewpoint() {
        val type1 = WaypointType.fromGpx(sym = "Flag, Blue", type = null, name = "Panoramablick vom Jusi")
        assertEquals(WaypointType.POI_VIEWPOINT, type1)

        val type2 = WaypointType.fromGpx(sym = "Flag, Blue", type = null, name = "Aussichtspunkt Floriansberg")
        assertEquals(WaypointType.POI_VIEWPOINT, type2)

        val type3 = WaypointType.fromGpx(sym = "Flag, Blue", type = null, name = "Blick auf Bartholomä und den Wirtsberg")
        assertEquals(WaypointType.POI_VIEWPOINT, type3)
    }

    @Test
    fun testFromGpx_water_classifiesAsPoiWater() {
        val type = WaypointType.fromGpx(sym = "Water", type = null, name = "Trinkbrunnen Dorfplatz")
        assertEquals(WaypointType.POI_WATER, type)
    }

    @Test
    fun testFromTcx_mapsStandardPointTypes() {
        assertEquals(WaypointType.POI_WATER, WaypointType.fromTcx("Water"))
        assertEquals(WaypointType.POI_FOOD, WaypointType.fromTcx("Food"))
        assertEquals(WaypointType.POI_SUMMIT, WaypointType.fromTcx("Summit"))
        assertEquals(WaypointType.POI_VIEWPOINT, WaypointType.fromTcx("Valley"))
        assertEquals(WaypointType.POI_DANGER, WaypointType.fromTcx("Danger"))
        assertEquals(WaypointType.POI_FIRST_AID, WaypointType.fromTcx("First Aid"))
        assertEquals(WaypointType.TURN_LEFT, WaypointType.fromTcx("Left"))
        assertEquals(WaypointType.TURN_RIGHT, WaypointType.fromTcx("Right"))
        assertEquals(WaypointType.TURN_STRAIGHT, WaypointType.fromTcx("Straight"))
        assertEquals(WaypointType.GENERIC, WaypointType.fromTcx("Generic"))
    }
}
