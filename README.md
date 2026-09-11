# Osiris — client Android natif

Client Android natif (Kotlin/Jetpack Compose, **zéro WebView**) pour
[Osiris](https://github.com/simplifaisoul/osiris), le dashboard OSINT open source (vols,
séismes, incendies, CCTV, actu 24/7, météo sévère, espace, cyber, zones de conflit, crypto/OFAC,
Telegram OSINT, toolkit RECON...).

## Architecture

Osiris (le dépôt web) est une appli Next.js dont ~40 routes `/api/*` agrègent en temps réel des
dizaines de sources externes (OpenSky, USGS, NASA FIRMS, TfL/WSDOT/Caltrans, GDELT, OpenSanctions,
NVD...). Plutôt que de dupliquer toute cette logique serveur en Kotlin, cette appli **réécrit
entièrement le client** en natif et consomme l'API JSON déjà existante d'une instance Osiris que
tu auto-héberges.

```
Appli Android (Kotlin/Compose, MapLibre Native)  →  ton backend Osiris auto-hébergé (Docker)
```

### Pile technique

- Kotlin + Jetpack Compose + Material3
- **MapLibre Native Android SDK** — rendu carte vectoriel GPU, équivalent natif de MapLibre GL JS
- Retrofit + OkHttp + kotlinx.serialization pour l'API REST
- Coroutines/Flow pour le polling par couche (une couche n'interroge le backend que si elle est
  activée, à la manière du `layerFetchedRef` du frontend web)
- DataStore Preferences pour l'URL du backend

## 1. Héberger le backend Osiris

Cette appli ne fonctionne qu'avec une instance Osiris joignable depuis ton téléphone. Deux
façons de la lancer :

**Node.js direct** (le plus simple pour tester en local) :

```bash
git clone https://github.com/simplifaisoul/osiris.git
cd osiris
npm install
npm run dev
```

Ouvre [http://localhost:3000](http://localhost:3000) sur ton PC pour vérifier que ça tourne.
Fonctionne sans aucune clé API — toutes les couches keyless marchent tout de suite, seul le
scanner RECON répond 503 tant que `SCANNER_URL`/`SCANNER_KEY` ne sont pas renseignés dans un
`.env` (`cp .env.example .env`).

**Docker** (isolation, redémarre en arrière-plan) :

```bash
cp .env.example .env
docker compose up -d
```

Voir [DOCKER.md](https://github.com/simplifaisoul/osiris/blob/master/DOCKER.md) du dépôt Osiris
pour l'auto-hébergement complet (CasaOS, clés API optionnelles FIRMS/OpenSky/N2YO, scanner RECON).

Dans les deux cas, ton téléphone doit joindre l'IP locale de ton PC (même Wi-Fi), pas
`localhost` — vois l'étape 2.

## 2. Ouvrir ce projet

`File > Open` dans Android Studio, sélectionne ce dossier, laisse Gradle synchroniser.

Au premier lancement, ouvre **Réglages** et renseigne l'URL de ton backend (ex.
`http://192.168.1.10:3000`), puis « Tester la connexion ».

## Roadmap

- [x] **Phase 0** — socle (carte MapLibre, réglages backend, réseau)
- [x] **Phase 1** — vols, séismes, incendies, météo sévère, zones de conflit
- [x] **Phase 2** — maritime (ports/chokepoints/AIS), satellites (SGP4 déjà calculé côté
      backend), actu en direct (tap sur un dot → WebView si `embed_allowed`, sinon ouverture
      externe — `/api/live-news` sert des flux YouTube, pas du HLS brut), cyberattaques
      (lignes statiques source→cible ; pas encore d'animation comme sur le web)
- [x] **Phase 3** — CCTV (`/api/cctv`, ~17k caméras, clustering MapLibre natif ; tap sur une
      caméra individuelle → snapshot JPEG ou lien externe pour les flux MP4 ; zoome pour faire
      éclater un cluster, pas encore de tap-to-zoom dédié), OSINT Telegram (`/api/news`, en fait
      le flux Telegram/RSS géoparsé — pas un flux "actu" classique malgré son nom ; tap sur un
      point → titre/description/lien)
- [x] **Phase 4** — panneau RECON (icône outils sur la carte) : scanner réseau (`/api/scanner`,
      503 tant que `SCANNER_URL`/`SCANNER_KEY` ne sont pas configurés côté backend), DNS, WHOIS,
      certificats SSL, IP intelligence, CVE, wallet crypto, sanctions OFAC, espace
      (`/api/space-weather`). Chaque outil affiche la réponse JSON brute mise en forme plutôt que
      des vues dédiées par outil — suffisant pour un premier jet, à enrichir en Phase 5 si besoin
- [x] **Phase 5** — vues RECON dédiées pour les 4 outils qui s'y prêtaient bien (CVE, DNS, IP
      Intelligence, Sanctions OFAC — CveResult/DnsResult/IpIntelResult/SanctionsResult typés,
      avec repli automatique sur le JSON brut si le décodage échoue) ; scanner/WHOIS/certificats
      SSL/wallet crypto/espace restent en JSON brut, leurs schémas étant trop profonds ou trop
      instables pour le rapport effort/valeur d'un DTO dédié ; bannière "Backend injoignable" sur
      la carte quand toutes les couches actives échouent contre un backend pourtant configuré
      (distinct du bandeau "backend non configuré")
- [x] **Cache local par couche** — `LayerCache` persiste le dernier payload reçu de chaque
      couche dans un fichier JSON privé (`filesDir/layer_cache/`), rechargé au lancement avant
      que le premier polling démarre (pour ne pas se faire écraser par une lecture disque plus
      lente qu'une réponse réseau) : la carte affiche les dernières données connues
      immédiatement plutôt que de repartir à vide à chaque ouverture de l'appli
- [x] **Polish 2** :
      - Vues dédiées WHOIS et wallet crypto (WhoisResult/CryptoWalletResult typés, schémas
        confirmés depuis les sources `route.ts`/`chainIntel.ts` du repo Osiris) ; le scanner et
        les certificats SSL passent maintenant par `JsonTreeView`, un arbre JSON indenté
        générique plutôt qu'un bloc monospace plat — pas de DTO dédié (le scanner proxie un
        microservice externe dont le schéma varie par `type` de scan et n'est pas dans ce repo)
      - Tap-to-zoom sur un cluster CCTV : `GeoJsonSource.getClusterExpansionZoom()` + anime la
        caméra vers ce niveau de zoom
      - Arcs de cyberattaques animés : `ArcMath` calcule une courbe de Bézier quadratique
        (bulge perpendiculaire) partagée entre la ligne statique (LayersController) et un point
        qui voyage dessus, repositionné toutes les ~80ms par une coroutine dans MapViewModel
        tant que la couche est active (annulée à la désactivation)
      - Icône d'appli retravaillée (double anneau radar + points de contact colorés)
      - `LayerCache` purge maintenant les fichiers de plus de 24h au lieu de les servir
        indéfiniment (basé sur `File.lastModified()`, pas d'horodatage dans le JSON)
- [x] **Fiches d'info par entité** — vols, séismes, incendies, météo, zones de conflit, ports,
      points de passage, navires et satellites sont désormais tapables comme CCTV/OSINT/actu
      l'étaient déjà. Chaque feature GeoJSON porte une propriété `idx` (position dans la liste
      de la couche au moment du rendu) ; au tap, `MapScreen` relit cette liste au même index et
      construit un `InfoDialogContent` (titre/sous-titre/lignes) via une fonction d'extension
      par type de modèle (`FlightMarker.toInfoDialog()`, etc.), affiché par un unique
      `EntityInfoDialog` réutilisable plutôt que neuf boîtes de dialogue quasi identiques
- [x] **Icônes pictographiques** — vols (avion, orienté selon le cap via `icon-rotate`),
      satellites, navires (bateau, orienté selon le cap) et ports (ancre) remplacent leurs
      cercles colorés par de vraies icônes ; `IconBitmaps` rend un `VectorDrawable` en bitmap
      teinté par (icône, couleur), un par catégorie existante (4 pour les vols, 6 pour les
      satellites, 3 pour les ports) donc le code couleur par catégorie qui marchait déjà en
      `circleColor` continue de marcher en `iconImage`, juste avec des bitmaps différents par
      teinte plutôt qu'un remplissage dynamique. Séismes/météo/conflits/points de passage
      restent des cercles (marqueur de zone/magnitude, pas un pictogramme d'objet physique)
- [x] **CCTV : icône caméra + vraie vue "live"** — les caméras individuelles (hors clusters)
      affichent une icône caméra plutôt qu'un cercle. Au tap : pour les rares caméras avec un
      `stream_url` (Quebec 511), lecture vidéo réelle via Media3 ExoPlayer dans un `PlayerView` ;
      pour toutes les autres (TfL, Caltrans, WSDOT…) qui n'exposent qu'un JPEG statique
      régulièrement mis à jour côté source — pas de vrai flux vidéo disponible — le snapshot se
      rafraîchit automatiquement toutes les 3s (URL "cache-bustée" avec un paramètre `?t=`), ce
      qui *est* le mieux qu'on puisse offrir comme "direct" pour ces caméras
- [x] **Vue satellite + style de carte plus détaillé** — bouton calque dans la barre du haut,
      bascule entre le style vectoriel par défaut (passé de MapLibre demo tiles, très sommaire,
      à [OpenFreeMap](https://openfreemap.org) Liberty — gratuit, sans clé, bâtiments/routes/
      occupation du sol) et une vue satellite (Esri World Imagery + calque noms de lieux, la
      même source gratuite et sans clé que Romurbex utilise déjà via osmdroid, ici branchée en
      RasterSource MapLibre). Changer de style recrée le `LayersController` contre le nouveau
      `Style` ; comme tous les `LaunchedEffect` de couches sont indexés dessus, elles se
      redessinent automatiquement sur le nouveau fond de carte sans attendre le prochain poll
- [x] **Fix flux CCTV vide (SkylineWebcams)** — `AsyncImage` avalait silencieusement les échecs
      de chargement ; passage à `SubcomposeAsyncImage` avec des états Loading/Error/Success
      explicites, ce qui a révélé la vraie cause : certaines sources (SkylineWebcams) exposent un
      `feed_url` relatif au proxy du backend (`/api/cctv/proxy?url=...`), pas une URL absolue
      comme TfL/Quebec 511. `CctvViewerDialog` résout désormais `feed_url`/`stream_url` contre
      l'URL du backend configurée (déjà exposée par `MapViewModel.backendUrl`) avant de les
      passer à Coil/ExoPlayer
- [x] **Icône CCTV agrandie** — `iconSize` de la couche caméras passe de `0.45f` à `0.75f`
      (à parité avec les vols) pour mieux ressortir parmi les autres pictogrammes
- [x] **Vue satellite par défaut, bouton de recentrage, icône avion affinée** — `mapStyleMode`
      démarre sur `SATELLITE` au lieu de `STREET` ; un `FloatingActionButton` (bas droite) relance
      `centerOnUserLocation()` à la demande (redemande la permission si besoin) ; l'auto-centrage
      au lancement se rabat sur `getCurrentLocation()` quand `lastLocation` est `null` (fréquent à
      froid — l'app ne se recentrait alors jamais tant qu'aucun fix récent n'existait) ; l'icône
      `ic_plane.xml` reprend le glyphe "flight" de Material Symbols (nez arrondi, empennage
      distinct) plutôt que le losange précédent, un peu plus proche d'un avion réel
- [x] **Clustering vols/satellites tenté puis annulé** — contrairement aux ~17k caméras CCTV, les
      vols et satellites sont bien moins nombreux : le clustering (`GeoJsonOptions.withCluster`,
      rayon 50px/maxZoom 13) les regroupait en une seule grosse bulle dès qu'on dézoomait un peu,
      les rendant méconnaissables (et les satellites, plus denses par endroits, restaient
      difficiles à voir même zoomé). Retour à des sources non clusterisées pour ces deux couches ;
      `ensureClusteredSource`/`ensureClusterCircleLayers`/`clusterExpansionZoom` restent
      disponibles et utilisés uniquement par CCTV, leur cas d'usage d'origine
- [x] **Mouvement animé des vols/navires** — jusqu'ici seules les cyberattaques avaient une
      animation fluide (`ArcMath`) ; vols (poll 60s) et navires (poll 20s) sautaient d'une
      position à l'autre à chaque rafraîchissement. `DeadReckoning.project()` (nouveau, calcul
      plat cap+vitesse en nœuds → déplacement lat/lng) extrapole chaque marqueur depuis sa
      dernière position réelle toutes les secondes tant que la couche est active
      (`startFlightsAnimation`/`startMaritimeAnimation` dans `MapViewModel`, même schéma
      start/stop par toggle que `startCyberAttackPulseAnimation`) ; la position réelle du backend
      reste la source de vérité (`rawFlights`/`rawMaritime`), le ticker ne fait qu'extrapoler
      jusqu'au prochain poll qui recale tout
- [x] **5 nouveaux outils RECON (Pseudo, Fuites de données, GitHub, Téléphone, Adresse MAC)** —
      en vérifiant si le scanner réseau pouvait enfin avoir une vue dédiée (il ne peut pas : sa
      route (`osiris-backend/src/app/api/scanner/route.ts`) ne fait que relayer telle quelle la
      réponse d'un microservice externe privé dont le schéma n'est nulle part dans le code, donc
      le JSON brut reste la seule option honnête), il s'est avéré que le backend expose une
      douzaine de routes `api/osint/*` (bgp, github, hudsonrock, leaks, mac, phone, shodan,
      sweep, threats, username…) dont seules la moitié étaient câblées côté Android. Les 5 au
      schéma confirmé et stable (lu directement dans les `route.ts` correspondants) ont été
      ajoutées avec vue dédiée, sur le même modèle que CVE/DNS/WHOIS : pseudo (énumération façon
      Sherlock sur des dizaines de sites), fuites de données (email → breaches connues via
      XposedOrNot), GitHub (profil + dépôts récents), téléphone (validité, opérateur, type de
      ligne via libphonenumber), adresse MAC (fabricant). bgp/threats/hudsonrock/shodan/sweep
      restent non câblés — schémas soit polymorphes (un champ peut être un nombre OU la chaîne
      "N/A" selon le cas), soit non confirmés faute d'avoir lu tout le code source associé
- [x] **Recherche sur la carte** — icône loupe dans la barre du haut ; recherche en direct (dès 2
      caractères) parmi les vols (callsign), caméras CCTV (nom/ville), satellites, ports et
      navires actuellement chargés en mémoire. Tap sur un résultat → la caméra survole sa position
      (zoom 10) et ouvre la même fiche/dialogue qu'un tap direct sur la carte. La liste est
      mémoïsée (`remember(searchQuery, flights, cctvCameras, satellites, maritime)`) pour ne pas
      refiltrer les ~17k caméras CCTV à chaque tick de l'animation des vols (1×/s)
- [x] **Alertes locales (séismes majeurs, escalade de conflit)** — `AlertNotifier` (nouveau,
      `NotificationCompat`, canal `osiris_alerts` créé au démarrage dans `OsirisApplication`)
      notifie pour tout séisme ≥ M6.0 jamais vu lors d'un poll précédent, et pour toute zone de
      conflit dont la sévérité passe à `high`/`war` alors qu'elle ne l'était pas déjà.
      `MapViewModel.checkEarthquakeAlerts`/`checkConflictAlerts` ignorent volontairement le tout
      premier poll d'une session (c'est l'état courant, pas un lot de nouveaux événements) et ne
      comparent qu'aux polls suivants. Demande la permission `POST_NOTIFICATIONS` au lancement
      sur Android 13+ (obligatoire depuis Tiramisu ; avant, les notifications sont autorisées
      sans prompt)
- [x] **Cadence de polling réglable par couche** — jusqu'ici fixe par `MapLayer.pollIntervalMs`.
      `PollIntervalPreferences` (DataStore, une clé par couche) stocke une surcharge optionnelle ;
      `MapViewModel.startPolling` relit l'intervalle courant à chaque tour de boucle plutôt
      qu'une fois au démarrage, donc un changement dans Réglages > Cadence de polling s'applique
      au prochain cycle sans avoir à désactiver/réactiver la couche. Neuf paliers proposés (15s à
      30 min) via un menu déroulant par couche dans l'écran Réglages
- [x] **Premiers tests unitaires + CI** — le projet n'avait aucun test. Ajout de
      `DeadReckoningTest`/`ArcMathTest` (`app/src/test/`) : ce sont les deux seuls bouts de code
      purement mathématiques du projet (aucune dépendance Android), donc testables par JUnit
      classique sans émulateur ni Robolectric — chaque valeur attendue est calculée à la main
      dans le commentaire du test plutôt que recopiée depuis l'implémentation. Le reste du code
      (ViewModels, repos, Compose) dépend du framework Android ou du réseau et demanderait
      Robolectric/instrumentation — hors scope de cette passe. `.github/workflows/android-ci.yml`
      (nouveau) fait tourner `testDebugUnitTest` puis `assembleDebug` sur chaque push/PR vers
      `main` ; corrigé au passage le bit exécutable de `gradlew` (`git update-index --chmod=+x`,
      manquant dans l'index comme ça l'avait été pour Romurbex — sans ça la CI échoue direct sur
      les runners Linux)

## Licence

MIT, comme le dépôt [Osiris](https://github.com/simplifaisoul/osiris) dont ce client s'inspire.
