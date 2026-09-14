package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/**
 * France CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/france.ts`, a fully static
 * list (no network call at all, same as [com.osiris.app.data.repository.LiveNewsRepository]):
 *   - Paris/Nice YouTube embeds: the backend links these with `stream_type: 'iframe'`, which
 *     [com.osiris.app.map.CctvViewerDialog] has no equivalent for (it only knows real MP4
 *     playback vs. an auto-refreshing image) — an embed URL fed to ExoPlayer as a stream
 *     wouldn't play, so these become external-link-only entries instead of a broken player.
 *   - Bordeaux Tourisme: external-link-only in the backend too (see its own comment there —
 *     no stable fetchable file for that player), ported as-is.
 *   - APRR/AREA: 123 highway webcams, each a real `gieat.viewsurf.com` MP4 `mediaRedirect` (one
 *     exception uses `action=film`) that plays directly in [com.osiris.app.map.CctvViewerDialog]'s
 *     ExoPlayer, no proxy needed.
 * SkylineWebcams (16 cameras) is deliberately NOT ported here: its CDN needs a `Referer` header
 * to serve images at all (`osiris-backend/src/app/api/cctv/proxy/route.ts`), which
 * [com.osiris.app.map.CctvViewerDialog]'s current Coil `SubcomposeAsyncImage(model = url)` call
 * has no way to send — SkylineWebcams is reused across many other country files too, so this
 * is a follow-up to add once (custom-header image loading), not a per-country one-off.
 */
object FranceCctvSource {

    private data class Aprr(
        val id: String,
        val lat: Double,
        val lng: Double,
        val name: String,
        val city: String,
        val viewsurfId: String,
        val node: String,
        val action: String = "mediaRedirect",
    )

    private val YOUTUBE_LIVE = listOf(
        CctvCamera(
            id = "fr-paris-1", lat = 48.8584, lng = 2.2945, name = "Paris - Eiffel Tower Area",
            city = "Paris", country = "France", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=UMuEooW0iAQ",
        ),
        CctvCamera(
            id = "fr-paris-2", lat = 48.8600, lng = 2.3300, name = "Paris - Louvre Area",
            city = "Paris", country = "France", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=OzYp4NRZlwQ",
        ),
        CctvCamera(
            id = "fr-nice-1", lat = 43.6961, lng = 7.2717, name = "Nice - Promenade des Anglais",
            city = "Nice", country = "France", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=YAdNYoRY0Cw",
        ),
        CctvCamera(
            id = "fr-nice-2", lat = 43.7000, lng = 7.2600, name = "Nice - City View",
            city = "Nice", country = "France", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=asO_10T0k2k",
        ),
    )

    private val BORDEAUX_TOURISME = listOf(
        CctvCamera(
            id = "bdx-place-de-la-bourse", lat = 44.8412, lng = -0.5698,
            name = "Bordeaux - Place de la Bourse", city = "Bordeaux", country = "France",
            source = "Bordeaux Tourisme",
            externalUrl = "https://www.viewsurf.com/univers/ville/vue/890-france-aquitaine-bordeaux-place-de-la-bourse",
        ),
        CctvCamera(
            id = "bdx-quai-des-chartrons", lat = 44.8523, lng = -0.5663,
            name = "Bordeaux - Garonne / Quai des Chartrons", city = "Bordeaux", country = "France",
            source = "Bordeaux Tourisme",
            externalUrl = "https://www.viewsurf.com/univers/ville/vue/18716-france-aquitaine-bordeaux-quai-des-chartrons",
        ),
        CctvCamera(
            id = "bdx-cite-du-vin", lat = 44.8590, lng = -0.5504,
            name = "Bordeaux - Ponton de La Cité du Vin", city = "Bordeaux", country = "France",
            source = "Bordeaux Tourisme",
            externalUrl = "https://www.viewsurf.com/univers/ville/vue/18720-france-aquitaine-bordeaux-les-hangars-et-la-cite-du-vin",
        ),
        CctvCamera(
            id = "bdx-pont-chaban-delmas", lat = 44.8611, lng = -0.5453,
            name = "Bordeaux - Pont Chaban-Delmas", city = "Bordeaux", country = "France",
            source = "Bordeaux Tourisme",
            externalUrl = "https://www.viewsurf.com/univers/ville/vue/4567-france-aquitaine-bordeaux-pont-bacalan-bastide",
        ),
    )

    private val APRR_HIGHWAY = listOf(
        Aprr("aprr-porte-de-montreuil-96", 48.864615, 2.412806, "Porte de Montreuil", "Montreuil", "5598", "96"),
        Aprr("aprr-porte-des-lilas-97", 48.865265, 2.412849, "Porte des Lilas", "Lilas", "5596", "97"),
        Aprr("aprr-bifurcation-des-autoroutes-a31-et-a6-pres-de-beaune-98", 47.029731, 4.871092, "Bifurcation des autoroutes A31 et A6 près de Beaune", "Beaune", "2744", "98"),
        Aprr("aprr-bifurcation-a5-105-pres-de-melun-99", 48.594719, 2.634015, "Bifurcation A5 / 105 près de Melun", "Melun", "2768", "99"),
        Aprr("aprr-bifurcation-des-autoroutes-a5-et-a19-pres-de-sens-100", 48.270854, 3.289032, "Bifurcation des autoroutes A5 et A19 près de Sens", "Sens", "2738", "100"),
        Aprr("aprr-pres-d-auxerre-et-d-avallon-101", 47.789459, 3.670528, "Près d'Auxerre et d'Avallon", "A6", "2742", "101"),
        Aprr("aprr-pres-de-saint-priest-102", 45.702772, 4.965949, "Près de Saint-Priest", "Saint-Priest", "5590", "102"),
        Aprr("aprr-nord-de-lyon-103", 45.810736, 4.918906, "Nord de Lyon", "Lyon", "2792", "103"),
        Aprr("aprr-peage-de-villefranche-limas-au-nord-de-lyon-104", 45.969258, 4.728348, "Péage de Villefranche Limas, au nord de Lyon", "Lyon", "12690", "104"),
        Aprr("aprr-peage-des-eprunes-105", 48.590348, 2.649443, "Péage des Eprunes", "Eprunes", "2736", "105"),
        Aprr("aprr-pres-de-nantua-106", 46.160572, 5.651951, "Près de Nantua", "Nantua", "2758", "106"),
        Aprr("aprr-pres-d-oyonnax-et-nantua-107", 46.128591, 5.538104, "Près d'Oyonnax et Nantua", "A40", "2760", "107"),
        Aprr("aprr-peripherie-de-villefranche-sur-saone-nord-108", 46.004221, 4.73279, "Périphérie de Villefranche-sur-Saône Nord", "Villefranche-sur-Saône Nord", "2780", "108"),
        Aprr("aprr-peripherie-de-macon-sud-109", 46.297344, 4.792013, "Périphérie de Mâcon Sud", "Mâcon Sud", "2778", "109"),
        Aprr("aprr-peripherie-de-chalon-sur-saone-nord-110", 46.800823, 4.825015, "Périphérie de Chalon-sur-Saône Nord", "Chalon-sur-Saône Nord", "2776", "110"),
        Aprr("aprr-peripherie-de-beaune-nord-111", 47.0582538, 4.821088, "Périphérie de Beaune-Nord", "Beaune-Nord", "2774", "111"),
        Aprr("aprr-au-niveau-de-l-aire-du-poulet-de-bresse-112", 46.498717, 5.313778, "Au niveau de l'Aire du poulet de Bresse", "Bresse", "2754", "112"),
        Aprr("aprr-peage-de-clermont-gerzat-113", 45.838427, 3.160071, "Péage de Clermont-Gerzat", "Clermont-Gerzat", "2766", "113"),
        Aprr("aprr-entre-dijon-et-langres-au-niveau-des-aires-de-langres-114", 47.821411, 5.224771, "Entre Dijon et Langres, au niveau des aires de Langres", "Langres", "2750", "114"),
        Aprr("aprr-entree-de-mulhouse-115", 47.744475, 7.263658, "Entrée de Mulhouse", "Mulhouse", "2784", "115"),
        Aprr("aprr-environs-de-montbeliard-116", 47.494222, 6.815001, "Environs de Montbéliard", "Montbéliard", "2786", "116"),
        Aprr("aprr-peripherie-de-besancon-117", 47.275473, 5.973516, "Périphérie de Besançon", "Besançon", "2788", "117"),
        Aprr("aprr-peage-de-gye-118", 48.633447, 5.881977, "Péage de Gye", "Gye", "2748", "118"),
        Aprr("aprr-peage-de-fleury-119", 48.425812, 2.539666, "Péage de Fleury", "Fleury", "2746", "119"),
        Aprr("aprr-pres-d-auxerre-nord-120", 47.855214, 3.550644, "Près d'Auxerre-Nord", "A6", "2772", "120"),
        Aprr("aprr-pres-de-troyes-121", 48.21272, 4.241838, "Près de Troyes", "Troyes", "2770", "121"),
        Aprr("aprr-peage-de-st-maurice-de-beynost-a-l-est-de-lyon-122", 45.819506, 4.99099, "Péage de St-Maurice-de-Beynost à l'est de Lyon", "Lyon", "2762", "122"),
        Aprr("aprr-pres-de-l-aire-de-service-des-volcans-d-auvergne-123", 46.073112, 3.120074, "Près de l'aire de service des Volcans-d'Auvergne", "Volcans-d'Auvergne", "2764", "123"),
        Aprr("aprr-peripherie-de-lyon-nord-a-proximite-de-la-bifurcation-a46-a42-124", 45.838876, 4.917026, "Périphérie de Lyon-Nord à proximité de la bifurcation A46/A42", "A46", "2794", "124"),
        Aprr("aprr-entree-de-grenoble-125", 45.200818, 5.760055, "Entrée de Grenoble", "Grenoble", "5584", "125"),
        Aprr("aprr-entree-de-grenoble-126", 45.276276, 5.624163, "Entrée de Grenoble", "Grenoble", "5588", "126"),
        Aprr("aprr-pres-de-bourgoin-127", 45.566407, 5.34605, "Près de Bourgoin", "Bourgoin", "5592", "127"),
        Aprr("aprr-pres-d-aiton-128", 45.565386, 6.216631, "Près d'Aiton", "A43/A430", "5586", "128"),
        Aprr("aprr-porte-d-aubervilliers-2699", 48.901083, 2.370616, "Porte d'Aubervilliers", "Paris", "5600", "2699"),
        Aprr("aprr-entre-porte-maillot-et-porte-des-ternes-2702", 48.877041, 2.279252, "Entre Porte Maillot et Porte des Ternes", "Ternes", "5606", "2702"),
        Aprr("aprr-porte-de-vanves-2705", 48.824957, 2.304235, "Porte de Vanves", "Vanves", "5608", "2705"),
        Aprr("aprr-porte-de-vanves-2708", 48.825847, 2.299106, "Porte de Vanves", "Vanves", "5610", "2708"),
        Aprr("aprr-entre-porte-maillot-et-porte-dauphine-2720", 48.877041, 2.279252, "Entre Porte Maillot et Porte Dauphine", "Paris", "5604", "2720"),
        Aprr("aprr-porte-d-aubervilliers-2723", 48.90091, 2.369024, "Porte d'Aubervilliers", "Paris", "5602", "2723", action = "film"),
        Aprr("aprr-la-pardieu-clermont-ferrand-3006", 45.767023, 3.138914, "La Pardieu - Clermont Ferrand", "A75", "17698", "3006"),
        Aprr("aprr-entree-du-tunnel-de-chamoise-3009", 46.142152, 5.622888, "Entrée du tunnel de Chamoise", "Chamoise", "2756", "3009"),
        Aprr("aprr-peripherie-de-bourg-en-bresse-3012", 46.1471337, 5.2916544, "Périphérie de Bourg-en-Bresse", "Bourg-en-Bresse", "2790", "3012"),
        Aprr("aprr-peage-de-st-maurice-3015", 47.42262, 6.68106, "Péage de St Maurice", "St Maurice", "2752", "3015"),
        Aprr("aprr-peage-de-crimolois-3027", 47.274882, 5.145179, "Péage de Crimolois", "Crimolois", "2782", "3027"),
        Aprr("aprr-pres-du-peage-de-montmarault-3030", 46.327987, 2.968625, "Près du péage de Montmarault", "Montmarault", "17772", "3030"),
        Aprr("aprr-sud-de-grenoble-3033", 45.162277, 5.707948, "Sud de Grenoble", "Grenoble", "18274", "3033"),
        Aprr("aprr-entree-de-l-a480-au-nord-de-grenoble-3036", 45.215553, 5.680209, "Entrée de l'A480 au nord de Grenoble", "Grenoble", "19146", "3036"),
        Aprr("aprr-environs-de-mions-3084", 45.671651, 4.939094, "Environs de Mions", "Mions", "2798", "3084"),
        Aprr("aprr-environs-de-saint-priest-bel-air-3087", 45.699466, 4.967194, "Environs de Saint-Priest Bel Air", "Saint-Priest Bel Air", "2800", "3087"),
        Aprr("aprr-peage-de-vienne-reventin-3090", 45.481096, 4.833512, "Péage de Vienne - Reventin", "A7", "2806", "3090"),
        Aprr("aprr-peage-de-lancon-provence-3093", 43.595335, 5.169377, "péage de Lançon - Provence", "A7", "2816", "3093"),
        Aprr("aprr-pres-de-valence-3096", 44.90188, 4.87532, "Près de Valence", "Valence", "2802", "3096"),
        Aprr("aprr-pres-de-montelimar-3099", 44.47781, 4.766655, "Près de Montélimar", "Montélimar", "2804", "3099"),
        Aprr("aprr-douane-de-bardonnex-3102", 46.132616, 6.095834, "Douane de Bardonnex", "Bardonnex", "16914", "3102"),
        Aprr("aprr-peage-de-nangy-3105", 46.153267, 6.29466, "Péage de Nangy", "Nangy", "2820", "3105"),
        Aprr("aprr-peage-de-beuzeville-4762", 49.337747, 0.364839, "Péage de Beuzeville", "Beuzeville", "2718", "4762"),
        Aprr("aprr-peage-de-heudebouville-4765", 49.191238, 1.232373, "Péage de Heudebouville", "Heudebouville", "2720", "4765"),
        Aprr("aprr-peage-de-buchelay-4768", 48.983382, 1.681163, "Péage de Buchelay", "Buchelay", "2722", "4768"),
        Aprr("aprr-peage-de-dozule-4771", 49.229693, -0.076242, "Péage de Dozulé", "Dozulé", "2716", "4771"),
        Aprr("aprr-peage-de-dozule-4777", 49.2295, -0.076173, "Péage de Dozulé", "Dozulé", "2714", "4777"),
        Aprr("aprr-peage-de-beuzeville-4780", 49.33806, 0.364683, "Péage de Beuzeville", "Beuzeville", "2724", "4780"),
        Aprr("aprr-peage-de-heudebouville-4783", 49.191314, 1.232713, "Péage de Heudebouville", "Heudebouville", "2726", "4783"),
        Aprr("aprr-peage-de-buchelay-4786", 48.983952, 1.681262, "Péage de Buchelay", "Buchelay", "2728", "4786"),
        Aprr("aprr-environs-de-deauville-4789", 49.292469, 0.200382, "Environs de Deauville", "Deauville", "2730", "4789"),
        Aprr("aprr-peage-de-cottevrard-4792", 49.646763, 1.242798, "Péage de Cottevrard", "Cottevrard", "17776", "4792"),
        Aprr("aprr-peage-de-cottevrard-4795", 49.646763, 1.242798, "Péage de Cottévrard", "Cottévrard", "17778", "4795"),
        Aprr("aprr-environs-de-la-brede-4798", 44.693943, -0.496126, "Environs de La Brède", "La Brède", "2808", "4798"),
        Aprr("aprr-pres-de-bidart-4801", 43.440592, -1.568305, "Près de Bidart", "Bidart", "2810", "4801"),
        Aprr("aprr-pres-de-saint-jean-de-vedas-4810", 43.5827778, 3.8875, "Près de Saint-Jean-de-Védas", "Saint-Jean-de-Védas", "2812", "4810"),
        Aprr("aprr-environs-du-palays-toulouse-4819", 43.546277, 1.500164, "Environs du Palays (Toulouse)", "A61", "2814", "4819"),
        Aprr("aprr-aire-des-sucheres-pres-de-noiretable-4822", 45.8541667, 3.725833, "Aire des Suchères près de Noirétable", "Noirétable", "15042", "4822"),
        Aprr("aprr-nord-d-avignon-4825", 43.9733333, 4.895555555, "Nord d'Avignon", "A7", "15044", "4825"),
        Aprr("aprr-nord-de-perpignan-4828", 42.7427778, 2.88944, "Nord de Perpignan", "Perpignan", "15046", "4828"),
        Aprr("aprr-sud-de-poitiers-4831", 46.4525, -0.01638, "Sud de Poitiers", "Poitiers", "15212", "4831"),
        Aprr("aprr-nord-de-bordeaux-4861", 44.9902778, -0.4275, "Nord de Bordeaux", "Bordeaux", "15208", "4861"),
        Aprr("aprr-pres-de-cahors-4864", 44.39, 1.51638, "Près de Cahors", "Cahors", "15220", "4864"),
        Aprr("aprr-environs-d-egleton-4867", 45.3994, 1.99527, "Environs d'Egleton", "A89", "15216", "4867"),
        Aprr("aprr-vers-passy-viaduc-des-egratz-4870", 45.9322, 6.73427, "Vers Passy (viaduc des Egratz)", "RN 205", "5554", "4870"),
        Aprr("aprr-echangeur-d-etrembieres-4879", 46.180483193363, 6.23196847736835, "Échangeur d'Étrembières", "A40", "16916", "4879"),
        Aprr("aprr-peage-de-nangy-4882", 46.15392815, 6.291830689, "Péage de Nangy", "Nangy", "16918", "4882"),
        Aprr("aprr-echangeur-de-la-vallee-verte-4885", 46.14564072, 6.312820315, "Échangeur de la Vallée Verte", "A40", "16920", "4885"),
        Aprr("aprr-rampe-du-tunnel-du-mont-blanc-4888", 45.90096963, 6.859573871, "Rampe du Tunnel du Mont-Blanc", "Mont-Blanc", "16922", "4888"),
        Aprr("aprr-aire-de-repos-de-passy-4891", 45.923776, 6.658878, "Aire de repos de Passy", "Passy", "2822", "4891"),
        Aprr("aprr-nord-du-viaduc-de-millau-4894", 44.091972, 3.022076, "Nord du viaduc de Millau", "Millau", "18276", "4894"),
        Aprr("aprr-sud-du-viaduc-de-millau-4897", 44.087362, 3.021389, "Sud du viaduc de Millau", "Millau", "2824", "4897"),
        Aprr("aprr-environs-de-marcoussis-4900", 48.660441, 2.185425, "Environs de Marcoussis", "Marcoussis", "5568", "4900"),
        Aprr("aprr-environs-des-ulis-4903", 48.660724, 2.185994, "Environs des Ulis", "Ulis", "5578", "4903"),
        Aprr("aprr-bifurcation-a10-et-a71-pres-d-orleans-4906", 47.902743, 1.846377, "Bifurcation A10 et A71 près d'Orléans", "A10", "5556", "4906"),
        Aprr("aprr-environs-de-tours-centre-4909", 47.402202, 0.712362, "Environs de Tours-centre", "Tours-centre", "5570", "4909"),
        Aprr("aprr-tours-nord-4912", 47.417003, 0.730591, "Tours-nord", "A10", "5566", "4912"),
        Aprr("aprr-pres-de-chambray-les-tours-4915", 47.359118, 0.710333, "Près de Chambray-lès-Tours", "Chambray-lès-Tours", "5572", "4915"),
        Aprr("aprr-peage-de-saint-arnoult-4918", 48.555265, 1.935285, "Péage de Saint-Arnoult", "Saint-Arnoult", "5560", "4918"),
        Aprr("aprr-bifurcation-des-autoroutes-a11-et-a81-4921", 48.037976, 0.143718, "Bifurcation des autoroutes A11 et A81", "A11", "5576", "4921"),
        Aprr("aprr-peage-de-saint-arnoult-4924", 48.550564, 1.921874, "Péage de Saint-Arnoult", "Saint-Arnoult", "5558", "4924"),
        Aprr("aprr-bifurcation-des-autoroutes-a11-et-a28-sud-4927", 48.050316, 0.294228, "Bifurcation des autoroutes A11 et A28 Sud", "A11", "5582", "4927"),
        Aprr("aprr-bifurcation-des-autoroutes-a10-et-a85-4930", 47.302119, 0.692444, "Bifurcation des autoroutes A10 et A85", "A10", "5580", "4930"),
        Aprr("aprr-contournement-nord-de-nantes-4933", 47.268593, -1.56164, "Contournement Nord de Nantes", "Nantes", "5562", "4933"),
        Aprr("aprr-contournement-nord-de-nantes-4936", 47.268011, -1.574662, "Contournement Nord de Nantes", "Nantes", "5564", "4936"),
        Aprr("aprr-entree-tunnel-cna-angers-nord-4939", 47.495843, -0.564456, "Entrée tunnel CNA – Angers Nord", "A11", "11688", "4939"),
        Aprr("aprr-bifurcation-des-autoroutes-a71-et-a85-sud-4942", 47.29286, 2.044078, "Bifurcation des autoroutes A71 et A85 Sud", "A71", "5574", "4942"),
        Aprr("aprr-entree-du-tunnel-du-frejus-4945", 45.195827, 6.677329, "Entrée du Tunnel du Fréjus", "Fréjus", "5463", "4945"),
        Aprr("aprr-rampe-d-acces-au-tunnel-du-frejus-4948", 45.18854, 6.65477, "Rampe d'accès au Tunnel du Fréjus", "Fréjus", "5467", "4948"),
        Aprr("aprr-peage-de-saint-michel-de-maurienne-4951", 45.210063, 6.512648, "Péage de Saint-Michel de Maurienne", "Maurienne", "5471", "4951"),
        Aprr("aprr-les-hurtieres-4954", 45.50855, 6.30519, "Les Hurtières", "A43", "5475", "4954"),
        Aprr("aprr-environs-de-la-saulce-4957", 44.430459, 6.020802, "Environs de La Saulce", "La Saulce", "6976", "4957"),
        Aprr("aprr-environs-d-aix-en-provence-4960", 43.517122, 5.4318, "Environs d'Aix-en-Provence", "A8", "6980", "4960"),
        Aprr("aprr-quartier-saint-isidore-a-nice-4963", 43.70904, 7.194765, "Quartier saint-Isidore à Nice", "A8", "6984", "4963"),
        Aprr("aprr-peage-de-coutevroult-4966", 48.853739, 2.839084, "Péage de Coutevroult", "Coutevroult", "6960", "4966"),
        Aprr("aprr-peage-de-coutevroult-4969", 48.854056, 2.838387, "Péage de Coutevroult", "Coutevroult", "6968", "4969"),
        Aprr("aprr-peage-d-ormes-4972", 49.247918, 3.960806, "Péage d'Ormes", "A26", "7042", "4972"),
        Aprr("aprr-peage-d-ormes-4975", 49.247624, 3.961599, "Péage d'Ormes", "A26", "7038", "4975"),
        Aprr("aprr-peage-de-schwindratzheim-4978", 48.769504, 7.601876, "Péage de Schwindratzheim", "Schwindratzheim", "6964", "4978"),
        Aprr("aprr-peage-de-schwindratzheim-4981", 48.769679, 7.601812, "Péage de Schwindratzheim", "Schwindratzheim", "6972", "4981"),
        Aprr("aprr-peage-de-jules-verne-4984", 49.859318, 2.394386, "Péage de Jules Verne", "Jules Verne", "7408", "4984"),
        Aprr("aprr-peage-de-jules-verne-4987", 49.859117, 2.394407, "Péage de Jules Verne", "Jules Verne", "7404", "4987"),
        Aprr("aprr-peage-de-villefranche-limas-6313", 45.96944122354047, 4.7279718282152094, "Péage de Villefranche-Limas", "Villefranche-Limas", "2740", "6313"),
        Aprr("aprr-col-de-rossatiere-39826", 45.447158, 5.399744, "Col de Rossatière", "Rossatière", "20314", "39826"),
        Aprr("aprr-pres-de-villabe-56146", 48.592616, 2.444088, "Près de Villabé", "Villabé", "20346", "56146"),
        Aprr("aprr-aire-de-lisses-56149", 48.592616, 2.444088, "Aire de Lisses", "Lisses", "20348", "56149"),
        Aprr("aprr-aire-de-repos-d-evires-57592", 46.04721, 6.263947, "Aire de repos d'Evires", "A410", "20350", "57592"),
        Aprr("aprr-secteur-de-rumilly-57595", 45.819216, 6.013524, "Secteur de Rumilly", "Rumilly", "20352", "57595"),
        Aprr("aprr-aire-de-repos-du-lavaret-57598", 45.568044, 5.790624, "Aire de repos du Lavaret", "Lavaret", "20354", "57598"),
        Aprr("aprr-aire-de-repos-de-la-ravoire-57601", 46.017531, 6.117144, "Aire de repos de la Ravoire", "A41 ADELAC", "20356", "57601"),
    )

    suspend fun fetch(): List<CctvCamera> =
        YOUTUBE_LIVE + BORDEAUX_TOURISME + APRR_HIGHWAY.map { c ->
            CctvCamera(
                id = c.id,
                lat = c.lat,
                lng = c.lng,
                name = c.name,
                city = c.city,
                country = "France",
                streamUrl = "https://gieat.viewsurf.com/?id=${c.viewsurfId}&action=${c.action}",
                source = "APRR/AREA",
                externalUrl = "https://voyage.aprr.fr/node/${c.node}",
            )
        }
}
