package net.chevillot.raintoday.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client de l'API « RainService » de Meteogroup, la même que l'application
 * Rain Today d'origine.
 *
 *  - zone   : https://feed.alertspro.meteogroup.com/AlertsPro/AlertsProPollService.php?method=lookupCoord
 *  - pluie  : https://weatherpro.consumer.meteogroup.com/weatherpro/RainService.php?method=getRainChart
 *  - lieux  : https://weatherpro.consumer.meteogroup.com/weatherpro/SearchFeed.php (XML)
 */
class MeteogroupApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    private val weatherBase = "https://weatherpro.consumer.meteogroup.com/weatherpro"
    private val alertBase = "https://feed.alertspro.meteogroup.com/AlertsPro"

    /** Code de zone UWZ pour un point (celui que RainService attend en `areaID`). */
    suspend fun lookupArea(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val body = get("$alertBase/AlertsProPollService.php?method=lookupCoord&lat=$lat&lon=$lon")
        runCatching {
            json.parseToJsonElement(body).jsonArray
                .firstOrNull()
                ?.jsonObject
                ?.get("AREA_ID")
                ?.jsonPrimitive
                ?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    suspend fun rainChart(areaId: String): RainSeries = withContext(Dispatchers.IO) {
        val area = URLEncoder.encode(areaId, "UTF-8")
        val body = get("$weatherBase/RainService.php?method=getRainChart&areaID=$area")
        val raw = json.decodeFromString<RawRainChart>(body)
        if (raw.avg.isEmpty()) throw IOException("Le service n'a pas renvoyé de série de pluie")
        val start = parseApiDate(raw.startdtg) ?: throw IOException("Date de série illisible")
        RainSeries(
            areaId = areaId,
            start = start,
            intervalSeconds = ((raw.interval.toLongOrNull() ?: 60_000L) / 1000L).coerceAtLeast(1L),
            domainMin = raw.domainMin,
            domainMax = raw.domainMax,
            avg = raw.avg,
            min = raw.min,
            max = raw.max,
            createdAt = parseApiDate(raw.creationdtg) ?: start,
        )
    }

    suspend fun search(query: String, lang: String = "fr", limit: Int = 15): List<Place> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            parseCities(get("$weatherBase/SearchFeed.php?search=$q&limit=$limit&offsetCity=0&lang=$lang"))
        }

    /** Nom du lieu le plus proche d'un point (géocodage inverse du backend). */
    suspend fun nearest(lat: Double, lon: Double, lang: String = "fr"): Place? =
        withContext(Dispatchers.IO) {
            parseCities(get("$weatherBase/SearchFeed.php?next=$lon,$lat&lang=$lang&limit=1")).firstOrNull()
        }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RainTodayLite/1.0 (Android)")
            .header("Accept", "*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} sur ${request.url.host}")
            return response.body?.string().orEmpty()
        }
    }

    private fun parseCities(xml: String): List<Place> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        val places = mutableListOf<Place>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "city") {
                fun attr(name: String) = parser.getAttributeValue(null, name).orEmpty()
                val lat = attr("latitude").toDoubleOrNull()
                val lon = attr("longitude").toDoubleOrNull()
                val name = attr("name")
                if (lat != null && lon != null && name.isNotBlank()) {
                    places += Place(
                        id = attr("id").ifBlank { "$lat,$lon" },
                        name = name,
                        region = attr("province-name"),
                        country = attr("country-name"),
                        lat = lat,
                        lon = lon,
                    )
                }
            }
            event = parser.next()
        }
        return places
    }
}
