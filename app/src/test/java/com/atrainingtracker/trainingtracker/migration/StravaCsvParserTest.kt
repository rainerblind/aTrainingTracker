/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/**
 * Unit tests for language-independent RFC 4180 Strava CSV metadata parsing (REQ-MIG-048.2 / TST-MIG-046.1).
 */
class StravaCsvParserTest {

    @Test
    fun testParseCsv_rfc4180MultilineAndEscapedQuotes() {
        val csv = """
            col1,col2,col3
            "simple",normal,"with, comma"
            "multi
            line","said ""hello"" world",123
        """.trimIndent()

        val records = StravaCsvParser.parseCsv(StringReader(csv))
        assertEquals(3, records.size)

        assertEquals(listOf("col1", "col2", "col3"), records[0])
        assertEquals(listOf("simple", "normal", "with, comma"), records[1])
        assertEquals("multi\nline", records[2][0])
        assertEquals("said \"hello\" world", records[2][1])
        assertEquals("123", records[2][2])
    }

    @Test
    fun testParseActivities_germanHeaders() {
        val csv = """
            Aktivitäts-ID,Aktivitätsdatum,Name der Aktivität,Aktivitätsart,Aktivitätsbeschreibung,Pendeln,Hinweis zur Privatsphäre für Aktivitäten,Aktivitätsausrüstung,Dateiname,Verstrichene Zeit,Distanz
            20811350233,"01.11.2026, 01:46:43",Runde ab Zu Hause,Radfahrt,"Teilweise bewölkt, 15°C",false,Private Training Note,Canyon Endurance,activities/21975890558.tcx.gz,1800,25000.5
            20811350234,"02.11.2026, 08:30:00",Lauf am Morgen,Lauf,"Schöner Morgenlauf",true,,Nike Pegasus,activities/21975890559.fit.gz,1200,5000.0
        """.trimIndent()

        val activities = StravaCsvParser.parseActivities(StringReader(csv))
        assertEquals(2, activities.size)

        val ride = activities[0]
        assertEquals("20811350233", ride.activityId)
        assertEquals("01.11.2026, 01:46:43", ride.activityDate)
        assertEquals("Runde ab Zu Hause", ride.activityName)
        assertEquals(BSportType.BIKE, ride.bSportType)
        assertEquals("Teilweise bewölkt, 15°C", ride.activityDescription)
        assertEquals("Private Training Note", ride.privateNote)
        assertFalse(ride.commute)
        assertEquals("Canyon Endurance", ride.gearName)
        assertEquals("activities/21975890558.tcx.gz", ride.filename)

        val run = activities[1]
        assertEquals("20811350234", run.activityId)
        assertEquals("Lauf am Morgen", run.activityName)
        assertEquals(BSportType.RUN, run.bSportType)
        assertTrue(run.commute)
        assertEquals("Nike Pegasus", run.gearName)
        assertEquals("activities/21975890559.fit.gz", run.filename)
        assertNull(run.privateNote)
    }

    @Test
    fun testParseActivities_englishHeaders() {
        val csv = """
            Activity ID,Activity Date,Activity Name,Activity Type,Activity Description,Commute,Activity Private Note,Activity Gear,Filename,Elapsed Time,Distance
            99887766,"Nov 2, 2026, 10:00:00 AM",Gravel Explorer,Gravel Ride,"Epic gravel route",true,Target Zone 2,Specialized Diverge,activities/99887766.gpx.gz,3600,45000.0
        """.trimIndent()

        val activities = StravaCsvParser.parseActivities(StringReader(csv))
        assertEquals(1, activities.size)

        val row = activities[0]
        assertEquals("99887766", row.activityId)
        assertEquals("Nov 2, 2026, 10:00:00 AM", row.activityDate)
        assertEquals("Gravel Explorer", row.activityName)
        assertEquals(BSportType.BIKE, row.bSportType)
        assertEquals("Epic gravel route", row.activityDescription)
        assertEquals("Target Zone 2", row.privateNote)
        assertTrue(row.commute)
        assertEquals("Specialized Diverge", row.gearName)
        assertEquals("activities/99887766.gpx.gz", row.filename)
    }

    @Test
    fun testParseActivities_frenchSpanishJapanese_fixedIndicesAndPattern() {
        // French Strava export layout (with canonical 13+ column structure)
        val frenchCsv = """
            Identifiant de l'activité,Date de l'activité,Nom de l'activité,Type d'activité,Description de l'activité,Champ5,Champ6,Champ7,Champ8,Trajet domicile-travail,Note privée,Équipement de l'activité,Nom du fichier
            55443322,"03.11.2026, 14:00:00","Sortie vélo du soir","Sortie vélo","Belle balade",,,,,oui,"Note secrète","Vélo de course","activities/55443322.fit.gz"
        """.trimIndent()

        val frenchActivities = StravaCsvParser.parseActivities(StringReader(frenchCsv))
        assertEquals(1, frenchActivities.size)
        val frRow = frenchActivities[0]
        assertEquals("55443322", frRow.activityId)
        assertEquals("03.11.2026, 14:00:00", frRow.activityDate)
        assertEquals("Sortie vélo du soir", frRow.activityName)
        assertEquals(BSportType.BIKE, frRow.bSportType)
        assertEquals("Belle balade", frRow.activityDescription)
        assertTrue(frRow.commute)
        assertEquals("Note secrète", frRow.privateNote)
        assertEquals("Vélo de course", frRow.gearName)
        assertEquals("activities/55443322.fit.gz", frRow.filename)

        // Spanish / Unknown headers relying strictly on fixed canonical indices:
        // Col 0: ID, Col 1: Date, Col 2: Name, Col 3: Type, Col 4: Desc, Col 9: Commute, Col 10: Note, Col 11: Gear, Col 12: Filename
        val spanishCsv = """
            C0,C1,C2,C3,C4,C5,C6,C7,C8,C9,C10,C11,C12
            88776655,"04.11.2026, 09:00:00","Carrera matutina","Carrera","Entrenamiento suave",,,,,si,"Objetivo 5k","Zapatillas Asics","activities/88776655.tcx.gz"
        """.trimIndent()

        val spanishActivities = StravaCsvParser.parseActivities(StringReader(spanishCsv))
        assertEquals(1, spanishActivities.size)
        val esRow = spanishActivities[0]
        assertEquals("88776655", esRow.activityId)
        assertEquals("Carrera matutina", esRow.activityName)
        assertEquals(BSportType.RUN, esRow.bSportType)
        assertTrue(esRow.commute)
        assertEquals("Objetivo 5k", esRow.privateNote)
        assertEquals("Zapatillas Asics", esRow.gearName)
        assertEquals("activities/88776655.tcx.gz", esRow.filename)

        // Japanese Strava export with Japanese characters
        val japaneseCsv = """
            アクティビティID,アクティビティ日時,アクティビティ名,アクティビティタイプ,説明,C5,C6,C7,C8,通勤,プライベートメモ,ギア,ファイル名
            77665544,"2026/11/05 07:00:00","朝のランニング","ランニング","爽快な朝",,,,,はい,"心拍管理","ミズノ","activities/77665544.gpx.gz"
        """.trimIndent()

        val japaneseActivities = StravaCsvParser.parseActivities(StringReader(japaneseCsv))
        assertEquals(1, japaneseActivities.size)
        val jaRow = japaneseActivities[0]
        assertEquals("77665544", jaRow.activityId)
        assertEquals("朝のランニング", jaRow.activityName)
        assertEquals(BSportType.RUN, jaRow.bSportType)
        assertTrue(jaRow.commute)
        assertEquals("心拍管理", jaRow.privateNote)
        assertEquals("ミズノ", jaRow.gearName)
        assertEquals("activities/77665544.gpx.gz", jaRow.filename)
    }

    @Test
    fun testParseActivities_shiftedFilenameColumn_patternDetection() {
        // Filename is at column 5 instead of canonical 12, without matching alias
        val csv = """
            ColA,ColB,ColC,ColD,ColE,ColF,ColG
            112233,"01.01.2026","Test Title","Ride","Some Notes","activities/112233.fit.gz","Extra"
        """.trimIndent()

        val activities = StravaCsvParser.parseActivities(StringReader(csv))
        assertEquals(1, activities.size)
        val row = activities[0]
        assertEquals("112233", row.activityId)
        assertEquals("Test Title", row.activityName)
        assertEquals(BSportType.BIKE, row.bSportType)
        assertEquals("activities/112233.fit.gz", row.filename)
    }

    @Test
    fun testParseBikesAndShoes_germanAndEnglish() {
        val bikesCsv = """
            Fahrradname,Fahrradmarke,Fahrradmodell,Fahrrad-Standardsportarten
            Endurace,Canyon,Endurace AL,Radfahren
            ,Trek,Domane,
        """.trimIndent()

        val bikes = StravaCsvParser.parseBikes(StringReader(bikesCsv))
        assertEquals(2, bikes.size)
        assertEquals("Endurace", bikes[0].name)
        assertEquals("Canyon", bikes[0].brand)
        assertEquals("Trek Domane", bikes[1].name)
        assertTrue(bikes[0].isBike)

        val shoesCsv = """
            Shoe Name,Shoe Brand,Shoe Model,Shoe Default Sports
            Speedcross,Salomon,Speedcross 5,Running
            ,Brooks,Ghost 14,
        """.trimIndent()

        val shoes = StravaCsvParser.parseShoes(StringReader(shoesCsv))
        assertEquals(2, shoes.size)
        assertEquals("Speedcross", shoes[0].name)
        assertEquals("Brooks Ghost 14", shoes[1].name)
        assertFalse(shoes[0].isBike)
    }

    @Test
    fun testParseBikesAndShoes_fixedIndicesFallback() {
        // Unknown or localized headers (e.g. French / Spanish / arbitrary)
        val bikesCsv = """
            Nom,Marque,Modèle,Autre
            Aethos,Specialized,S-Works,Route
            ,Pinarello,Dogma F,
        """.trimIndent()

        val bikes = StravaCsvParser.parseBikes(StringReader(bikesCsv))
        assertEquals(2, bikes.size)
        assertEquals("Aethos", bikes[0].name)
        assertEquals("Specialized", bikes[0].brand)
        assertEquals("Pinarello Dogma F", bikes[1].name)
        assertTrue(bikes[0].isBike)

        val shoesCsv = """
            C0,C1,C2,C3
            Vaporfly,Nike,Next% 2,Asfalto
            ,Hoka,Clifton 9,
        """.trimIndent()

        val shoes = StravaCsvParser.parseShoes(StringReader(shoesCsv))
        assertEquals(2, shoes.size)
        assertEquals("Vaporfly", shoes[0].name)
        assertEquals("Hoka Clifton 9", shoes[1].name)
        assertFalse(shoes[0].isBike)
    }

    @Test
    fun testMapSportType() {
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("Ride"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("Radfahrt"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("VirtualRide"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("Gravel Ride"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("E-Mountainbike-Fahrt"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("Sortie vélo"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("Ciclismo"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("Pedalata"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("Fietsrit"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("Jazda na rowerze"))
        assertEquals(BSportType.BIKE, StravaCsvParser.mapSportType("サイクリング"))

        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Run"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Lauf"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Walk"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Spaziergang"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Hike"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Wanderung"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Course à pied"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Carrera"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Camminata"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Hardlopen"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("Bieg"))
        assertEquals(BSportType.RUN, StravaCsvParser.mapSportType("ランニング"))

        assertEquals(BSportType.UNKNOWN, StravaCsvParser.mapSportType("Workout"))
        assertEquals(BSportType.UNKNOWN, StravaCsvParser.mapSportType("Yoga"))
        assertEquals(BSportType.UNKNOWN, StravaCsvParser.mapSportType(null))
    }

    @Test
    fun testParseActivities_mediaColumnGermanAndEnglish() {
        val germanCsv = """
            Aktivitäts-ID,Aktivitätsdatum,Name der Aktivität,Aktivitätsart,Medien
            1001,"01.05.2024, 10:00:00",Trail Run,Lauf,"media/pic1.jpg|media/pic2.jpg"
            1002,"02.05.2024, 12:00:00",Bike Ride,Radfahrt,
        """.trimIndent()

        val germanActivities = StravaCsvParser.parseActivities(StringReader(germanCsv))
        assertEquals(2, germanActivities.size)
        assertEquals(listOf("media/pic1.jpg", "media/pic2.jpg"), germanActivities[0].media)
        assertTrue(germanActivities[1].media.isEmpty())

        val englishCsv = """
            Activity ID,Activity Date,Activity Name,Activity Type,Media
            2001,"01.05.2024, 10:00:00",Trail Run,Run,"media/picA.jpg"
        """.trimIndent()

        val englishActivities = StravaCsvParser.parseActivities(StringReader(englishCsv))
        assertEquals(1, englishActivities.size)
        assertEquals(listOf("media/picA.jpg"), englishActivities[0].media)
    }

    @Test
    fun testParseMediaCaptions_germanAndEnglish() {
        val germanMediaCsv = """
            Medien-Dateiname,Medienunterschrift
            media/pic1.jpg,"Beautiful mountain summit"
            media/pic2.jpg,Trailhead start
        """.trimIndent()

        val germanCaptions = StravaCsvParser.parseMediaCaptions(StringReader(germanMediaCsv))
        assertEquals(4, germanCaptions.size)
        assertEquals("Beautiful mountain summit", germanCaptions["media/pic1.jpg"])
        assertEquals("Beautiful mountain summit", germanCaptions["pic1.jpg"])
        assertEquals("Trailhead start", germanCaptions["media/pic2.jpg"])
        assertEquals("Trailhead start", germanCaptions["pic2.jpg"])

        val englishMediaCsv = """
            Media Filename,Media Caption
            media/picA.jpg,Finish line celebration
        """.trimIndent()

        val englishCaptions = StravaCsvParser.parseMediaCaptions(StringReader(englishMediaCsv))
        assertEquals(2, englishCaptions.size)
        assertEquals("Finish line celebration", englishCaptions["media/picA.jpg"])
        assertEquals("Finish line celebration", englishCaptions["picA.jpg"])
    }
}
