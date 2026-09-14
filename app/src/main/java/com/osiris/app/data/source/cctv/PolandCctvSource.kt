package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/**
 * Poland CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/poland.ts`, a fully static
 * list (no network call). Gdansk's YouTube embed and Słupsk's `stream_type: 'mjpeg'` feed both
 * become external-link-only entries — same reasoning as France's YouTube embeds
 * ([FranceCctvSource]): no player here can render either format directly. The other 71 —
 * nadmorski24.pl's Baltic coast network — are real `ls.tkchopin.pl` HLS streams that play
 * directly.
 */
object PolandCctvSource {

    private data class Nadmorski(val id: String, val lat: Double, val lng: Double, val name: String, val city: String, val streamUrl: String)

    private val MANUAL = listOf(
        CctvCamera(
            id = "pl-gdansk-1", lat = 54.3520, lng = 18.6466, name = "Gdansk - City View",
            city = "Gdansk", country = "Poland", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=NZ_ZiHAx8Ic",
        ),
        CctvCamera(
            id = "pl-slupsk-1", lat = 54.46474539073614, lng = 17.026874823866272,
            name = "Słupsk - Centrum (Ratusz)", city = "Słupsk", country = "Poland",
            source = "Urząd Miasta Słupsk", externalUrl = "https://www.slupsk.pl/kamera2",
        ),
    )

    private val NADMORSKI = listOf(
        Nadmorski("pl-nadm-2", 54.48052014236284, 18.564330339431766, "Gdynia - Orłowo", "Gdynia", "https://ls.tkchopin.pl/live/gdynia_orlowo_plaza_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-3", 54.35191693108313, 18.658819198608402, "Gdańsk - Stare Miasto, Motława", "Gdańsk", "https://ls.tkchopin.pl/live/gdansk_olowianka_widok_motlawa_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-4", 54.82870259306066, 17.94723987579346, "Białogóra - Plaża", "Białogóra", "https://ls.tkchopin.pl/live/bialogora_plaza_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-5", 54.61055943936092, 18.181536197662357, "Bolszewo - Skrzyżowanie Zamostna/DW468", "Bolszewo", "https://ls.tkchopin.pl/live/bolszewo_skrzyzowanie_k6_zamostna_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-6", 54.77307471900191, 18.02515268325806, "Brzyno - Przystań", "Brzyno", "https://ls.tkchopin.pl/live/brzyno_przystan_j_zarnowieckie_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-7", 54.75998068704836, 18.50546926259995, "Chałupy - Przystań", "Chałupy", "https://ls.tkchopin.pl/live/chalupy_molo_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-8", 54.829468912628776, 18.087283372879032, "Dębki - ul. Morska", "Dębki", "https://ls.tkchopin.pl/live/debki_ulica_morska_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-9", 54.83304075870433, 18.08807730674744, "Dębki - Plaża", "Dębki", "https://ls.tkchopin.pl/live/debki_plaza_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-10", 54.833043848399754, 18.08834552764893, "Dębki - Plaża (kamera obrotowa)", "Dębki", "https://ls.tkchopin.pl/live/debki_plaza_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-11", 54.368183549741005, 18.778617382049564, "Gdańsk - Górki Zachodnie NCŻ", "Gdańsk", "https://ls.tkchopin.pl/live/gdansk_gorki_zachodnie_ncz_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-12", 54.34760851181469, 18.648573160171512, "Gdańsk - Teatr Szekspirowski", "Gdańsk", "https://ls.tkchopin.pl/live/gdansk_centrum_teatr_szekspirowski_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-13", 54.51774365884892, 18.55496406555176, "Gdynia - Przystań jachtowa", "Gdynia", "https://ls.tkchopin.pl/live/gdynia_marina_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-14", 54.51878364838858, 18.55346202850342, "Gdynia - Molo Południowe", "Gdynia", "https://ls.tkchopin.pl/live/gdynia_al_jana_pawla_nabrzerze_pomorskie_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-15", 53.83392279530925, 18.82345318794251, "Gniew - Panorama miasta", "Gniew", "https://ls.tkchopin.pl/live/gniew_widok_rynek_wieza_kosciola_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-16", 53.82994016522363, 18.837003707885746, "Gniew - Zakole Wisły", "Gniew", "https://ls.tkchopin.pl/live/gniew_widok_zakole_wisly_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-17", 54.752331515437675, 18.38520169258118, "Gnieżdżewo - Rondo", "Gnieżdżewo", "https://ls.tkchopin.pl/live/gniezdzewo_rondo_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-18", 54.604935923129695, 18.801383972167972, "Hel - Bulwar Nadmorski", "Hel", "https://ls.tkchopin.pl/live/hel_bulwar_nadmorski_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-19", 54.61098194842774, 18.801593184471134, "Hel - ul. Dworcowa", "Hel", "https://ls.tkchopin.pl/live/hel_skrzyzowanie_dworcowa_przybyszewskiego_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-20", 54.697063520549094, 18.66937637329102, "Jastarnia - Molo", "Jastarnia", "https://ls.tkchopin.pl/live/jastarnia_molo_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-21", 54.706028002542894, 18.657799959182743, "Jastarnia - Rondo", "Jastarnia", "https://ls.tkchopin.pl/live/jastarnia_rondo_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-22", 54.83543829125787, 18.305261135101322, "Jastrzębia Góra - Plaża", "Jastrzębia Góra", "https://ls.tkchopin.pl/live/jastrzebiagora_plaza_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-23", 54.68189502493287, 18.71078968048096, "Jurata - Molo", "Jurata", "https://ls.tkchopin.pl/live/jurata_molo_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-24", 54.83227450693353, 18.209259510040287, "Karwia - Plaża", "Karwia", "https://ls.tkchopin.pl/live/karwia_plaza_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-25", 54.77908955902161, 18.16372632980347, "Krokowa - Rondo", "Krokowa", "https://ls.tkchopin.pl/live/krokowa_rondo_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-26", 54.77761068435877, 18.06357264518738, "Lubkowo - Przystań", "Lubkowo", "https://ls.tkchopin.pl/live/lubkowo_przystan_wies_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-27", 54.723275645706956, 18.41391742229462, "Puck - Plaża, Przystań, Molo", "Puck", "https://ls.tkchopin.pl/live/puck_molo_port_jachtowy_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-28", 54.72291626555111, 18.414008617401127, "Puck - Molo", "Puck", "https://ls.tkchopin.pl/live/puck_molo_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-29", 54.7204575, 18.4112916, "Puck - Stary Rynek", "Puck", "https://ls.tkchopin.pl/live/puck_stary_rynek_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-30", 54.60303742264476, 18.355594279211747, "Reda - Gniazdo pustułek", "Reda", "https://ls.tkchopin.pl/live/reda_gniazdo_pustulek_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-31", 54.60523420648384, 18.34751129150391, "Reda - Skrzyżowanie", "Reda", "https://ls.tkchopin.pl/live/reda_skrzyzowanie_pucka_k6_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-32", 54.44615123082206, 18.570799827575687, "Sopot - Molo", "Sopot", "https://ls.tkchopin.pl/live/sopot_molo_skwer_kuracyjny_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-33", 54.44928282456209, 18.568353652954105, "Sopot - Plaża, Małe Molo", "Sopot", "https://ls.tkchopin.pl/live/sopot_plaza_male_molo_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-34", 54.60262415303196, 18.22790622711182, "Wejherowo - Filharmonia Kaszubska", "Wejherowo", "https://ls.tkchopin.pl/live/wejherowo_filharmonia_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-35", 54.60293488245544, 18.299982547760013, "Wejherowo - ul. Orzeszkowej", "Wejherowo", "https://ls.tkchopin.pl/live/wejherowo_skrzyzowanie_orzeszkowej_k6_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-36", 54.60189082218993, 18.23940753936768, "Wejherowo - Plac Jakuba Wejhera", "Wejherowo", "https://ls.tkchopin.pl/live/wejherowo_plac_jakuba_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-37", 54.60356876312585, 18.261272907257084, "Wejherowo - ul. Rybacka", "Wejherowo", "https://ls.tkchopin.pl/live/wejherowo_skrzyzowanie_rybacka_k6_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-38", 54.79970463706625, 18.399116992950443, "Władysławowo - Aleja Gwiazd Sportu", "Władysławowo", "https://ls.tkchopin.pl/live/wladyslawowo_aleja_gwiazd.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-39", 54.79545257447648, 18.412077426910404, "Władysławowo - Widok na Bałtyk", "Władysławowo", "https://ls.tkchopin.pl/live/wladyslawowo_widok_na_baltyk_z_wiezy_domu_rybaka_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-40", 54.7818349, 18.4435644, "Władysławowo - Małe Morze", "Władysławowo", "https://ls.tkchopin.pl/live/wladyslawowo_kemping_male_morze_molo_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-41", 54.800827106961634, 18.404776453971866, "Władysławowo - Plaża", "Władysławowo", "https://ls.tkchopin.pl/live/wladyslawowo_plaza_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-42", 54.79448149526527, 18.409320116043094, "Władysławowo - Półwysep Helski", "Władysławowo", "https://ls.tkchopin.pl/live/wladyslawowo_widok_na_polwysep_helski_z_wiezy_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-43", 54.79743177198074, 18.41489911079407, "Władysławowo - Port", "Władysławowo", "https://ls.tkchopin.pl/live/wladyslawowo_port_nabrzeze_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-44", 54.79012990801725, 18.408751487731937, "Władysławowo - Rondo", "Władysławowo", "https://ls.tkchopin.pl/live/wladyslawowo_rondo_gdanska_starowiejska_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-46", 54.75949472440226, 18.504651188850406, "Chałupy - FunSurf", "Chałupy", "https://ls.tkchopin.pl/live/chalupy_szkolka_funsurf.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-47", 53.834074748257535, 18.82712004092468, "Gniew - Zamek", "Gniew", "https://ls.tkchopin.pl/live/gniew_widok_na_zamek_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-49", 54.6016686, 18.2396967, "Wejherowo - Plac Jakuba Wejhera, Fontanna", "Wejherowo", "https://ls.tkchopin.pl/live/wejherowo_rynek_fontanna_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-50", 54.83276268514629, 18.088002204895023, "Dębki - Widok z wieży", "Dębki", "https://ls.tkchopin.pl/live/debki_wieza_rybaczowka_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-52", 53.46979741054067, 18.732097148895267, "Grudziądz - Widok na Wisłę", "Grudziądz", "https://ls.tkchopin.pl/live/grudziadz_wisla_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-54", 54.7217755, 18.4110611, "Puck - Zatoka Pucka", "Puck", "https://ls.tkchopin.pl/live/puck_wieza_kosciola_zatokapucka_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-55", 54.600417905932005, 18.341621160507206, "Reda - Jara, panorama miasta", "Reda", "https://ls.tkchopin.pl/live/reda_jara_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-56", 54.59200821169998, 18.227348327636722, "Wejherowo - Panorama miasta", "Wejherowo", "https://ls.tkchopin.pl/live/wejherowo_wzgorze_wolnosci_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-82", 54.7183419, 18.0523041, "Gniewino - Kaszubskie Oko", "Gniewino", "https://ls.tkchopin.pl/live/gniewino_kaszubskie_oko_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-83", 54.715113, 18.6336011, "Jastarnia - Kemping Maszoperia", "Jastarnia", "https://ls.tkchopin.pl/live/jastarnia_kemping_maszoperia_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-84", 54.741680322686186, 18.059549331665043, "Nadole - Przystań", "Nadole", "https://ls.tkchopin.pl/live/nadole_przystan_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-88", 54.757668443190546, 18.078432083129886, "Lubkowo - DPS, Przystań", "Lubkowo", "https://ls.tkchopin.pl/live/lubkowo_przystan_dps_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-91", 54.18634432098426, 15.553550720214846, "Kołobrzeg - Port", "Kołobrzeg", "https://ls.tkchopin.pl/live/kolobrzeg_port_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-94", 54.60124759316259, 18.36521923542023, "Reda - MPCK Koksik, gniazdo sokoła", "Reda", "https://ls.tkchopin.pl/live/reda_koksik_gniazdo_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-95", 54.60116990780972, 18.365643024444584, "Reda - MPCK Koksik, gniazdo sokoła platforma", "Reda", "https://ls.tkchopin.pl/live/reda_koksik_gniazdo_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-96", 54.808043565519235, 18.382616043090824, "Chłapowo - Bałtyk z kempingu Lazurowe", "Chłapowo", "https://ls.tkchopin.pl/live/wladyslawowo_kemping_lazurowe_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-99", 54.69959621848603, 18.683205842971805, "Jastarnia - Plaża, Dom Zdrojowy", "Jastarnia", "https://ls.tkchopin.pl/live/jastarnia_plaza_dom_zdrojowy_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-100", 54.76172948523679, 18.497194647789005, "Chałupy - Kite.pl", "Chałupy", "https://ls.tkchopin.pl/live/chalupy6_szkolka_kitepl.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-101", 53.906145847299236, 14.236114025115969, "Świnoujście - Centrum", "Świnoujście", "https://ls.tkchopin.pl/live/swinoujscie_centrum_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-103", 54.609807610910956, 18.256509304046634, "Wejherowo - OPEC, gniazdo sokoła", "Wejherowo", "https://ls.tkchopin.pl/live/wejherowo_opec_gniazdo_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-104", 54.60968334040826, 18.256552219390873, "Wejherowo - OPEC, gniazdo sokoła platforma", "Wejherowo", "https://ls.tkchopin.pl/live/wejherowo_opec_gniazdo_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-105", 54.615088756567886, 18.178760111331943, "Bolszewo - ArtPark", "Bolszewo", "https://ls.tkchopin.pl/live/bolszewo_artpark_static.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-106", 54.63222601228011, 18.504356145858768, "Rewa - Surfstacja", "Rewa", "https://ls.tkchopin.pl/live/rewa_surf_stacja_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-108", 54.56474, 18.3830754, "Rumia - Wzgórze Markowca", "Rumia", "https://ls.tkchopin.pl/live/rumia_wzgorze_markowca_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-110", 54.08211474414523, 18.74384385582091, "Tczew - GPEC, gniazdo sokoła (wnętrze)", "Tczew", "https://ls.tkchopin.pl/live/tczew_gpec_gniazdo_wnetrze_720P.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-111", 54.082146213160655, 18.743844264094125, "Tczew - GPEC, gniazdo sokoła", "Tczew", "https://ls.tkchopin.pl/live/tczew_gpec_gniazdo_podest_720P.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-10113", 54.44848747234753, 18.577804576303542, "Sopot - Widok z mola", "Sopot", "https://ls.tkchopin.pl/live/sopot_molo_meridian_obrotowa.stream/playlist.m3u8"),
        Nadmorski("pl-nadm-10114", 54.75885708303593, 18.507906806608744, "Chałupy - Solar EASY/SURF", "Chałupy", "https://ls.tkchopin.pl/live/easysurf_1080P.stream/playlist.m3u8"),
    )

    suspend fun fetch(): List<CctvCamera> =
        MANUAL + NADMORSKI.map { n ->
            CctvCamera(
                id = n.id, lat = n.lat, lng = n.lng, name = n.name, city = n.city,
                country = "Poland", streamUrl = n.streamUrl, source = "nadmorski24.pl",
            )
        }
}
