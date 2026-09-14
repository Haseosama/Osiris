package com.osiris.app.data.repository

import com.osiris.app.data.model.LiveNewsFeed

/** The backend's `/api/live-news` never actually called anything upstream either — it just
 * returned this same hardcoded list of YouTube live-stream channels (curated for which ones
 * allow embedding). No source to port, just the list itself. */
class LiveNewsRepository {
    suspend fun fetch(baseUrl: String): List<LiveNewsFeed> = FEEDS

    companion object {
        private val FEEDS = listOf(
            LiveNewsFeed("nbcnews", "NBC News NOW", "New York", "US", 40.759, -73.980,
                "https://www.youtube.com/channel/UCeY0bbntWzzVIaj2z3QigXg/live", embedAllowed = false, category = "mainstream", language = "en"),
            LiveNewsFeed("cbsnews", "CBS News 24/7", "New York", "US", 40.764, -73.973,
                "https://www.youtube.com/channel/UC8p1vwvWtl6T73JiExfWs1g/live", embedAllowed = false, category = "mainstream", language = "en"),
            LiveNewsFeed("abcnews", "ABC News Live", "New York", "US", 40.763, -73.979,
                "https://www.youtube.com/channel/UCBi2mrWuNuyYy4gbM6fU18Q/live", embedAllowed = false, category = "mainstream", language = "en"),
            LiveNewsFeed("bloomberg", "Bloomberg TV", "New York", "US", 40.756, -73.988,
                "https://www.youtube.com/channel/UC_vQ72b7v5n2938v9d5c80w/live", embedAllowed = false, category = "finance", language = "en"),
            LiveNewsFeed("cspan", "C-SPAN", "Washington DC", "US", 38.897, -77.036,
                "https://www.youtube.com/channel/UCb--64Gl51jIEVE-GLDAVTg/live", embedAllowed = false, category = "government", language = "en"),
            LiveNewsFeed("cbc", "CBC News", "Toronto", "CA", 43.644, -79.387,
                "https://www.youtube.com/channel/UCKy1dAqELon0zgzZPOz9SVw/live", embedAllowed = false, category = "mainstream", language = "en"),
            LiveNewsFeed("skynews", "Sky News", "London", "GB", 51.500, -0.118,
                "https://www.youtube.com/embed/live_stream?channel=UCoMdktPbSTixAyNGwb-UYkQ&autoplay=1&mute=1", embedAllowed = true, category = "mainstream", language = "en"),
            LiveNewsFeed("france24en", "France 24 EN", "Paris", "FR", 48.830, 2.280,
                "https://www.youtube.com/embed/live_stream?channel=UCQfwfsi5VrQ8yKZ-UWmAEFg&autoplay=1&mute=1", embedAllowed = true, category = "mainstream", language = "en"),
            LiveNewsFeed("dwnews", "DW News", "Berlin", "DE", 52.508, 13.376,
                "https://www.youtube.com/embed/live_stream?channel=UCknLrEdhRCp1aegoMqRaCZg&autoplay=1&mute=1", embedAllowed = true, category = "mainstream", language = "en"),
            LiveNewsFeed("aljazeera", "Al Jazeera EN", "Doha", "QA", 25.286, 51.534,
                "https://www.youtube.com/embed/live_stream?channel=UCNye-wNBqNL5ZzHSJj3l8Bg&autoplay=1&mute=1", embedAllowed = true, category = "mainstream", language = "en"),
            LiveNewsFeed("nhkworld", "NHK World", "Tokyo", "JP", 35.690, 139.692,
                "https://www.youtube.com/embed/live_stream?channel=UCSPEjw8F2nQDtmUKPFNF7_A&autoplay=1&mute=1", embedAllowed = true, category = "mainstream", language = "en"),
            LiveNewsFeed("cna", "CNA 24/7", "Singapore", "SG", 1.290, 103.852,
                "https://www.youtube.com/embed/live_stream?channel=UC83jt4dlz1Gjl58fzQrrKZg&autoplay=1&mute=1", embedAllowed = true, category = "mainstream", language = "en"),
            LiveNewsFeed("wion", "WION", "New Delhi", "IN", 28.614, 77.209,
                "https://www.youtube.com/embed/live_stream?channel=UC_gUM8rL-Lrg6O3adPW9K1g&autoplay=1&mute=1", embedAllowed = true, category = "mainstream", language = "en"),
            LiveNewsFeed("cgtn", "CGTN", "Beijing", "CN", 39.904, 116.407,
                "https://www.youtube.com/channel/UCgrNz-aDmcr2uuto8_DL2jg/live", embedAllowed = false, category = "state", language = "en"),
            LiveNewsFeed("rt", "RT News", "Moscow", "RU", 55.755, 37.617,
                "https://rumble.com/c/RTNewsEN", embedAllowed = false, category = "state", language = "en"),
        )
    }
}
