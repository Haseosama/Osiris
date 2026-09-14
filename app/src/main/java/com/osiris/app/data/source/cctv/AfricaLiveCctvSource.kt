package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Africa public live webcams — mirrors `osiris-backend/src/app/api/cctv/world-live.ts`'s `fetchAfricaLiveCameras` (itself just `world-skyline.generated.ts`'s `AFRICA_SKYLINE_CAMERAS`). */
object AfricaLiveCctvSource {

    private data class Cam(
        val id: String,
        val lat: Double,
        val lng: Double,
        val name: String,
        val city: String,
        val country: String,
        val liveId: String?,
        val externalUrl: String,
    )

    private val CAMS = listOf(
        Cam("sky-kenya-diani-beach-kenya-4309", -4.3124919, 39.5793598, "Diani Beach - Kenya", "Diani Beach", "Kenya", "live4309", "https://www.skylinewebcams.com/en/webcam/kenya/kwale-county/diani-beach/diani-beach.html"),
        Cam("sky-kenya-masai-mara-kenya-4905", -0.2802724, 36.0712048, "Masai Mara - Kenya", "Nakuru", "Kenya", null, "https://www.skylinewebcams.com/en/webcam/kenya/rift-valley-province/nakuru/masai-mara.html"),
        Cam("sky-kenya-kenya-voi-wildlife-lodge-tsavo-east-national-park-992", -3.3631799, 38.5952927, "Kenya-Voi Wildlife Lodge Tsavo East National Park", "Voi", "Kenya", "live992", "https://www.skylinewebcams.com/en/webcam/kenya/taita-taveta-county/voi/tsavo-east-national-park.html"),
        Cam("sky-kenya-watamu-beach-kenya-572", -3.3550829, 40.0198076, "Watamu Beach - Kenya", "Watamu", "Kenya", "live572", "https://www.skylinewebcams.com/en/webcam/kenya/malindi/watamu/watamu-beach.html"),
        Cam("sky-morocco-imsouane-morocco-5791", 30.841899, -9.819964, "Imsouane - Morocco", "Imsouane", "Morocco", "live5791", "https://www.skylinewebcams.com/en/webcam/morocco/souss-massa/imsouane/plage.html"),
        Cam("sky-senegal-lake-retba-senegal-5790", 14.8447328, -17.216659, "Lake Retba - Senegal", "Niaga Peulh", "Senegal", "live5790", "https://www.skylinewebcams.com/en/webcam/senegal/dakar/niaga-peulh/lac-rose.html"),
        Cam("sky-seychelles-quincy-village-seychelles-4793", -4.5932954, 55.455248, "Quincy Village - Seychelles", "Anse Etoile", "Seychelles", "live4793", "https://www.skylinewebcams.com/en/webcam/seychelles/mahe/anse-etoile/quincy-village.html"),
        Cam("sky-seychelles-beau-vallon-seychelles-5", -4.6096262, 55.438103, "Beau Vallon - Seychelles", "Beau Vallon", "Seychelles", "live5", "https://www.skylinewebcams.com/en/webcam/seychelles/mahe/beau-vallon/beau-vallon-seychelles.html"),
        Cam("sky-seychelles-anse-parnel-takamaka-1475", -4.7679474, 55.522495, "Anse Parnel Takamaka", "Takamaka", "Seychelles", "live1475", "https://www.skylinewebcams.com/en/webcam/seychelles/mahe/takamaka/anse-parnel-takamaka.html"),
        Cam("sky-seychelles-seychelles-anse-parnel-takamaka-868", -4.7768202, 55.5234855, "Seychelles - Anse Parnel Takamaka", "Takamaka", "Seychelles", "live868", "https://www.skylinewebcams.com/en/webcam/seychelles/mahe/takamaka/seychelles-takamaka.html"),
        Cam("sky-south-africa-olifants-river-2639", -24.1873674, 30.936862, "Olifants River", "Balule Nature Reserve", "South Africa", null, "https://www.skylinewebcams.com/en/webcam/south-africa/limpopo/balule-nature-reserve/olifants-river.html"),
        Cam("sky-south-africa-cape-town-zeekoevlei-4040", -34.0620199, 18.508325, "Cape Town - Zeekoevlei", "Cape Town", "South Africa", "live4040", "https://www.skylinewebcams.com/en/webcam/south-africa/western-cape/cape-town/zeekoevlei.html"),
        Cam("sky-south-africa-knysna-south-africa-5808", -34.0406024, 23.0548265, "Knysna - South Africa", "Knysna", "South Africa", null, "https://www.skylinewebcams.com/en/webcam/south-africa/western-cape/knysna/knysna-lagoon.html"),
        Cam("sky-south-africa-nkorho-africa-2640", -23.9357763, 31.5083741, "Nkorho - Africa", "Kruger National Park", "South Africa", null, "https://www.skylinewebcams.com/en/webcam/south-africa/limpopo/kruger-national-park/nkorho.html"),
        Cam("sky-south-africa-ballito-738", -29.5405244, 31.2162968, "Ballito", "Kwadukuza", "South Africa", "live738", "https://www.skylinewebcams.com/en/webcam/south-africa/kwazulu-natal/kwadukuza/ballito-beach.html"),
        Cam("sky-south-africa-ballito-south-africa-666", -29.543864, 31.214517, "Ballito - South Africa", "Kwadukuza", "South Africa", "live666", "https://www.skylinewebcams.com/en/webcam/south-africa/kwazulu-natal/kwadukuza/ballito.html"),
        Cam("sky-south-africa-ballito-willard-beach-1112", -29.3383782, 31.2881205, "Ballito - Willard Beach", "Kwadukuza", "South Africa", "live1112", "https://www.skylinewebcams.com/en/webcam/south-africa/kwazulu-natal/kwadukuza/willard-beach.html"),
        Cam("sky-south-africa-zinkwazi-beach-5376", -29.2797867, 31.4397649, "Zinkwazi Beach", "Kwadukuza", "South Africa", "live5376", "https://www.skylinewebcams.com/en/webcam/south-africa/kwazulu-natal/kwadukuza/zinkwazi-beach.html"),
        Cam("sky-south-africa-tembe-elephant-park-1795", -26.988287, 32.756033, "Tembe Elephant Park", "Manguzi", "South Africa", null, "https://www.skylinewebcams.com/en/webcam/south-africa/kwazulu-natal/manguzi/tembe-elephant-park.html"),
        Cam("sky-south-africa-pretoria-bird-feeder-1984", -25.7459277, 28.1879101, "Pretoria - Bird Feeder", "Pretoria", "South Africa", null, "https://www.skylinewebcams.com/en/webcam/south-africa/tshwane/pretoria/pretoria-bird-feeder.html"),
        Cam("sky-south-africa-springs-south-africa-5476", -26.2500305, 28.4377769, "Springs - South Africa", "Springs", "South Africa", null, "https://www.skylinewebcams.com/en/webcam/south-africa/gauteng/springs/springs.html"),
        Cam("sky-south-africa-weather-in-stellenbosch-south-africa-5997", -33.9300085, 18.8557258, "Weather in Stellenbosch - South-Africa", "Stellenbosch", "South Africa", "live5997", "https://www.skylinewebcams.com/en/webcam/south-africa/western-cape/stellenbosch/dennesig.html"),
        Cam("sky-zanzibar-jambiani-beach-3385", -6.3281124, 39.5507609, "Jambiani Beach", "Jambiani", "Tanzania", "live3385", "https://www.skylinewebcams.com/en/webcam/zanzibar/zanzibar-central-south/jambiani/jambiani-beach.html"),
        Cam("sky-zanzibar-kiwengwa-1622", -5.9817463, 39.3736772, "Kiwengwa", "Kiwengwa", "Tanzania", "live1622", "https://www.skylinewebcams.com/en/webcam/zanzibar/zanzibar-north/kiwengwa/kiwengwa.html"),
        Cam("sky-zanzibar-zanzibar-kiwengwa-1188", -5.9394963, 39.3601605, "Zanzibar - Kiwengwa", "Kiwengwa", "Tanzania", "live1188", "https://www.skylinewebcams.com/en/webcam/zanzibar/zanzibar-north/kiwengwa/zanzibar-kiwengwa.html"),
        Cam("sky-zanzibar-dongwe-beach-5098", -6.15149, 39.5170009, "Dongwe Beach", "Michamvi", "Tanzania", "live5098", "https://www.skylinewebcams.com/en/webcam/zanzibar/zanzibar-central-south/michamvi/pingwe-michamvi.html"),
        Cam("sky-zanzibar-nungwi-beach-2604", -5.732993, 39.2939216, "Nungwi Beach", "Nungwi", "Tanzania", "live2604", "https://www.skylinewebcams.com/en/webcam/zanzibar/zanzibar-north/nungwi/nungwi-beach.html"),
    )

    suspend fun fetch(): List<CctvCamera> = CAMS.map { c ->
        CctvCamera(
            id = c.id,
            lat = c.lat,
            lng = c.lng,
            name = c.name,
            city = c.city,
            country = c.country,
            feedUrl = c.liveId?.let { id -> "https://cdn.skylinewebcams.com/$id.jpg" },
            externalUrl = c.externalUrl,
            source = "SkylineWebcams",
        )
    }
}
