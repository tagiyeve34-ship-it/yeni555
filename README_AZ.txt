AİLƏ NƏZARƏTİ PANEL V3 — TƏMİZ YENİ QURULUŞ

Bu layihənin UI hissəsi sıfırdan yığılıb. Köhnə xəritə və zəng ekranlarının layout/Kotlin faylları istifadə olunmur.

XƏRİTƏ
- İlk açılışda yalnız son mövqe göstərilir.
- “Keçmişə bax” ayrıca kompakt panel açır.
- Tarix+saat birbaşa sahəyə dd.MM.yyyy HH:mm formatında yazılır; böyük Android calendar popup yoxdur.
- Nöqtələr və marşrut xətti ayrıca ON/OFF edilir.
- İstiqamət oxları yalnız az sayda, marşrut boyunca göstərilir.
- GPS sıçrayışları (çox zəif accuracy / qeyri-real sürət) filtr olunur.
- Markerə toxunanda panel daxilində məlumat görünür; Google Maps yalnız ayrıca düymə ilə açılır.
- Google API key və billing yoxdur.

ZƏNGLƏR
- Əsas ekranda filtr forması yoxdur; nömrələr dərhal görünür.
- Eyni nömrələr bir kartda qruplaşdırılır.
- Filtr yalnız “Filtr” düyməsi ilə açılır və tətbiqdən sonra bağlanır.
- 100 / 250 / 500 / Hamısı dəstəyi.
- Nömrəyə toxunanda kopyalanır.
- Karta toxunanda həmin nömrənin bütün zəng tarixçəsi və statistikası açılır.

SERVER
- Mövcud yenilənmiş api/calls.php və api/locations.php istifadə olunur.
- SQL import lazım deyil.

GITHUB
- .github/workflows/build-apk.yml hazırdır.
- GitHub Actions-da Build Android APK çalışdırın.
