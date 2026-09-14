package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Japan CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/japan.ts`, a fully static
 * list: 18 YouTube embeds (converted to external-link-only, see [FranceCctvSource]'s doc
 * comment) plus 33 MLIT river-monitoring cameras (`cam.river.go.jp`, direct JPEG snapshots
 * refreshed roughly every 60s, no Referer needed unlike SkylineWebcams). */
object JapanCctvSource {
    private fun yt(id: String, lat: Double, lng: Double, name: String, city: String, ytId: String, source: String) = CctvCamera(
        id = id, lat = lat, lng = lng, name = name, city = city, country = "Japan",
        source = source, externalUrl = "https://www.youtube.com/watch?v=$ytId",
    )

    private fun river(id: String, lat: Double, lng: Double, name: String, city: String, camPath: String) = CctvCamera(
        id = id, lat = lat, lng = lng, name = name, city = city, country = "Japan",
        feedUrl = "https://cam.river.go.jp/cam/now/$camPath",
        source = "MLIT river.go.jp",
    )

    suspend fun fetch(): List<CctvCamera> = listOf(
        yt("jp-shibuya-crossing", 35.6595, 139.7005, "Shibuya Scramble Crossing", "Tokyo", "coYw-eVU0Ks", "ANN News / YouTube"),
        yt("jp-tokyo-tower", 35.6586, 139.7454, "Tokyo Tower Live Cam", "Tokyo", "cbJ03Xk_eLQ", "YouTube"),
        yt("jp-mt-fuji", 35.3606, 138.7274, "Mt. Fuji Live", "Shizuoka/Yamanashi", "5aLh8R2HqOQ", "YouTube"),
        yt("jp-osaka-dotonbori", 34.6687, 135.5013, "Dotonbori Live Cam", "Osaka", "m6J9w94oBXY", "YouTube"),
        yt("jp-shinjuku-kabukicho", 35.6938, 139.7034, "Shinjuku Kabukicho Live", "Tokyo", "gFRtAAmiFbE", "YouTube"),
        yt("jp-akihabara", 35.6984, 139.7731, "Akihabara Electric Town", "Tokyo", "HULqEi0RqXI", "YouTube"),
        yt("jp-tokyo-skytree", 35.7101, 139.8107, "Tokyo Skytree Live", "Tokyo", "xIp5F2D8vQ0", "YouTube"),
        yt("jp-ginza", 35.6717, 139.7649, "Ginza 4-Chome Crossing", "Tokyo", "LYzCVlG6lkE", "YouTube"),
        yt("jp-yokohama-port", 35.4437, 139.6380, "Yokohama Port Live", "Yokohama", "dN4HRiQnAn4", "YouTube"),
        yt("jp-kyoto-kiyomizu", 34.9949, 135.7850, "Kyoto Arashiyama Bamboo Forest", "Kyoto", "Op-lf2NRMzs", "YouTube"),
        yt("jp-hiroshima-dome", 34.3955, 132.4536, "Hiroshima Peace Memorial", "Hiroshima", "R6-G_4W5K_M", "YouTube"),
        yt("jp-sapporo-odori", 43.0588, 141.3563, "Sapporo Odori Park", "Sapporo", "N7k3Q5rMZfM", "YouTube"),
        yt("jp-naha-kokusai", 26.3358, 127.6809, "Naha Kokusai Street", "Naha/Okinawa", "cKTkCqVB00A", "YouTube"),
        yt("jp-fukuoka-hakata", 33.5898, 130.4017, "Fukuoka Hakata Station", "Fukuoka", "xvN_GxkVjKs", "YouTube"),
        yt("jp-nagoya-station", 35.1709, 136.8815, "Nagoya Station Area", "Nagoya", "Oji-G0UhD9U", "YouTube"),
        yt("jp-kobe-harbor", 34.6851, 135.1956, "Kobe Harborland", "Kobe", "3xGw0xQBN0s", "YouTube"),
        yt("jp-asakusa-sensoji", 35.7148, 139.7967, "Asakusa Senso-ji Temple", "Tokyo", "Ic5FaEzh6h0", "YouTube"),
        yt("jp-tokyo-bay", 35.6279, 139.7742, "Tokyo Bay Waterfront", "Tokyo", "Y9X1W8HBE4g", "YouTube"),

        river("jp-river-aoba-meguro", 35.649111, 139.693419, "Aoba Platform, Meguro Ward", "Tokyo", "303329015.jpg"),
        river("jp-river-asayama-bridge", 35.536928, 139.498187, "Asayama Bridge", "Yokohama", "303585018.jpg"),
        river("jp-river-atago-shirane", 35.47929, 139.552883, "Atago-Shirane Bridge", "Yokohama", "303585047.jpg"),
        river("jp-river-banri-bridge", 35.463406, 139.622166, "Banri Bridge, Karasido River", "Yokohama", "103585088.jpg"),
        river("jp-river-bentenjima", 35.639903, 139.887903, "Bentenjima Bridge, Urayasu", "Tokyo", "103073090.jpg"),
        river("jp-river-expressway7", 35.698164, 139.859811, "Capital Expressway 7, Arakawa", "Tokyo", "221281005.jpg"),
        river("jp-river-denenchofu", 35.582931, 139.672006, "Denenchofu Water Level Station", "Tokyo", "cctv_130001_31C03351.jpg"),
        river("jp-river-denenchofu-ota", 35.589261, 139.665769, "Denenchofu Station, Ota Ward", "Tokyo", "221320032.jpg"),
        river("jp-river-ebara-pond", 35.627906, 139.716111, "Ebara Regulation Pond, Shinagawa", "Tokyo", "303329016.jpg"),
        river("jp-river-futako-tamagawa", 35.610458, 139.629478, "Futako Tamagawa Rise Tower, Setagaya", "Tokyo", "221320007.jpg"),
        river("jp-river-hachiman", 35.41315, 139.628097, "Hachiman Bridge", "Yokohama", "303585121.jpg"),
        river("jp-river-heiwa", 35.46374, 139.588114, "Heiwa Bridge, Karasido River", "Yokohama", "103585089.jpg"),
        river("jp-river-tamagawa-green", 35.585, 139.668, "Tamagawa Green Area Office, Ota Ward", "Tokyo", "cctv_130001_31C03399.jpg"),
        river("jp-river-shino-bridge", 35.651, 139.746, "Shī-no-Bridge, Minato Ward", "Tokyo", "303329013.jpg"),
        river("jp-river-edogawa-1", 35.7088, 139.8772, "Edogawa River Upstream", "Tokyo", "221281001.jpg"),
        river("jp-river-edogawa-2", 35.7152, 139.8690, "Edogawa River Komatsugawa", "Tokyo", "221281002.jpg"),
        river("jp-river-arakawa-1", 35.7931, 139.7195, "Arakawa River, Kita Ward", "Tokyo", "221281003.jpg"),
        river("jp-river-arakawa-2", 35.7500, 139.7855, "Arakawa River, Adachi Ward", "Tokyo", "221281004.jpg"),
        river("jp-river-tamagawa-1", 35.5950, 139.6500, "Tama River, Ota Ward", "Tokyo", "221320001.jpg"),
        river("jp-river-tamagawa-2", 35.6020, 139.6350, "Tama River, Setagaya Ward", "Tokyo", "221320002.jpg"),
        river("jp-river-tamagawa-4", 35.6200, 139.6100, "Tama River, Komae", "Tokyo", "221320004.jpg"),
        river("jp-river-tamagawa-5", 35.6350, 139.5900, "Tama River, Chofu", "Tokyo", "221320005.jpg"),
        river("jp-river-tamagawa-6", 35.6450, 139.5650, "Tama River, Fuchu", "Tokyo", "221320006.jpg"),
        river("jp-river-sumida-1", 35.7200, 139.8000, "Sumida River, Asakusa", "Tokyo", "221281010.jpg"),
        river("jp-river-sumida-2", 35.6900, 139.7950, "Sumida River, Ryogoku", "Tokyo", "221281011.jpg"),
        river("jp-river-sumida-3", 35.6600, 139.7850, "Sumida River, Tsukiji", "Tokyo", "221281012.jpg"),
        river("jp-river-nakagawa-1", 35.7600, 139.8500, "Naka River, Katsushika Ward", "Tokyo", "221281020.jpg"),
        river("jp-river-tsurumi-1", 35.5100, 139.6700, "Tsurumi River, Yokohama", "Yokohama", "303585001.jpg"),
        river("jp-river-tsurumi-2", 35.5250, 139.6200, "Tsurumi River, Midori Ward", "Yokohama", "303585002.jpg"),
        river("jp-river-meguro-1", 35.6400, 139.7100, "Meguro River, Meguro Ward", "Tokyo", "303329001.jpg"),
        river("jp-river-meguro-2", 35.6320, 139.7200, "Meguro River, Shinagawa Ward", "Tokyo", "303329002.jpg"),
        river("jp-river-kanda-1", 35.7000, 139.7600, "Kanda River, Chiyoda", "Tokyo", "303329005.jpg"),
        river("jp-river-shakujii-1", 35.7400, 139.6700, "Shakujii River, Nerima", "Tokyo", "303329010.jpg"),
    )
}
