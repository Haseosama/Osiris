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
- [x] **Icône avion alignée sur le site web** — vérification faite dans le code source d'Osiris
      (`OsirisMap.tsx`, fonction `createIcon()`) : les avions y sont dessinés sur un `<canvas>`
      avec une silhouette "dard" (nez + ailes, sans queue distincte), `icon-rotate: heading` /
      `icon-rotation-alignment: map` — exactement la même convention que côté Android.
      `ic_plane.xml` reprend ces coordonnées converties une à une vers un viewport 24×24.
      En revanche bateaux/satellites/CCTV/ports sur le site web ne sont que des points colorés
      par type (`ship-dots`, `sat-dots`, `dot-cctv`, pas de pictogramme) — l'appli Android garde
      volontairement ses icônes dédiées pour ces couches, plus lisibles qu'un simple point
- [x] **Icônes avec un peu de relief** — `IconBitmaps.render()` ne se contente plus d'un
      `Drawable.setTint()` plat : ombre portée douce (`BlurMaskFilter` sur un canvas hors-écran,
      donc toujours logiciel — pas de restriction d'accélération matérielle), dégradé lumineux du
      haut vers le bas dérivé de la couleur de catégorie (teinte éclaircie/assombrie via HSV), et
      un fin contour sombre pour se détacher du fond (surtout utile en vue satellite). Le tout
      dérivé d'une seule couleur par appel — aucun asset par icône à refaire. Un changement dans
      cette seule fonction partagée améliore les six pictogrammes (avion, bateau, satellite,
      ancre, caméra, flamme) d'un coup
- [x] **Mémorisation de la dernière vue** — `MapViewPreferences` (DataStore) sauvegarde
      position/zoom/cap/inclinaison de la caméra à chaque arrêt de mouvement
      (`addOnCameraIdleListener`) et le style (rue/satellite) à chaque bascule. Au lancement, la
      vue sauvegardée est restaurée si elle existe ; sinon (premier lancement seulement) l'appli
      se centre sur la position GPS comme avant
- [x] **Interface plus "futuriste"** — typographie basculée sur `FontFamily.Monospace` (police
      système, aucun asset à ajouter) avec un tracking large pour les titres/labels façon HUD, le
      texte de contenu (dialogues, résultats RECON) reste en police par défaut pour rester
      lisible. Les boutons de la barre du haut et le bouton de recentrage deviennent des
      `HudIconButton` (cercle vitre sombre translucide + liseré cyan, plus lumineux quand actif)
      au lieu des `IconButton` Material plats ; les chips de couches et les bannières
      backend/recherche reprennent la même bordure cyan translucide pour un look cohérent
- [ ] **Globe 3D au dézoom — non réalisable actuellement.** Vérifié directement sur le dépôt
      [maplibre-native](https://github.com/maplibre/maplibre-native/issues/3161) (le moteur
      utilisé par le SDK Android/iOS, distinct de MapLibre GL JS qui a bien cette fonctionnalité
      côté web) : la projection globe n'existe pas dans MapLibre Native, personne n'y travaille
      activement, et un mainteneur du projet estime le coût de développement à "au moins 6
      chiffres en dollars". Ce n'est donc pas quelque chose qu'on peut ajouter ici — il faudrait
      soit attendre que MapLibre Native l'implémente en amont, soit rester sur la vue plate
      actuelle (satellite + inclinaison de caméra possible en gesture, mais pas une vraie sphère)
- [x] **Avions militaires : retour au rouge** — repassés du vert (`#4CD97B`, changé plus tôt dans
      la session) au rouge (`#FF5252`) dans `LayersController.FLIGHT_MILITARY`
- [x] **Mouvement animé des satellites** — même traitement que les vols/navires
      (`startSatellitesAnimation` dans `MapViewModel`), mais les satellites n'ont ni cap ni
      vitesse dans l'API : `DeadReckoning.bearing()`/`speedKnots()` (nouveau) déduisent une
      vitesse apparente en comparant chaque satellite (clé = NORAD id) à sa position du poll
      précédent, réutilisable ensuite par `project()` comme pour un vol. Ne bouge qu'à partir du
      **deuxième** poll (la toute première mesure n'a rien à comparer). En vérifiant pourquoi ça
      ne se voyait pas, découvert que le backend renvoie **18 829 satellites** (11k Starlink +
      6,8k débris) — bien trop pour reconstruire la couche carte chaque seconde sur un téléphone.
      `SatellitesRepository` plafonne désormais les catégories `comms`/`other` (triées par NORAD
      id pour rester stables d'un poll à l'autre, condition nécessaire à une animation continue)
      et garde tout le reste (navigation/militaire/science/observation terrestre, ~600 au total).
      Plafond réglé à 3000/catégorie sur demande explicite (~7600 satellites affichés au lieu de
      18 829) après avoir proposé plusieurs paliers avec leur compromis fluidité/densité — à
      revoir si ça rame en pratique sur le téléphone, le plafond est un seul const en haut du
      fichier (`HIGH_VOLUME_CAP`)
- [x] **Plafond satellites désactivé à la demande, tous affichés (~18-19k)** — `CAP_ENABLED =
      false` dans `SatellitesRepository` ; le plafond à 3000/catégorie reste en place, prêt à
      réactiver (un seul booléen) si ça rame. À ce volume, `LayersController.setSatellites`
      reconstruit une source GeoJSON de ~18 829 entités à chaque poll (60s) *et* à chaque tick
      d'animation (1×/s tant que la couche est active) — c'est un vrai risque de saccades/ANR sur
      téléphone, pas juste une hypothèse ; à tester en conditions réelles
- [x] **Plafond satellites réglé à ~6000 au total** — `SatellitesRepository` recalculé en cible
      globale plutôt que par catégorie : les catégories réduites (navigation/militaire/science/
      observation terrestre, ~600) restent intégrales, le reste du budget (~5400) est réparti à
      parts égales entre `comms`/`other`, chacune triée par NORAD id pour rester stable d'un poll
      à l'autre (nécessaire à l'animation)
- [x] **Fiche vol enrichie** — ajout de `airline_code`/`aircraft_category`/`grounded` au modèle
      `Flight` (déjà renvoyés par `/api/flights`, juste jamais lus côté Android) : statut
      au sol/en vol, code compagnie, mention "Hélicoptère" quand pertinent. Altitude et vitesse
      affichent maintenant aussi pieds/km-h à côté de mètres/nœuds
- [x] **Plafond satellites ajusté à 4000** (au total, même logique de répartition que le palier
      6000 précédent)
- [x] **Fiche satellite enrichie : classification d'orbite + période orbitale à la demande** —
      classification LEO/MEO/GEO/HEO calculée côté client depuis l'altitude (aucun appel réseau).
      La période orbitale, elle, n'existe pas dans `/api/satellites` (juste lat/lng/alt) mais
      dans un endpoint dédié `/api/satellites/orbit?id=&t=`, volontairement séparé côté backend
      ("plusieurs Mo pour ~19 000 satellites, un opérateur ne regarde qu'une orbite à la fois").
      `MapViewModel.selectSatellite()` affiche la fiche immédiatement avec ce qu'on a déjà, puis
      la met à jour une fois la période reçue — protégé contre une réponse tardive qui
      écraserait une sélection plus récente (`pendingSatelliteOrbitKey`)
- [x] **Filtre satellites par catégorie, plus de plafond fixe** — `SatellitesRepository` ne
      plafonne plus rien (retour au catalogue complet, ~18-19k) ; à la place, une ligne de chips
      (Comms/Navigation/Observation Terre/Militaire/Science/Autres) apparaît sous les couches
      quand "Satellites" est actif, persistée via `SatelliteCategoryPreferences` (DataStore). Le
      filtre s'applique côté client avant l'animation et le rendu — c'est redevenu le contrôle de
      l'utilisateur sur la densité affichée plutôt qu'un plafond algorithmique fixe côté appli
- [x] **Fiche vol façon FlightRadar24 : trajet réel, aéroport, heures, vitesse verticale** — deux
      nouveaux appels à la demande au tap sur un vol (comme pour l'orbite satellite), en
      parallèle : `/api/flight-route` (aéroport départ/arrivée, distance totale/parcourue/
      restante, heure de départ/arrivée estimée, progression) et `/api/aircraft` (immatriculation,
      modèle complet, compagnie, **trajet réellement volé** lu depuis l'historique adsb.lol —
      pas juste une ligne droite origine→destination). La trajectoire réelle est privilégiée pour
      le tracé sur la carte, avec repli sur l'arc synthétique si l'appareil n'a pas d'historique
      (typique pour le militaire). Vitesse verticale (`vertical_rate_fpm`) ajoutée côté backend
      (déjà dans les données OpenSky brutes, juste jamais exposée) : montée/descente en ft/min.
      Champs demandés mais non disponibles côté sources actuelles : numéro de série (MSN), année
      de fabrication, vitesse air vraie (TAS), vent et température extérieure — aucune des
      sources déjà intégrées (OpenSky, adsb.fi, adsb.lol, adsbdb) ne les fournit ; nécessiterait
      une base immatriculation→MSN/année et un flux météo en altitude, aucun des deux intégré ici
- [x] **Fiches d'info repensées façon FlightRadar24** — `EntityInfoDialog` (partagé par vols,
      séismes, incendies, météo, conflits, maritime, satellites) ne liste plus les faits en texte
      brut "label : valeur" : chaque fiche est maintenant des cartes bordurées, faits groupés deux
      par ligne façon tuiles. `InfoDialogContent` passe d'une simple liste de lignes à des
      `InfoSection` nommées (les vols ont désormais "Vol"/"Position"/"Avion" séparés ; les autres
      types gardent un seul groupe anonyme via `oneSection()`, suffisant vu leur volume de
      données). Les vols reçoivent en plus un bandeau "route" au-dessus des cartes — codes
      aéroport en grand de part et d'autre d'une barre de progression, heures de départ/arrivée
      en dessous — repris de la présentation FlightRadar24 mais avec la palette cyan déjà en
      place plutôt que le vert de leur référence
- [x] **Palette corrigée sur les vraies couleurs du site** — le site (revenu en ligne entre
      temps, `osirisai.live`) inspecté directement via ses variables CSS (`getComputedStyle` sur
      `:root`) plutôt que deviné sur capture d'écran : son accent principal (bordures, boutons
      actifs, titres) est en fait **l'or** (`--gold-primary #d4af37` / `--gold-light #f0d060`),
      le cyan n'y sert qu'aux données en direct (horloge, statut, compteurs). `Color.kt` reprend
      ces valeurs exactes (fonds `--bg-void`/`--bg-secondary`/`--bg-tertiary`, rouge d'alerte
      `--alert-red`, textes `--text-primary`/`--text-secondary`) ; nettoyé au passage les
      constantes `Flight*`/`Severity*` jamais utilisées (`LayersController` a toujours eu ses
      propres constantes séparées). Comme tous les composants de l'appli lisent déjà
      `MaterialTheme.colorScheme.primary` plutôt que des couleurs codées en dur, ce seul fichier
      changé fait basculer boutons/bordures/chips/bannières du cyan vers l'or partout d'un coup.
      Police JetBrains Mono du site non reprise (demanderait de livrer le fichier de police ou
      une dépendance Google Fonts) — le monospace système déjà en place reste un compromis
      raisonnable visuellement proche
- [x] **Fiches d'info colorées par entité + cyberattaques enfin cliquables** — le site étant
      revenu en ligne, lu directement le code des popups qu'il affiche au clic (`OsirisMap.tsx`,
      `SatelliteCard.tsx`) plutôt que deviner. Découverte : chaque fiche y est bordurée dans la
      couleur propre de l'entité (sévérité pour un séisme/une cyberattaque, catégorie pour un
      satellite) plutôt qu'une couleur d'app uniforme — repris via `InfoDialogContent.accentHex`
      et un nouveau fichier `EntityColors.kt` qui devient la source unique des teintes déjà
      utilisées par `LayersController` pour les points sur la carte (fiche et marqueur d'un même
      satellite partagent maintenant exactement la même couleur, au lieu de deux constantes
      dupliquées qui pouvaient diverger). Satellites : ajout du champ Vitesse (dérivé de la
      période orbitale, même calcul que `SatelliteCard.tsx`). **Cyberattaques : jusqu'ici pas
      cliquables du tout côté Android** — découvert en comparant à la vraie interface ; ajout de
      la propriété `idx` sur la couche + une fiche dédiée (sévérité, cible, pays cible, origine)
- [x] **Encore plus d'infos, reprises directement du code source du site** — comparaison
      systématique de chaque type de fiche (avion, séisme, incendie, météo, conflit, port,
      chokepoint, navire, satellite, cyberattaque) contre les vrais gestionnaires de clic
      d'`OsirisMap.tsx` et les routes backend, pour ajouter uniquement des champs réellement
      renseignés (pas des champs qui existent dans le HTML du site mais ne sont jamais remplis
      par notre propre backend auto-hébergé — ex. `Ship.flag` volontairement pas ajouté, jamais
      peuplé côté AIS). Ports : `rank` (rang mondial des ports à conteneurs), `fleet` (flotte
      basée, ports militaires), `dwell_time` (temps d'attente estimé, calculé côté serveur à
      partir de l'AIS en direct) — trois champs réels du backend qui n'étaient pas encore
      affichés. Bouton « ouvrir la source » (façon « OPEN SOURCE ↗ » du site de référence)
      ajouté sur les fiches vol (FlightAware), séisme (USGS), incendie (carte NASA FIRMS), zone
      de conflit (source GDELT), navire (MarineTraffic), satellite (N2YO) et météo (lien
      `source` de la route `/api/weather`, jusqu'ici jamais lu côté Android) — nouveau
      `InfoDialogContent.externalUrl`/`externalUrlLabel`, rendu comme un bouton dans
      `EntityInfoDialog` qui ouvre le navigateur du téléphone. Chokepoint vérifié contre le site
      de référence : aucun champ manquant (nom/trafic/risque déjà complet)
- [x] **Beaucoup plus de CCTV en France, et les caméras "sans flux" réparées** — deux problèmes
      distincts. D'abord `sky-fr-mont-dore` n'avait qu'un lien externe (pas de `feed_url`) :
      trouvé son snapshot réel (`social5344.jpg`) directement dans le JS de la page SkylineWebcams
      correspondante. Ensuite et surtout, les 24 "caméras" APRR/AREA (autoroutes) étaient en fait
      inventées : des coordonnées de péages/villes devinées, sans aucune image ni vidéo, juste un
      lien externe générique vers la carte APRR — la dialog affichait donc "Pas de flux disponible"
      pour chacune. Inspecté le vrai site (`voyage.aprr.fr/carte-itineraires?type=webcam`) au lieu
      de deviner : son appli Angular interne appelle `GET /api/aprr/pois/webcam` (liste des 124
      caméras avec id + coordonnées réelles) puis `GET /api/aprr/pois/popin/webcam/<id>` par caméra
      pour son titre et son vrai lien vidéo — un lien `gieat.viewsurf.com/?id=...&action=mediaRedirect`
      qui redirige (302) vers un clip MP4 court, régulièrement renouvelé (vérifié en direct : vrai
      `video/mp4`, sans referer ni auth). Récupéré les 124, exclu la seule sans vidéo côté site
      (id 3132, "Porte de Bagnolet"), remplacé le bloc `APRR_HIGHWAY` par ces 123 caméras avec un
      vrai `stream_url` (nouveau `stream_type: 'mp4'` dans `CctvStreamType`) — lues par ExoPlayer
      exactement comme les flux Québec 511 déjà existants, aucun changement côté Android nécessaire
- [x] **Flux figé → lien vers le site de la caméra, et boucle locale pour les clips courts** —
      un clip APRR/AREA est court et seulement ré-enregistré côté serveur de temps en temps ; sans
      boucle locale, Media3 le joue une fois puis reste figé sur la dernière image, ce qui se lit
      comme "le flux s'est arrêté". Ajout de `repeatMode = Player.REPEAT_MODE_ONE` sur l'ExoPlayer
      de `CctvViewerDialog` pour que ça continue de bouger entre deux rafraîchissements serveur.
      Ajout aussi d'un nouveau champ `CctvCamera.externalUrl` (`external_url`, déjà présent côté
      backend mais jusqu'ici jamais lu côté Android) : la vidéo/l'image est maintenant cliquable et
      un bouton « voir sur le site de la caméra » apparaît sous le flux quand ce lien existe, pour
      qu'un flux figé ou en échec ne soit jamais une impasse. Complété `external_url` sur les 123
      caméras APRR/AREA (vers leur fiche `voyage.aprr.fr/node/<id>`) — elles n'en avaient pas encore
- [x] **4 webcams Bordeaux (Place de la Bourse, Garonne, Cité du Vin, Pont Chaban-Delmas)** —
      trouvées via bordeaux-tourisme.com, qui embarque des caméras Viewsurf par un lecteur
      "joada.net". Contrairement à l'intégration APRR/AREA (un lien `gieat.viewsurf.com` stable qui
      redirige toujours vers le clip courant), ce lecteur charge sa vidéo par un pipeline
      blob/MediaSource opaque, sans équivalent stable — le seul fichier directement accessible est
      un instantané horodaté (vérifié en direct, mais qui finit par renvoyer 404 une fois la
      rotation Viewsurf passée, en général sous 1-2 jours). Plutôt que de coder en dur une URL dont
      on sait qu'elle va expirer et redevenir "pas de flux disponible", ces 4 caméras n'ont qu'un
      `external_url` vers leur page Viewsurf dédiée — testé (avec `insecam.org`) et exclu comme
      source : c'est un annuaire de caméras privées exposées sans le consentement de leurs
      propriétaires, fondamentalement différent des réseaux officiels déjà utilisés
- [x] **HUD renseignement (façon « œil de Dieu »), en option** — nouveau bouton dans la barre du
      haut qui superpose un habillage type reconnaissance satellite sur la carte : crochets d'angle
      façon viseur, bannière « OSIRIS // LIVE-FEED // NOFORN », identifiants de mission générés une
      fois par session, pastille REC clignotante avec horloge UTC en direct, et les coordonnées/le
      zoom de la caméra actuelle dans les coins bas. Inspiré du HUD de
      [God's Eye View](https://github.com/bilawalsidhu/gods-eye-view) — le fork Android de
      l'utilisateur, `L-oeil-de-Dieu`, en pointait le code source (`src/hud.js`, `style.css`) — mais
      ce projet-là tourne en React/CesiumJS (WebGL, dans un navigateur), une techno totalement
      différente de MapLibre Native ; on ne pouvait pas « prendre sa carte », seulement s'inspirer
      de son style visuel (crochets, glow monospace, bannières). Décision prise avec l'utilisateur
      après clarification : pas de WebView, pas de vrai globe 3D, juste l'habillage sur la carte
      MapLibre plate existante. Tout ce qu'affiche le HUD est réel et dérivé localement (position et
      zoom de la caméra) — pas de conversion MGRS (nécessiterait une lib de géodésie pour une seule
      ligne cosmétique) ni de résumé généré par IA (celui du projet source appelle un endpoint
      OpenAI qui n'existe pas ici)
- [x] **Partage d'une fiche** — nouveau bouton (icône partage, à côté du bouton fermer) sur
      `EntityInfoDialog` qui construit un résumé texte de tout ce que la fiche affiche (titre,
      sections, lien externe si présent) et l'envoie au sélecteur de partage standard d'Android
      (`ACTION_SEND`), plutôt que de dupliquer une UI de partage propriétaire
- [x] **Thèmes du HUD (« reskin reality »)** — le HUD renseignement de tout à l'heure supporte
      maintenant 4 palettes au lieu d'une seule, avec les valeurs exactes du `HUD_COLORS` du
      projet source (`src/hud.js`) : RECON (cyan, par défaut), SURVEILLANCE (vert phosphore),
      THERMAL (blanc), RETRO (ambre). Un petit sélecteur à 4 pastilles apparaît en bas de l'écran
      quand le HUD est actif — la seule zone de tout l'overlay qui capte le tactile, le reste
      laisse passer les gestes vers la carte comme avant. Le libellé « MODE: » du HUD suit le
      thème choisi au lieu d'afficher toujours « RECON »
- [x] **Rejeu (historique de session)** — le backend ne garde aucun historique (chaque poll
      écrase la position précédente), donc pas de vrai "remonter dans le temps" possible sans
      changer le backend. À la place, `MapViewModel` bufferise localement un instantané des
      vols/navires/satellites toutes les 30s (jusqu'à 40 images, donc ~20 minutes glissantes) à
      partir des vraies positions du dernier poll — jamais depuis les positions extrapolées par le
      dead reckoning. Nouveau bouton "historique" dans la barre du haut : bascule le mode rejeu, ce
      qui fige ces trois couches sur l'image bufferisée et met en pause les boucles d'animation le
      temps du rejeu (elles reprennent seules dès la sortie). Une barre en bas de carte affiche
      l'heure de l'image courante, un curseur pour naviguer manuellement dans le buffer, et un
      bouton lecture/pause qui avance automatiquement d'une image toutes les 800ms jusqu'à la plus
      récente
- [x] **Widget écran d'accueil** — nouveau module `widget/` (Jetpack Glance, pas du
      RemoteViews/XML classique, pour rester cohérent avec le reste de l'UI Compose) : une carte
      glanceable avec le nombre de vols/cyberattaques/zones de conflit (sévérité haute/guerre)
      actuellement suivis, plus l'heure de la dernière mise à jour. `MapViewModel` pousse ces
      compteurs dans l'état propre à chaque widget posé (le mécanisme de state intégré de Glance,
      pas un DataStore séparé) à chaque poll des vols/cyberattaques/conflits. Le widget lui-même
      n'appelle jamais le backend — il ne fait qu'afficher ce qu'on lui a poussé en dernier, donc
      les chiffres ne bougent que tant que l'appli est ouverte et sonde ; un vrai rafraîchissement
      en arrière-plan demanderait un Worker dupliquant la logique de polling, hors scope pour une
      carte de statut. Tap sur le widget → ouvre l'appli
- [x] **Bouton actualiser sur le widget** — le widget ne bougeait qu'en gardant l'appli ouverte ;
      ajout d'un bouton ↻ qui lance `RefreshWidgetAction` (Glance `ActionCallback`), un aller-retour
      direct vers les repositories vols/cyberattaques/conflits sans passer par `MapViewModel` —
      fonctionne donc même appli fermée. Chaque compteur échoue indépendamment (`runCatching`) :
      si un seul fetch rate, les deux autres se mettent quand même à jour, et celui en échec garde
      sa dernière valeur connue plutôt que de retomber à 0
- [x] **Trafic routier (nouvelle couche)** — aucune source de trafic (fermetures, travaux,
      bouchons, accidents) n'existait dans le backend, seulement des caméras. Intégré l'API
      TomTom Traffic Incident Details (clé gratuite fournie par l'utilisateur, stockée
      uniquement côté serveur dans `osiris-backend/.env`, jamais envoyée au client). Contrainte
      découverte en testant en direct : TomTom rejette toute `bbox` de plus de 10 000 km²
      (la France entière ~550 000 km² part donc en erreur 400) — vu le quota gratuit journalier,
      couvrir tout le pays en tuilant reviendrait à 130+ requêtes par sondage. La nouvelle route
      `/api/traffic` interroge donc 8 pôles métro/autoroutiers (Paris, Lyon, Marseille, Toulouse,
      Bordeaux, Nantes, Lille, Strasbourg — les mêmes zones déjà couvertes par CCTV/APRR) en
      parallèle et fusionne le résultat ; un `?bbox=` explicite reste possible pour une zone sur
      mesure. Nouvelle couche Android `TRAFFIC` (sondage 10 min, quota oblige), points colorés
      par ampleur (vert=mineure → rouge=fermeture, échelle TomTom native), fiche au clic avec lien
      Google Maps vers l'incident
- [x] **Le vrai tronçon de route dessiné, pas juste un point** — `TomTom` renvoie une géométrie
      (`LineString`) par incident, jusque-là récupérée côté backend mais jamais exploitée côté
      carte (seul le point milieu servait à placer le marqueur). Ajout du champ `geometry` au
      modèle Android + une seconde couche `LineLayer` (`traffic-lines-layer`, sous les points
      pour ne pas cacher la cible de tap) qui dessine le segment réel, coloré par la même échelle
      de gravité que les points. Le point au milieu reste la seule cible tactile (la ligne ne
      porte pas la propriété `idx`) ; un incident sans géométrie exploitable garde son point sans
      ligne plutôt que de disparaître
- [x] **Correction : incidents TomTom en géométrie `Point`** — découvert en essayant de montrer un
      accident réel sur le terrain : la couche Trafic n'affichait strictement aucun marqueur
      malgré des milliers d'incidents valides côté backend. Cause : `geometry.coordinates` n'est
      pas toujours une `LineString` (tableau de paires imbriquées) — TomTom renvoie parfois un
      `Point` (une paire `[lng, lat]` à plat) pour certains accidents/véhicules en panne. Le calcul
      du point milieu indexait alors ce tableau plat comme s'il était imbriqué, produisant un
      `lat`/`lng` `undefined` côté JSON (silencieusement omis par `JSON.stringify`) — et comme ces
      deux champs sont non-nullables côté modèle Kotlin, un seul incident malformé faisait échouer
      le décodage de la liste entière. `/api/traffic` normalise maintenant les deux formes de
      géométrie avant de calculer le milieu
- [x] **Widget : plus de données, plus soigné** — ajout de deux lignes (séismes M4.5+, trafic
      perturbé ampleur ≥ modérée) aux trois déjà affichées. Refonte visuelle : fin liseré doré
      autour de la carte (un `Box` doré contenant un `Box` inséré de 1,5dp peint dans le fond réel
      — Glance n'a pas de modificateur de bordure natif), sous-titre « Dashboard OSINT », icône par
      catégorie, et valeurs grisées au repos qui s'allument dans la couleur de la ligne dès qu'il y
      a quelque chose d'actif à voir
- [x] **Icône de l'application** — remplace l'icône adaptative générique (anneaux radar
      vectoriels) par l'œil d'Horus doré illustré (avion/incendie/navire intégrés au dessin). Le
      contenu est inséré à 66% du canevas adaptatif pour ne jamais être rogné par le masque du
      launcher (cercle, carré arrondi...), avec une couleur de fond `ic_launcher_background`
      échantillonnée directement sur le coin de l'illustration pour que la marge transparente se
      fonde dedans. Un petit rappel de cette icône apparaît aussi à côté du titre « OSIRIS » en
      haut à gauche de l'appli
- [x] **Vers zéro backend, phase 1** — chantier en plusieurs phases pour que l'appli n'ait plus
      besoin du tout d'un backend auto-hébergé (voir le plan de migration). Cette première phase
      porte les couches sans clé et à logique de fusion minimale directement en Kotlin, chacune
      appelant sa source amont depuis le téléphone au lieu de passer par `/api/*` : Séismes
      (USGS), Incendies (CSV NASA FIRMS + volcans EONET), Cyberattaques (blocklist Feodo Tracker
      abuse.ch), Actu en direct (liste statique, aucune requête réseau côté backend de toute
      façon), et le détail d'un vol au tap (traces adsb.lol + identité adsbdb, avec la même table
      de ~330 aéroports et la même détection de tronçon en cours que le backend). Sept outils
      RECON suivent le même chemin : DNS, Certificats SSL, CVE, Fuites de données, GitHub,
      Adresse MAC, et Téléphone (ce dernier 100% local via `libphonenumber`, aucune requête).
      Chaque couche/outil migré fonctionne désormais même sans backend configuré du tout — le
      reste (vols en direct, trafic, maritime, CCTV, OSINT Telegram, scanner de ports...) continue
      de passer par le backend en attendant les phases suivantes
- [x] **Vers zéro backend, phase 2** — Météo sévère (fusion NASA EONET + NOAA/NWS + GDACS,
      parsing XML par regex pour GDACS qui n'a pas d'API JSON), Zones de conflit (mêmes flux RSS
      BBC/Al Jazeera/NYT que le backend, appariés aux mêmes 15 zones fixes par mots-clés), et la
      route de vol détaillée au tap (course adsbdb/hexdb/airplanes.live, plausibilité
      géographique vérifiée comme côté backend). Trois outils RECON de plus : WHOIS (RDAP +
      empreinte des en-têtes HTTP), IP Intelligence (ip-api.com), Sanctions OFAC — ces trois
      partagent un module `SanctionsIndex` qui télécharge et indexe le CSV OpenSanctions
      (~7 Mo, dizaines de milliers d'entrées) une fois, en cache 24h, port direct de
      `sanctions.ts` du backend. Dix couches/outils natifs au total maintenant ; il ne reste que
      vols en direct, trafic, maritime, satellites, CCTV, OSINT Telegram, scanner de ports, et
      wallet crypto/pseudo côté RECON derrière le backend
- [x] **Vers zéro backend, phase 3 (4/4 — terminée)** — les quatre couches à clé :
      **Trafic routier** (TomTom, clé lue depuis `BuildConfig`), **Maritime** (connexion
      WebSocket directe à aisstream.io depuis le téléphone via OkHttp — même principe de cache
      de navires en mémoire tenu tout le cycle de vie du process que le backend, juste avec
      l'appli elle-même comme seul "client"), **Vols en direct** (OpenSky authentifié quand les
      clés sont configurées, repli anonyme sinon, secours adsb.fi régional en dernier recours —
      moteur de classification commercial/privé/jet/militaire porté à l'identique), et
      **Satellites** (CelesTrak, ~40 groupes interrogés en parallèle pour éviter le rate-limit
      d'une requête unique + repli SatNOGS, propagation orbitale SGP4/SDP4 on-device via
      `uk.me.g4dpz:predict4java` — port Java du modèle NORAD, MIT, disponible sur Maven Central
      contrairement à d'autres portages qui n'existent que sur JitPack sans version figée).
      Les clés OpenSky/AIS/TomTom viennent de `local.properties` → `BuildConfig`. Contrairement
      au backend, pas de cache TLE sur disque : le process Android est de toute façon tué bien
      plus souvent par l'OS qu'un serveur qui tourne des jours d'affilée, donc la persistance
      disque n'apportait pas grand-chose ici — juste un nouveau fetch CelesTrak au démarrage
      à froid de l'appli. Avec cette phase, toutes les couches carte et la quasi-totalité des
      outils RECON tournent maintenant sans backend
- [x] **Vers zéro backend, phase 4** — les deux derniers outils RECON à logique lourde :
      **Wallet crypto** (port de `chainIntel.ts` — détection BTC/ETH/SOL par regex d'adresse,
      collecte directe mempool.space/Blockscout v2/RPC JSON Solana public, prix CoinGecko en
      cache, recoupement OFAC via `SanctionsIndex`, même moteur de score de risque pondéré
      qu'avant), et **Pseudo/Sherlock** (port de `sherlock.ts` — base de règles du Sherlock
      Project, ~70 sites de la palette prioritaire interrogés en parallèle borné à 12,
      calibration anti-faux-positif par deux pseudos de contrôle aléatoires). Douze
      couches/outils natifs au total ; ne restent derrière le backend que le fil OSINT
      Telegram, le CCTV, et le scanner de ports (phase 5)
- [x] **Vers zéro backend, phase 5.1 — scanner réseau** — `/api/scanner` s'est avéré être un
      simple proxy vers un microservice externe (`SCANNER_URL`/`SCANNER_KEY`) dont le code
      n'est dans aucun des deux dépôts : impossible de porter ses 9 types de scan ligne à
      ligne. Reconstruit nativement les sept qui correspondent à des techniques standard bien
      comprises, en réutilisant les sources déjà écrites pour d'autres outils RECON quand les
      mêmes données s'appliquent : `whois` (RDAP), `ssl`/`subdomains` (crt.sh, qui renvoie déjà
      les deux), `geoloc` (ip-api.com résout aussi un nom de domaine côté serveur), `rdns`
      (DNS-over-HTTPS Google, PTR), `headers` (même sonde HTTP HEAD que WHOIS), et `quick` (scan
      de connexion TCP — pas SYN — borné à une liste fixe de ports courants, pas une plage
      arbitraire, exécuté depuis le téléphone avec un avertissement explicite dans le résultat).
      `tech` et `vuln` sont retirés du menu : aucune façon honnête de les approcher sans le code
      du microservice d'origine. Treize couches/outils natifs au total ; ne restent derrière le
      backend que le fil OSINT Telegram et le CCTV
- [x] **Vers zéro backend, phase 5.2 — fil OSINT Telegram** — malgré son nom, `/api/news` n'a
      rien à voir avec la liste statique de streams YouTube de la phase 1 : c'est le fil OSINT
      géoparsé qui scrape `t.me/s/{channel}` (OSINTtechnical, Faytuks, Liveuamap, CyberKnow) en
      HTML, avec repli sur quelques flux RSS (BBC/Al Jazeera/GDACS) seulement si Telegram bloque
      la connexion. Port fidèle des mêmes heuristiques que le backend : score de risque par
      mots-clés, coordonnées par une petite table de lieux nommés (pas du vrai géoparsing), id
      MD5 de lien+date. Scrape HTML par regex fragile par nature (aucune API publique pour ça),
      un compromis déjà accepté côté backend et qui casse pareil à chaque changement de balisage
      Telegram. Quatorze couches/outils natifs au total ; ne reste derrière le backend que le
      CCTV

## Licence

MIT, comme le dépôt [Osiris](https://github.com/simplifaisoul/osiris) dont ce client s'inspire.
