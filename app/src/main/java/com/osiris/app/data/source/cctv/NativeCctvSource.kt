package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Aggregates every CCTV region ported off the backend — see the "no backend" migration plan,
 * Phase 5. Built up in batches across `osiris-backend/src/app/api/cctv/` (all `*.ts` files) ~45 sources:
 * Batch 1: [TflCctvSource] (UK), [WsdotCctvSource]/[CaltransCctvSource] (Washington/California),
 * [FranceCctvSource] (APRR/AREA's 123 highway webcams + static list).
 * Batch 2: the IBI 511 US states — [FloridaCctvSource], [GeorgiaCctvSource],
 * [NorthCarolinaCctvSource], [ArizonaCctvSource] via the shared [Ibi511] loader, plus
 * [LouisianaCctvSource] on the same platform with its own record shape.
 * Batch 3: [AustraliaCctvSource]/[FinlandCctvSource] (keyless APIs), [PolandCctvSource] (real
 * HLS streams), [BulgariaCctvSource], [SerbiaCctvSource], [MacedoniaCctvSource],
 * [GermanyCctvSource], [SlovakiaCctvSource], [CzechiaCctvSource].
 * Batch 4: [UtahCctvSource]/[NevadaCctvSource] (two more IBI 511 states, each with its own small
 * loader), [IcelandCctvSource], [NewZealandCctvSource] (XML), [OregonCctvSource],
 * [MichiganCctvSource] (HTML-embedded fields), [AsfinagCctvSource], [IndianaCctvSource]
 * (GraphQL POST, HLS URLs rebuilt from a poster-frame token), [SwitzerlandCctvSource].
 * Batch 5 (final — every remaining region): [com.osiris.app.map.CctvViewerDialog] gained a
 * generic Referer-header image loader, unlocking every SkylineWebcams-fed source that needed
 * it — [ItalyCctvSource] (100% Skyline), [NetherlandsCctvSource], plus the two big generated
 * lists [AsiaLiveCctvSource]/[LatamLiveCctvSource]/[AfricaLiveCctvSource]/[EuropeLiveCctvSource]
 * (~600 cameras together) and [TaiwanCctvSource]'s THB index. New this batch: [OpenCctv]'s
 * shared loader for [EastAsiaCctvSource]/[SeAsiaCctvSource]/[WestAsiaCctvSource],
 * [HongKongCctvSource], [ThailandCctvSource], [SingaporeCctvSource], [SpainCctvSource] (DGT +
 * Skyline), [JapanCctvSource] (MLIT river cameras + YouTube), [CanadaCctvSource] (seven
 * unrelated municipal/provincial sources fetched in parallel), [UsCentralCctvSource] (Illinois),
 * [UsEastCctvSource]/[MiddleEastCctvSource] (curated), [RomaniaCctvSource]. Turkey (backend's
 * own source emptied, Windy.com embed restrictions) and Greece (IPCamLive's web player used as
 * both image and stream URL — neither actually loads as either) have nothing portable.
 *
 * Every region `route.ts` served is now covered — [com.osiris.app.data.repository.CctvRepository]
 * is just this list, no backend involved at all any more.
 */
object NativeCctvSource {
    private val SOURCES: List<suspend () -> List<CctvCamera>> = listOf(
        TflCctvSource::fetch,
        WsdotCctvSource::fetch,
        CaltransCctvSource::fetch,
        FranceCctvSource::fetch,
        FloridaCctvSource::fetch,
        GeorgiaCctvSource::fetch,
        NorthCarolinaCctvSource::fetch,
        ArizonaCctvSource::fetch,
        LouisianaCctvSource::fetch,
        AustraliaCctvSource::fetch,
        FinlandCctvSource::fetch,
        PolandCctvSource::fetch,
        BulgariaCctvSource::fetch,
        SerbiaCctvSource::fetch,
        MacedoniaCctvSource::fetch,
        GermanyCctvSource::fetch,
        SlovakiaCctvSource::fetch,
        CzechiaCctvSource::fetch,
        UtahCctvSource::fetch,
        NevadaCctvSource::fetch,
        SwitzerlandCctvSource::fetch,
        IcelandCctvSource::fetch,
        NewZealandCctvSource::fetch,
        OregonCctvSource::fetch,
        MichiganCctvSource::fetch,
        AsfinagCctvSource::fetch,
        IndianaCctvSource::fetch,
        NetherlandsCctvSource::fetch,
        ItalyCctvSource::fetch,
        AsiaLiveCctvSource::fetch,
        LatamLiveCctvSource::fetch,
        AfricaLiveCctvSource::fetch,
        EuropeLiveCctvSource::fetch,
        EastAsiaCctvSource::fetch,
        SeAsiaCctvSource::fetch,
        WestAsiaCctvSource::fetch,
        HongKongCctvSource::fetch,
        TaiwanCctvSource::fetch,
        ThailandCctvSource::fetch,
        SingaporeCctvSource::fetch,
        UsCentralCctvSource::fetch,
        UsEastCctvSource::fetch,
        MiddleEastCctvSource::fetch,
        CanadaCctvSource::fetch,
        SpainCctvSource::fetch,
        JapanCctvSource::fetch,
        RomaniaCctvSource::fetch,
    )

    suspend fun fetch(): List<CctvCamera> = coroutineScope {
        SOURCES.map { source -> async { source() } }.map { it.await() }.flatten()
    }
}
