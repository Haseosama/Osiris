package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Spain CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/spain.ts`. Three YouTube
 * embeds (converted to external-link-only, see [FranceCctvSource]'s doc comment), ~50
 * SkylineWebcams entries (the CDN's self-referencing Referer requirement is now handled
 * generically by [com.osiris.app.map.CctvViewerDialog]'s image loader), and DGT's live
 * highway-camera index (`etraffic.dgt.es`, also Referer-gated, same generic handling). DGT
 * cameras within ~100m of a static entry above are dropped as duplicates, same de-dup the
 * backend does. */
object SpainCctvSource {

    private val PROVINCES = mapOf(
        "01" to "Álava", "02" to "Albacete", "03" to "Alicante", "04" to "Almería", "05" to "Ávila",
        "06" to "Badajoz", "07" to "Baleares", "08" to "Barcelona", "09" to "Burgos", "10" to "Cáceres",
        "11" to "Cádiz", "12" to "Castellón", "13" to "Ciudad Real", "14" to "Córdoba", "15" to "A Coruña",
        "16" to "Cuenca", "17" to "Girona", "18" to "Granada", "19" to "Guadalajara", "20" to "Gipuzkoa",
        "21" to "Huelva", "22" to "Huesca", "23" to "Jaén", "24" to "León", "25" to "Lleida",
        "26" to "La Rioja", "27" to "Lugo", "28" to "Madrid", "29" to "Málaga", "30" to "Murcia",
        "31" to "Navarra", "32" to "Ourense", "33" to "Asturias", "34" to "Palencia", "35" to "Las Palmas",
        "36" to "Pontevedra", "37" to "Salamanca", "38" to "S.C. Tenerife", "39" to "Cantabria",
        "40" to "Segovia", "41" to "Sevilla", "42" to "Soria", "43" to "Tarragona", "44" to "Teruel",
        "45" to "Toledo", "46" to "Valencia", "47" to "Valladolid", "48" to "Bizkaia", "49" to "Zamora",
        "50" to "Zaragoza", "51" to "Ceuta", "52" to "Melilla",
    )

    private val YOUTUBE = listOf(
        CctvCamera(
            id = "es-barcelona-2", lat = 41.3800, lng = 2.1800, name = "Barcelona - Beach Area",
            city = "Barcelona", country = "Spain", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=4DjwrvoTKwk",
        ),
        CctvCamera(
            id = "es-madrid-1", lat = 40.4168, lng = -3.7038, name = "Madrid - Puerta del Sol",
            city = "Madrid", country = "Spain", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=4CaHlfpGlAI",
        ),
        CctvCamera(
            id = "es-madrid-2", lat = 40.4200, lng = -3.7000, name = "Madrid - Gran Via",
            city = "Madrid", country = "Spain", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=LSPN10FbR3U",
        ),
    )

    private data class Sky(val id: String, val lat: Double, val lng: Double, val name: String, val city: String, val liveId: String?, val externalUrl: String)

    private val SKYLINE = listOf(
        Sky("sky-es-los-cristianos", 28.0517, -16.7155, "Playa de Los Cristianos", "Tenerife", "live340", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-los-cristianos.html"),
        Sky("sky-es-las-vistas", 28.0540, -16.7230, "Playa Las Vistas", "Tenerife", "live339", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-las-vistas.html"),
        Sky("sky-es-medano-surf", 28.0445, -16.5370, "El Médano Surf & Kitesurf", "Tenerife", "live427", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/surf-kitesurf-medano.html"),
        Sky("sky-es-duque", 28.0800, -16.7400, "Playa del Duque - El Beril", "Tenerife", "live4943", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-del-duque-el-beril.html"),
        Sky("sky-es-fanabe", 28.0730, -16.7370, "Playa de Fañabé", "Tenerife", "live383", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-de-fanabe.html"),
        Sky("sky-es-troya", 28.0600, -16.7300, "Playa de Troya - Las Américas", "Tenerife", "live382", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-troya.html"),
        Sky("sky-es-puerto-cruz", 28.4147, -16.5476, "Puerto de la Cruz", "Tenerife", "live366", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/puerto-de-la-cruz-tenerife.html"),
        Sky("sky-es-la-pinta", 28.0750, -16.7350, "Costa Adeje - Playa La Pinta", "Tenerife", "live1064", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-la-pinta.html"),
        Sky("sky-es-bahia-cristianos", 28.0490, -16.7180, "Bahía de Los Cristianos", "Tenerife", "live2910", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/bahia-los-cristianos.html"),
        Sky("sky-es-isora", 28.1940, -16.8560, "Guía de Isora - Playa San Juan", "Tenerife", "live3033", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/guia-de-isora.html"),
        Sky("sky-es-costa-adeje", 28.0900, -16.7500, "Costa Adeje - Playa Paraíso", "Tenerife", "live5145", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/costa-adeje.html"),
        Sky("sky-es-punta-brava", 28.4100, -16.5600, "Puerto de la Cruz - Punta Brava", "Tenerife", "live1276", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/punta-brava.html"),
        Sky("sky-es-masca", 28.2960, -16.8410, "Parque Rural de Teno - Masca", "Tenerife", "live1093", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/masca-valley-tenerife.html"),
        Sky("sky-es-americas", 28.0560, -16.7280, "Arona - Playa de Las Américas", "Tenerife", "live1115", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-de-las-americas.html"),
        Sky("sky-es-hidalgo", 28.5600, -16.3300, "Punta del Hidalgo", "Tenerife", "live1065", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/punta-del-hidalgo.html"),
        Sky("sky-es-lago-martianez", 28.4150, -16.5460, "Puerto de la Cruz - Lago Martiánez", "Tenerife", "live1042", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/puerto-de-la-cruz-lago-martianez.html"),
        Sky("sky-es-san-telmo", 28.4130, -16.5500, "Puerto de la Cruz - Playa San Telmo", "Tenerife", "live792", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/puerto-de-la-cruz-playa-san-telmo.html"),
        Sky("sky-es-medano-playa", 28.0450, -16.5380, "Playa de El Médano", "Tenerife", "live376", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-de-el-medano.html"),
        Sky("sky-es-duque2", 28.0810, -16.7410, "Playa del Duque", "Tenerife", "live1073", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/playa-del-duque.html"),
        Sky("sky-es-catamaran", 28.0700, -16.7350, "Catamarán Royal Delfin", "Tenerife", "live670", "https://www.skylinewebcams.com/en/webcam/espana/canarias/santa-cruz-de-tenerife/catamarano-royal-delfin.html"),
        Sky("sky-es-canteras-g", 28.1420, -15.4330, "Playa Grande de Las Canteras", "Gran Canaria", "live680", "https://www.skylinewebcams.com/en/webcam/espana/canarias/las-palmas-gran-canaria/playa-grande-las-canteras.html"),
        Sky("sky-es-canteras", 28.1400, -15.4380, "Las Palmas - Playa de Las Canteras", "Gran Canaria", "live624", "https://www.skylinewebcams.com/en/webcam/espana/canarias/las-palmas-gran-canaria/playa-las-canteras.html"),
        Sky("sky-es-patalavaca", 27.7730, -15.6830, "Patalavaca - Anfi del Mar", "Gran Canaria", "live211", "https://www.skylinewebcams.com/en/webcam/espana/canarias/las-palmas-gran-canaria/patalavaca.html"),
        Sky("sky-es-puerto-rico", 27.7870, -15.7100, "Mogán - Playa de Puerto Rico", "Gran Canaria", "live1747", "https://www.skylinewebcams.com/en/webcam/espana/canarias/las-palmas-gran-canaria/mogan-playa-de-puerto-rico.html"),
        Sky("sky-es-amadores", 27.7920, -15.7200, "Playa de Amadores", "Gran Canaria", "live1517", "https://www.skylinewebcams.com/en/webcam/espana/canarias/las-palmas-gran-canaria/puerto-rico-de-gran-canaria-playa-amadores.html"),
        Sky("sky-es-playa-cura", 27.7850, -15.7050, "Mogán - Playa del Cura", "Gran Canaria", "live1518", "https://www.skylinewebcams.com/en/webcam/espana/canarias/las-palmas-gran-canaria/mogan-playa-del-cura.html"),
        Sky("sky-es-pocillos", 28.9210, -13.6510, "Puerto del Carmen - Playa Los Pocillos", "Lanzarote", "live4592", "https://www.skylinewebcams.com/en/webcam/espana/canarias/las-palmas-gran-canaria/puerto-del-carmen-playa-los-pocillos.html"),
        Sky("sky-es-corralejo", 28.7300, -13.8670, "Fuerteventura - Corralejo", "Fuerteventura", "live1091", "https://www.skylinewebcams.com/en/webcam/espana/canarias/corralejo/fuerteventura-corralejo.html"),
        Sky("sky-es-corralejo-gp", 28.7350, -13.8600, "Grandes Playas de Corralejo", "Fuerteventura", "live6086", "https://www.skylinewebcams.com/en/webcam/espana/canarias/corralejo/grandes-playas-corralejo.html"),
        Sky("sky-es-playa-blanca", 28.8600, -13.8300, "Yaiza - Playa Blanca", "Lanzarote", "live6084", "https://www.skylinewebcams.com/en/webcam/espana/canarias/las-palmas/yaiza-playa-blanca.html"),
        Sky("sky-es-sol-tiopepe", 40.4170, -3.7035, "Puerta del Sol - Tío Pepe", "Madrid", null, "https://www.skylinewebcams.com/en/webcam/espana/comunidad-de-madrid/madrid/puerta-del-sol-tio-pepe.html"),
        Sky("sky-es-sol-mayor", 40.4172, -3.7050, "Puerta del Sol - Calle Mayor", "Madrid", null, "https://www.skylinewebcams.com/en/webcam/espana/comunidad-de-madrid/madrid/puerta-del-sol-calle-mayor.html"),
        Sky("sky-es-callao", 40.4200, -3.7070, "Madrid - Plaza del Callao", "Madrid", "live566", "https://www.skylinewebcams.com/en/webcam/espana/comunidad-de-madrid/madrid/madrid-plaza-del-callao.html"),
        Sky("sky-es-alcala", 40.4190, -3.6940, "Calle de Alcalá - Cibeles", "Madrid", "live314", "https://www.skylinewebcams.com/en/webcam/espana/comunidad-de-madrid/madrid/calle-alcala.html"),
        Sky("sky-es-alhambra", 37.1760, -3.5880, "La Alhambra de Granada", "Granada", "live372", "https://www.skylinewebcams.com/en/webcam/espana/andalucia/granada/alhambra-de-granada.html"),
        Sky("sky-es-sevilla-sf", 37.3891, -5.9945, "Sevilla - Plaza de San Francisco", "Sevilla", "live823", "https://www.skylinewebcams.com/en/webcam/espana/andalucia/sevilla/siviglia-plaza-san-francisco.html"),
        Sky("sky-es-barrosa", 36.3700, -6.1700, "Playa de la Barrosa - Chiclana", "Chiclana", "live1636", "https://www.skylinewebcams.com/en/webcam/espana/andalucia/cadiz/chiclana-de-la-frontera-playa-de-la-barrosa.html"),
        Sky("sky-es-cocedores", 37.3850, -1.6400, "Pulpí - Playa de los Cocedores", "Pulpí", "live5987", "https://www.skylinewebcams.com/en/webcam/espana/andalucia/almeria/pulpi-playa-de-los-cocedores.html"),
        Sky("sky-es-benidorm-p", 38.5322, -0.1270, "Benidorm - Playa de Poniente", "Benidorm", "live630", "https://www.skylinewebcams.com/en/webcam/espana/comunidad-valenciana/alicante/benidorm-playa-poniente-sur.html"),
        Sky("sky-es-benidorm-pp", 38.5340, -0.1250, "Benidorm - Playa de Poniente - Puerto", "Benidorm", "live293", "https://www.skylinewebcams.com/en/webcam/espana/comunidad-valenciana/alicante/benidorm-playa-poniente.html"),
        Sky("sky-es-benidorm-l", 38.5370, -0.1180, "Benidorm - Playa de Levante", "Benidorm", "live642", "https://www.skylinewebcams.com/en/webcam/espana/comunidad-valenciana/alicante/benidorm-playa-levante.html"),
        Sky("sky-es-benidorm-la", 38.5360, -0.1200, "Benidorm - Playa de Levante - Alicante", "Benidorm", "live592", "https://www.skylinewebcams.com/en/webcam/espana/comunidad-valenciana/alicante/benidorm-playa-alicante.html"),
        Sky("sky-es-calpe", 38.6440, 0.0650, "Calpe - Peñón de Ifach", "Calpe", "live3032", "https://www.skylinewebcams.com/en/webcam/espana/comunidad-valenciana/alicante/calpe-penon-de-ifach.html"),
        Sky("sky-es-lloret", 41.7010, 2.8460, "Lloret de Mar - Costa Brava", "Lloret de Mar", "live631", "https://www.skylinewebcams.com/en/webcam/espana/cataluna/gerona/lloret-de-mar-costa-brava.html"),
        Sky("sky-es-calafell", 41.1970, 1.5660, "Calafell - Tarragona", "Calafell", "live3822", "https://www.skylinewebcams.com/en/webcam/espana/cataluna/tarragona/calafell.html"),
        Sky("sky-es-barcelona-cat", 41.3810, 2.1970, "Tour en Catamarán - Port Olímpic", "Barcelona", null, "https://www.skylinewebcams.com/en/webcam/espana/cataluna/barcelona/catamaran.html"),
        Sky("sky-es-sardinero", 43.4750, -3.7870, "Santander - Playa del Sardinero", "Santander", "live728", "https://www.skylinewebcams.com/en/webcam/espana/cantabria/santander/playa-del-sardinero.html"),
        Sky("sky-es-suances", 43.4320, -4.0420, "Suances - Playa de la Concha", "Suances", "live804", "https://www.skylinewebcams.com/en/webcam/espana/cantabria/suances/playa-de-la-concha.html"),
        Sky("sky-es-pujols", 38.7230, 1.4620, "Formentera - Playa de Es Pujols", "Formentera", "live4727", "https://www.skylinewebcams.com/en/webcam/espana/islas-baleares/formentera/playa-es-pujols.html"),
        Sky("sky-es-bullas", 38.0497, -1.6700, "Bullas - Plaza de España", "Bullas", "live299", "https://www.skylinewebcams.com/en/webcam/espana/region-de-murcia/murcia/bullas-plaza-de-espana.html"),
    )

    private fun skylineCameras(): List<CctvCamera> = SKYLINE.map { s ->
        CctvCamera(
            id = s.id, lat = s.lat, lng = s.lng, name = s.name, city = s.city, country = "Spain",
            feedUrl = s.liveId?.let { "https://cdn.skylinewebcams.com/$it.jpg" },
            externalUrl = s.externalUrl,
            source = "SkylineWebcams",
        )
    }

    private suspend fun dgtCameras(): List<CctvCamera> = runCatching {
        val response = DirectHttp.getJson<DgtResponse>("https://www.dgt.es/.content/.assets/json/camaras.json")
        response.camaras.mapNotNull { cam ->
            val id = cam.id ?: return@mapNotNull null
            val lat = cam.latitud?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = cam.longitud?.toDoubleOrNull() ?: return@mapNotNull null
            if (lat == 0.0 && lng == 0.0) return@mapNotNull null
            val imagen = cam.imagen?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val direction = when (cam.sentido) {
                "-", null -> ""
                "+" -> " (Ascending)"
                else -> " (${cam.sentido})"
            }
            val city = PROVINCES[cam.provincia] ?: "Province ${cam.provincia.orEmpty()}"

            CctvCamera(
                id = "dgt-$id",
                lat = lat,
                lng = lng,
                name = "${cam.carretera.orEmpty()} km ${cam.pk.orEmpty()}$direction",
                city = city,
                country = "Spain",
                feedUrl = imagen,
                source = "DGT",
            )
        }
    }.getOrDefault(emptyList())

    suspend fun fetch(): List<CctvCamera> {
        val static = YOUTUBE + skylineCameras()
        val dgt = dgtCameras().filterNot { d ->
            static.any { s -> kotlin.math.abs(s.lat - d.lat) <= 0.001 && kotlin.math.abs(s.lng - d.lng) <= 0.001 }
        }
        return static + dgt
    }

    @Serializable
    private data class DgtResponse(val camaras: List<DgtCamera> = emptyList())

    @Serializable
    private data class DgtCamera(
        val id: String? = null,
        val latitud: String? = null,
        val longitud: String? = null,
        val carretera: String? = null,
        val pk: String? = null,
        val sentido: String? = null,
        val provincia: String? = null,
        val imagen: String? = null,
    )
}
