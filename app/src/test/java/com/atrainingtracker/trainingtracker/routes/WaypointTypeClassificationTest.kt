package com.atrainingtracker.trainingtracker.routes

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [WaypointType.fromGpx] and [WaypointType.fromTcx] classification heuristics,
 * verifying description parsing, outdoor keyword recognition, and disambiguation (REQ-MAP-026, REQ-MAP-033, TST-MAP-035.1).
 */
class WaypointTypeClassificationTest {

    @Test
    fun testFromGpx_withDescriptionOnly_classifiesCorrectly() {
        // Name is generic, but description has "Unterstand" -> now classified as POI_SHELTER
        val type = WaypointType.fromGpx(sym = "Circle, Red", type = "user", name = "Start Tour 13", desc = "Unterstand")
        assertEquals(WaypointType.POI_SHELTER, type)
    }

    @Test
    fun testFromGpx_shelters_classifiesAsPoiShelter() {
        val type1 = WaypointType.fromGpx(sym = "Fishing Hot Spot Facility", type = null, name = "Schutzhütte und Grillplatz")
        assertEquals(WaypointType.POI_SHELTER, type1)

        val type2 = WaypointType.fromGpx(sym = null, type = null, name = "Unterstand am Waldrand")
        assertEquals(WaypointType.POI_SHELTER, type2)

        val type3 = WaypointType.fromGpx(sym = null, type = null, name = "Refuge du Mont Blanc")
        assertEquals(WaypointType.POI_SHELTER, type3)

        val type4 = WaypointType.fromGpx(sym = null, type = null, name = "Biwakschachtel")
        assertEquals(WaypointType.POI_SHELTER, type4)
    }

    @Test
    fun testFromGpx_kreuzung_doesNotClassifyAsSummit() {
        // "Kreuzung" must NOT match summit "kreuz" false positive
        val type1 = WaypointType.fromGpx(sym = null, type = null, name = "Kreuzung Waldweg")
        assertEquals(WaypointType.GENERIC, type1)

        val type2 = WaypointType.fromGpx(sym = null, type = null, name = "Wegweiser an der Kreuzung", desc = "Große Kreuzung")
        assertEquals(WaypointType.GENERIC, type2)

        // But genuine summit crosses MUST match
        val type3 = WaypointType.fromGpx(sym = null, type = null, name = "Gipfelkreuz auf dem Jusi")
        assertEquals(WaypointType.POI_SUMMIT, type3)

        val type4 = WaypointType.fromGpx(sym = null, type = null, name = "Kreuz am Gipfel")
        assertEquals(WaypointType.POI_SUMMIT, type4)
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
    fun testFromGpx_food_classifiesAsPoiFood() {
        val type1 = WaypointType.fromGpx(sym = "Restaurant", type = null, name = "Gasthof Hirsch")
        assertEquals(WaypointType.POI_FOOD, type1)

        val type2 = WaypointType.fromGpx(sym = "Cafe", type = null, name = "Café am Markt")
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
        assertEquals(WaypointType.POI_SHELTER, WaypointType.fromTcx("Shelter"))
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
