# MIYAV Android — İstehsala (Production) Hazırlıq Bələdçisi

Tarix: 8 noyabr 2026. Tətbiq: `az.miyav.native`, versiya `1.0.0` (versionCode `1`).

Bu sənəd Android tətbiqini quraşdırmaq, konfiqurasiya etmək, test etmək, imzalamaq və Google Play-də dərc etmək üçün lazım olan hər şeyi izah edir. Sonda real vəziyyət və qalan xarici addımlar dəqiq göstərilib.

---

## 1. Layihə arxitekturası

Tətbiq çoxmodullu Gradle layihəsinin bir hissəsidir. Android yalnız `android-app` modulunda yaşayır, biznes qaydaları isə paylaşılan Java modullarındadır (desktop tətbiqi ilə eyni):

```text
common  ←  domain  ←  (application, data, security)  ←  android-app
```

| Modul | Rolu |
| --- | --- |
| `shared/common` | Xəta kodları, dil kataloqu, saat |
| `shared/domain` | `User`, `Pet`, `AdoptionApplication`, `AuthSession`, validatorlar, repository interfeysləri |
| `shared/application` | `AuthService`, `UserService`, `PetService`, `AdoptionService`, `NotificationService`, `LocationService` |
| `shared/security` | `SessionStore`, `RoleGuard`, sessiya kodlaşdırması |
| `shared/data` | Supabase REST/Auth/Storage adapterləri (OkHttp + Gson) |
| `android-app` | Ekranlar, naviqasiya, Android Keystore sessiyası, Room outbox, WorkManager |

Android tərkibi (`MiyavApplication`) bütün servisləri bir yerdə qurur. Ekranlar biznes qaydası saxlamır; hər şey servislər vasitəsilə gedir. Avtorizasiyanın əsl nəzarətçisi Supabase RLS (Row Level Security) siyasətləridir, tətbiqdəki yoxlamalar yalnız istifadəçi təcrübəsi üçündür.

### Ekranlar

| Ekran | Funksiya |
| --- | --- |
| Splash | Saxlanılmış sessiyanı bərpa edir, lazım olsa tokeni yeniləyir |
| Giriş / Qeydiyyat | E-poçt + şifrə ilə giriş, qeydiyyat, şifrə sıfırlama sorğusu, dil seçimi |
| Kəşf | Dərc olunmuş elanlar: axtarış, şəhər və növ filtri, səhifələmə, “Yaxınlıqdakı elanlar” (25 km, məsafəyə görə sıralanır, kartda məsafə nişanı), skelet yüklənmə |
| Elanlarım | Öz elanlarınız (qaralama, dərc, övladlığa verilib, arxiv) |
| Elan redaktoru | Heyvanın növü (İt, Pişik…), Cinsi (məs. Golden Retriever), Cinsiyyəti (Erkək/Dişi düymələri), yaş, peyvənd, şəhər, təsvir, şəkillər, **elanın məkanı** (xəritədə seçim) |
| Məkan seçimi | İcazə izahı → cari məkan → xəritədə sancağı sürüşdürüb dəqiqləşdirmə → rayon/şəhər adı (Geocoder) |
| Elan detalı | Qalereya, növ/cins/cinsiyyət/yaş/peyvənd/status çipləri, təsvir, məkan (~150 m dairə, dəqiq nöqtə yox), “Xəritədə aç”, paylaş, **elan sahibinin kartı** (avatar, ad, təsdiq nişanı, qısa məlumat, WhatsApp, zəng), sahibin digər elanları, müraciət |
| İctimai profil | Elan sahibinin və ya gəzintidəki istifadəçinin ictimai profili və elanları |
| **Gəzinti (Beta)** | Alt menyunun mərkəzindəki böyük düymə. Xəritədə yaxınlıqda gəzən istifadəçilər, gəzintiyə başla / fasilə / bitir, müddət, gəzən haqqında önizləmə (profil, WhatsApp) |
| Müraciətlər | Göndərdiklərim / Gələnlər; qəbul, rədd, ləğv |
| Profil | Ad, şəhər, rayon, telefon, **haqqında (bio)**, **“Nömrəmi göstər” razılığı**, avatar, dil, bildirişlər, şifrə dəyişmə, çıxış |
| Bildirişlər | Supabase `notifications` cədvəli; oxundu kimi işarələmə |
| Şifrə | Profil daxilində dəyişmə və ya e-poçtdakı bərpa keçidindən sonra yeni şifrə |

---

## 2. Texnologiyalar və asılılıqlar

| Sahə | Versiya |
| --- | --- |
| Java | Bytecode 17 (JDK 17 və ya 21 ilə yığılır) |
| Gradle | 8.11.1 (wrapper) |
| Android Gradle Plugin | 8.7.3 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 (Android 8.0+) |
| UI | Material Components 1.12.0 (Material 3 Light, isti pastel dizayn sistemi), AppCompat 1.7.0, RecyclerView, SwipeRefreshLayout |
| Xəritə | osmdroid 6.1.20 (OpenStreetMap plitələri, API açarı tələb etmir) |
| Naviqasiya | Navigation 2.8.5 |
| Yerli verilənlər | Room 2.6.1 (outbox), WorkManager 2.10.0 |
| Şəbəkə | OkHttp 4.12.0, Gson 2.11.0 |
| Şəkil | ExifInterface 1.3.7 (şəklin istiqaməti) |
| Test | JUnit 4.13.2 (Android), JUnit 5 + Mockito (paylaşılan modullar) |
| Backend | Supabase (Auth, PostgREST, Storage), layihə `<project-ref>` |

Google Maps istifadə **olunmur**; xəritələr osmdroid + OpenStreetMap ilə göstərilir (10-cu bölmə). Məkan məlumatının necə saxlandığı və qorunduğu 2.2-də izah olunub.

### 2.1 Dizayn sistemi

Bütün rənglər, ölçülər və komponent stilləri mərkəzləşdirilib; ekranlarda təsadüfi rəng yoxdur.

| Fayl | Məzmun |
| --- | --- |
| `res/values/colors.xml` | Brend: mərcan `brand_coral #FF6B4A`, günəş `brand_sun #FFB938`, nanə, lavanda, səma; pastel fonlar; krem fon `#FFF9F5`, ağ səthlər; mətn, xətt, xəta/uğur tokenləri |
| `res/values/dimens.xml` | Boşluqlar `space_xs…xxxl`, radiuslar `radius_sm…xl`, kölgələr, düymə hündürlüyü, avatar/kart/qalereya/xəritə ölçüləri, Gəzinti düyməsi `66dp` |
| `res/values/themes.xml` | `Theme.MIYAV` (Material3 Light), mətn stilləri (Display/Title/Headline/Body/Caption/Section/NavLabel/Badge), `Widget.Miyav.*` — Button (Primary/Tonal/WhatsApp/Outlined/Segment/Text/Danger), TextInput, Card, Chip, BottomNavigation, Toolbar, BottomSheet |
| `res/color/*.xml` | Vəziyyət seçiciləri: naviqasiya, seqment düymələri, input xətti, çip |
| `ui/widget/ShimmerLayout` | Skelet yüklənmə üçün yüngül parıltı effekti (kəşf kartları, elan detalı, sahib kartı, yaxınlıqdakı gəzənlər) |

Alt naviqasiya: Kəşf · Elanlarım · **Gəzinti** · Müraciətlər · Profil. Gəzinti düyməsi mərkəzdə, digərlərindən böyük, mərcan→günəş qradiyentli, ağ haşiyəli, yuxarı qaldırılmış və “BETA” nişanlıdır.

### 2.2 Məkan və məxfilik modeli

| Məlumat | Harada | Dəqiqlik | Kim görür |
| --- | --- | --- | --- |
| Elanın məkanı | `adoption_listings.latitude/longitude` `numeric(6,3)` + `location_name` | **~100 m** (verilənlər bazası 3 onluq rəqəmdən artığını saxlaya bilmir) | Dərc olunmuş elanı görən hər kəs; detalda nöqtə yox, ~150 m dairə göstərilir |
| Gəzinti zamanı canlı məkan | `private.walk_locations` (Data API-yə açıq deyil, birbaşa giriş yoxdur) | Serverdə 5 onluq | Heç kim birbaşa oxuya bilmir |
| Digər gəzənlərə verilən məkan | `nearby_walkers()` RPC | 3 onluğa yuvarlaqlaşdırılır (~100 m), məsafə 100 m-ə yuvarlaqlaşdırılır | Yalnız **özü də hazırda aktiv gəzən** və 3 km radiusda olan istifadəçilər |
| Telefon nömrəsi | `profiles.phone` | — | Yalnız istifadəçi “Nömrəmi göstər” seçibsə, `public_profile` / `nearby_walkers` RPC-ləri ilə |

Qaydalar:

- Fasilə və ya bitirmə anında canlı məkan sətri serverdən **silinir**. 10 dəqiqə yeniləmə gəlməsə (fasilədə 30 dəqiqə) sessiya avtomatik bitir və məkan silinir (`expire_walks()` hər gəzinti RPC-sində çağırılır, pg_cron tələb etmir).
- Gəzməyən istifadəçi heç kimi görmür və heç kimə görünmür (“görmək üçün paylaş” qarşılıqlılığı).
- Koordinatlar yalnız Azərbaycan ərazisində (en 38–42°, uzunluq 44–51°) qəbul edilir; həm tətbiqdə, həm verilənlər bazasında yoxlanılır.
- Məkan icazəsi rədd edilsə, tətbiq heç vaxt saxta koordinat yaratmır; istifadəçiyə izah göstərilir və xəritə yalnız kamera mövqeyi kimi Bakını göstərir (heç yerə yazılmır).
- Supabase Realtime **istifadə olunmur**: `postgres_changes` dəqiq sətirləri abunəçilərə ötürərdi. Əvəzində tətbiq gəzinti zamanı 15 saniyədən bir `nearby_walkers()` sorğusu göndərir.
- Batareya: gəzinti zamanı `location` tipli foreground service 20 saniyə / 20 metr intervalla yeniləmə alır, server ürək döyüntüsü 60 saniyədir; gəzinti bitəndə servis dərhal dayanır. Fon məkanı (`ACCESS_BACKGROUND_LOCATION`) **istənmir**.

---

## 3. İlkin tələblərin quraşdırılması

1. **JDK 17 və ya 21** (məs. Temurin). `java -version` ilə yoxlayın.
2. **Android Studio** (Ladybug və ya daha yeni).
3. Android Studio → *SDK Manager*:
   - Android SDK Platform **35**
   - Android SDK Build-Tools (34+)
   - Android SDK Platform-Tools
   - Emulator (test üçün)
4. Git.

---

## 4. Android Studio konfiqurasiyası

1. *File → Open* → repozitoriyanın kök qovluğunu (`Heyvan`) açın (yalnız `android-app` yox).
2. *Settings → Build Tools → Gradle → Gradle JDK*: JDK 17 və ya 21 seçin.
3. Gradle sync bitəndən sonra *Run configuration* olaraq `android-app` seçin.
4. Emulator: *Device Manager* → Pixel (API 34–36) yaradın.

---

## 5. Gradle konfiqurasiyası

Konfiqurasiya `android-app/build.gradle`-dadır:

- `versionCode 1`, `versionName "1.0.0"` — hər Play yükləməsində `versionCode` artırılmalıdır.
- Release: `minifyEnabled true` (R8), `shrinkResources true`, qaydalar `android-app/proguard-rules.pro`.
- Dil splitləri söndürülüb (`bundle.language.enableSplit = false`), çünki tətbiq daxilində dil dəyişdirilir. AAB-də yoxlanılıb: `splitDimension LANGUAGE negate: true`.
- Supabase dəyərləri `local.properties`-dən, olmadıqda mühit dəyişənlərindən oxunur və `BuildConfig`-ə yazılır.
- Qorunma: `sb_secret_` açarı aşkarlanarsa yığım dayanır; release yığımı Supabase dəyərləri olmadan başlamır.
- İmzalama: `keystore.properties` və ya `MIYAV_UPLOAD_*` mühit dəyişənləri varsa release avtomatik imzalanır, yoxdursa imzasız qalır.

---

## 6. Supabase konfiqurasiyası

Tətbiq mövcud Supabase layihəsindən istifadə edir. Yeni layihə yaratmağa ehtiyac yoxdur. Cədvəllər, RLS və RPC-lər artıq tətbiq olunub (miqrasiyalar `supabase/migrations` və Expo layihəsində).

### 6.1 Konfiqurasiya faylı

`local.properties.example` faylını `local.properties` adı ilə kopyalayın (bu fayl `.gitignore`-dadır):

```properties
sdk.dir=C:\\Users\\<siz>\\AppData\\Local\\Android\\Sdk
supabase.url=https://<project-ref>.supabase.co
supabase.publishableKey=sb_publishable_...
```

Bu maşında `local.properties` artıq doldurulub.

### 6.2 Auth URL konfiqurasiyası (MÜTLƏQ)

Supabase Dashboard → **Authentication → URL Configuration → Redirect URLs** bölməsinə əlavə edin:

```text
az.miyav.native://auth
```

Bu olmadan təsdiq və şifrə bərpa məktublarındakı keçid tətbiqi açmayacaq (Supabase onu Site URL-ə yönləndirəcək). Bu addım dashboard girişi tələb edir və avtomatik edilməyib.

### 6.3 SMTP (MÜTLƏQ, istehsal üçün)

Supabase-in daxili e-poçt göndəricisi saatda çox az məktuba icazə verir və yalnız sınaq üçündür. Testdə şifrə bərpa sorğusu bu limitə düşdü (`auth.rate_limited`). İstehsal üçün: **Authentication → Emails → SMTP Settings** bölməsində öz SMTP provayderinizi (Resend, SendGrid, Mailgun, Amazon SES və s.) qoşun və göndərici ünvanı təsdiqləyin.

### 6.4 Məlumat təhlükəsizliyi (yoxlanılıb)

- Bütün `public` cədvəllərdə RLS aktivdir.
- Klient `profiles.role` və `profiles.suspended` sütunlarını dəyişə bilməz (yalnız `service_role`).
- Elan yazıları yalnız sahibə icazəlidir; başqa istifadəçinin dəyişiklik cəhdi canlı testdə rədd edildi.
- Müraciət statusu yalnız `accept/reject/cancel_adoption_application` RPC-ləri ilə dəyişir; eyni elana ikinci gözləyən müraciət və öz elanına müraciət verilənlər bazası səviyyəsində bloklanır.
- Şəkillər `adoptions` bucket-indədir (özəl, 2 MB, yol `{owner}/{listing}/{uuid}.jpg`); avatarlar `avatars` bucket-indədir.

Yeni miqrasiya lazım olmadı. RLS heç yerdə söndürülməyib.

---

## 7. Publishable key haradan alınır

Supabase Dashboard → layihə → **Project Settings → API Keys** → **Publishable key** (`sb_publishable_...` ilə başlayır). Bu açar ictimaidir və tətbiqə daxil edilə bilər, çünki bütün giriş RLS ilə qorunur.

**Heç vaxt** `sb_secret_...` açarını və ya `service_role` JWT-ni Android-ə qoymayın. Yığım bunu aşkarlayıb dayandırır, `SupabaseConfig` də işləmə zamanı rədd edir.

## 8. Layihə URL-i haraya yazılır

`local.properties` → `supabase.url=https://<project-ref>.supabase.co`. CI üçün: `MIYAV_SUPABASE_URL` mühit dəyişəni (`.env.example`-ə baxın). URL Dashboard → **Project Settings → Data API**-də görünür.

## 9. Digər API açarları

Başqa açar tələb olunmur. Telegram, push bildirişləri və Google Maps bu buraxılışa daxil deyil.

## 10. Google Maps

İstifadə olunmur, buna görə Maps API açarı lazım deyil. Gələcəkdə xəritə əlavə edilsə: Google Cloud Console → Maps SDK for Android → açarı paket adı (`az.miyav.native`) və SHA-1 imza barmaq izi ilə məhdudlaşdırın, `local.properties`-də saxlayın.

---

## 11. Test hesabı

İki xüsusi QA hesabı Supabase Auth-un açıq qeydiyyat API-si ilə yaradılıb (real istifadəçi hesabı deyil, layihədə bundan əvvəl istifadəçi yox idi):

| Rol | E-poçt |
| --- | --- |
| Elan sahibi | `<qa-owner-email>` |
| Müraciət edən | `<qa-applicant-email>` |

- Şifrələr təsadüfi yaradılıb və repozitoriyada **saxlanılmır**. Onlar bu kompüterdə `%USERPROFILE%\.miyav-qa\accounts.txt` faylındadır.
- Layihədə e-poçt təsdiqi tələb olunur. Testi davam etdirmək üçün **yalnız bu iki hesab** SQL ilə təsdiqlənmiş kimi işarələndi (`auth.users.email_confirmed_at`). Təsdiq məktubları `<owner-mailbox>` qutusuna da göndərilib.
- Hər iki hesabın `profiles` sətri qeydiyyat trigger-i ilə avtomatik yaranıb (`role = owner`, `suspended = false`).
- Plus-ünvanlar (`+qa-...`) sizin `<owner-mailbox>` qutunuza gəlir, ona görə bərpa məktubları da sizə çatır.

### Hesab ilə əl ilə yoxlama

1. Tətbiqi açın → *Daxil ol* → QA e-poçt və şifrəni yazın.
2. Kəşf açılır. Tətbiqi tam bağlayıb yenidən açın: sessiya bərpa olunur (giriş ekranı çıxmır).
3. *Profil → Çıxış*: giriş ekranına qayıdır, yenidən açanda sessiya yoxdur.
4. *Şifrəni unutmusunuz?*: məktub gəlir (SMTP limiti aşılmayıbsa). Keçidi telefonda açın → “Yeni şifrə təyin edin” ekranı (6.2 addımı tamamlanmalıdır).

### Yeni test hesabı yaratmaq

Tətbiqdə *Qeydiyyat* → ad, e-poçt, şifrə (10–72 simvol, hərf və rəqəm). Təsdiq məktubundakı keçidi telefonda açın. Admin hüququ lazımdırsa, yalnız SQL Editor-da `service_role` ilə: `update public.profiles set role = 'admin' where id = '<uuid>';`

---

## 12. Tətbiqi işə salmaq

- Android Studio: `android-app` → ▶ Run.
- Terminal:

```bat
gradlew.bat :android-app:installDebug
```

## 13. Debug APK

```bat
gradlew.bat :android-app:assembleDebug
```

Nəticə: `android-app\build\outputs\apk\debug\android-app-debug.apk` (debug açarı ilə imzalanıb).

## 14. Release APK

```bat
gradlew.bat :android-app:assembleRelease
```

- `keystore.properties` varsa: `android-app\build\outputs\apk\release\android-app-release.apk` (imzalı).
- Yoxdursa: `android-app-release-unsigned.apk` (R8 ilə kiçildilmiş, imzasız, quraşdırıla bilməz).

## 15. AAB (Google Play üçün)

```bat
gradlew.bat :android-app:bundleRelease
```

Nəticə: `android-app\build\outputs\bundle\release\android-app-release.aab`. Play Console yalnız upload açarı ilə imzalanmış AAB qəbul edir (16-cı bölmə).

## 16. İmzalama

Saxta imza məlumatı yaradılmayıb. Upload açarını özünüz yaradın:

```bat
mkdir ..\miyav-keys
keytool -genkeypair -v -keystore ..\miyav-keys\miyav-upload.jks -alias miyav-upload -keyalg RSA -keysize 4096 -validity 10000
```

Sonra `keystore.properties.example` → `keystore.properties`:

```properties
storeFile=../miyav-keys/miyav-upload.jks
storePassword=...
keyAlias=miyav-upload
keyPassword=...
```

Qaydalar:

- `.jks` faylını və şifrələri **git-ə qoymayın** (`.gitignore` bunu bloklayır). Repozitoriyadan kənarda, şifrələnmiş ehtiyat nüsxə ilə (məs. parol menecerində və ayrıca oflayn disk) saxlayın.
- Play Console-da **Play App Signing** aktiv edin: Google tətbiq açarını saxlayır, siz yalnız upload açarını. Upload açarı itərsə, Play dəstəyi vasitəsilə sıfırlana bilər.
- CI üçün `MIYAV_UPLOAD_STORE_FILE`, `MIYAV_UPLOAD_STORE_PASSWORD`, `MIYAV_UPLOAD_KEY_ALIAS`, `MIYAV_UPLOAD_KEY_PASSWORD` dəyişənlərini CI-nin gizli saxlancından ötürün.

Yoxlama:

```bat
gradlew.bat :android-app:bundleRelease
"%LOCALAPPDATA%\Android\Sdk\build-tools\<versiya>\apksigner.bat" verify --print-certs android-app\build\outputs\apk\release\android-app-release.apk
```

## 17. Google Play-də dərc

1. [Play Console](https://play.google.com/console)-da developer hesabı (birdəfəlik ödəniş, şəxsiyyət təsdiqi).
2. *Create app* → ad “MIYAV”, standart dil Azərbaycan dili, Free.
3. **Play App Signing**-i qəbul edin və AAB-ni yükləyin.
4. *Store listing*:
   - Tətbiq ikonu 512×512: `android-app\store\play_icon_512.png`
   - Feature graphic 1024×500: `android-app\store\play_feature_1024x500.png`
   - Ekran görüntüləri: `docs\screenshots\` (emulyatorda çəkilib; real elanlarla yenilərini çəkmək tövsiyə olunur)
5. *App content*:
   - **Privacy policy URL** (məcburi; ictimai veb səhifə lazımdır).
   - **Data safety**: toplanan məlumatlar — e-poçt, ad, telefon (istəyə bağlı), istifadəçi şəkilləri (avatar və elan şəkilləri), istifadəçinin yazdığı mətnlər (elan, müraciət). Hamısı TLS ilə ötürülür. Təxmini məkan yalnız cihazda istifadə olunur və **toplanmır**. Reklam və üçüncü tərəf analitika yoxdur.
   - **Account deletion**: hesab yaradan tətbiqlər üçün Play tələb edir ki, tətbiqdə hesabı silmək yolu və veb keçid olsun. Bu hələ hazır deyil (19-cu bölmə).
   - Content rating anketi, Target audience (18+ tövsiyə olunur, çünki heyvan övladlığa götürmə müqaviləsidir).
6. *Testing → Internal testing*: AAB yükləyin, test istifadəçiləri əlavə edin, real cihazda yoxlayın.
7. Closed/Open testing (yeni şəxsi hesablar üçün Play 14 gün ərzində minimum testerlə qapalı test tələb edə bilər) → *Production* → *Send for review*.

---

## 18. Tez-tez rast gəlinən xətalar

| Problem | Həll |
| --- | --- |
| Tətbiqdə “Supabase ünvanı və publishable açar təyin edilməyib” | `local.properties`-də `supabase.url` və `supabase.publishableKey` yazın, yenidən yığın |
| `Release build needs supabase.url ...` | Release yığımı konfiqurasiyasız dayanır — eyni həll |
| `SDK location not found` | `local.properties`-də `sdk.dir` yazın və ya Android Studio-da açın |
| `Android resource linking failed ... not found` (qovluq köçürüldükdən sonra) | `gradlew --stop`, `android-app\build` qovluğunu silin, yenidən yığın |
| “Hesabı e-poçtdakı keçidlə təsdiqləyin” | Təsdiq məktubundakı keçidi açın; məktub gəlmirsə SMTP limitini/ayarlarını yoxlayın |
| “Çox sayda sorğu göndərildi” | Supabase e-poçt limiti. Gözləyin və ya öz SMTP-nizi qoşun |
| E-poçt keçidi tətbiqi açmır | 6.2: `az.miyav.native://auth` Redirect URLs siyahısında olmalıdır; keçidi tətbiq quraşdırılmış telefonda açın |
| “Keçid etibarsızdır” | Keçid bu cihazda qeydiyyat və ya bərpa istənən e-poçta aid deyil, ya da vaxtı bitib. Bu qəsdən qoruma mexanizmidir |
| Məkan: “dəstəklənən ərazidən kənardadır” | Yalnız Azərbaycan ərazisi (38–42° en, 44–51° uzunluq) dəstəklənir; emulyatorda məkanı Bakıya qoyun |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Fərqli açarla imzalanmış köhnə versiyanı silin |
| Lint: `GradleDependency` xəbərdarlıqları | Yeni versiyalar haqqında məlumatdır; yığımı dayandırmır |

---

## 19. Qalan xarici tələblər (sizin etməli olduqlarınız)

1. **Supabase Redirect URL**: `az.miyav.native://auth` əlavə edin (6.2).
2. **Custom SMTP**: istehsalda e-poçt göndərmək üçün məcburidir (6.3).
3. **Upload keystore** yaradın və `keystore.properties`-i doldurun (16).
4. **Google Play developer hesabı**.
5. **Privacy policy** səhifəsi (ictimai URL).
6. **Hesabın silinməsi**: Play siyasəti tətbiqdaxili silmə yolu və veb keçid tələb edir. Bu, məlumat saxlama barədə məhsul/hüquqi qərar tələb edir (övladlığa götürmə müraciətləri və elan tarixçəsi qalsınmı?) və `service_role` istifadə edən server funksiyası (Edge Function) lazımdır. Bu buraxılışda **edilməyib** və Play-ə göndərməzdən əvvəl tamamlanmalıdır.
7. Real Android cihazda son yoxlama (xüsusən qalereyadan şəkil seçimi və e-poçt keçidləri).

---

## 20. Layihənin dəqiq son vəziyyəti

### Yığımlar (bu maşında icra olunub)

| Yığım | Nəticə | Yer |
| --- | --- | --- |
| Debug APK | Uğurlu, debug imzalı | `android-app\build\outputs\apk\debug\android-app-debug.apk` |
| Release APK | Uğurlu, R8 ilə, **imzasız** (keystore yoxdur) | `android-app\build\outputs\apk\release\android-app-release-unsigned.apk` |
| Release AAB | Uğurlu, **imzasız** | `android-app\build\outputs\bundle\release\android-app-release.aab` |
| Lint (debug, tam) | 0 xəta | `android-app\build\reports\lint-results-debug.html` |
| Lint vital (release) | Keçdi | — |

### Testlər (həqiqətən icra olunub)

- Paylaşılan modullar: 28 JVM testi, 0 uğursuz.
- Android unit testləri: 6 test (deep link parser, outbox siyasəti), 0 uğursuz.
- **Canlı Supabase testləri** (`LiveSupabaseFlowTest`, QA hesabları ilə): 3/3 uğurlu — giriş, tokenin bərpada yenilənməsi, çıxışdan sonra refresh tokenin ləğvi, yanlış şifrənin rəddi, profil yeniləmə, qaralama → şəkil yükləmə/endirmə → dərc → axtarışda görünmə, başqa istifadəçinin elanı dəyişməsinin RLS ilə rəddi, müraciət, təkrar müraciətin bloku, öz elanına müraciətin bloku, yalnız sahibin qəbul edə bilməsi, “övladlığa verildi” statusu, arxiv, tətbiqdaxili şifrə dəyişmə (geri qaytarılıb).
- Şifrə bərpa sorğusu Supabase-ə çatdı, lakin daxili e-poçt limiti səbəbindən `auth.rate_limited` qaytardı — məktubun çatdırılması **yoxlanılmayıb**.
- **Emulyator (Android 16, Pixel 9 Pro), R8-li release yığımı** ilə əl ilə UI yoxlaması: ilk açılışda Azərbaycan dili, boş form validasiyası, yanlış şifrə mesajı, giriş, prosesi öldürdükdən sonra sessiyanın bərpası, bütün tablar, bildirişlər (real trigger bildirişi), redaktorda inline validasiya, UI ilə qaralama yaratma, detal, arxiv təsdiqi, məkan icazəsi dialoqu və rədd edilməsi, EN dilinə keçid, çıxış və çıxışdan sonra sessiyanın olmaması. Heç bir crash qeydə alınmadı.
- Yoxlanılmayanlar: sistem qalereyasından şəkil seçimi UI ilə (yükləmə kodu canlı testdə yoxlanılıb), e-poçt keçidi ilə tətbiqin açılması (Redirect URL dashboard-da əlavə edilməlidir), real fiziki cihaz.

### Təhlükəsizlik

- Sessiya Android Keystore-dakı AES-256-GCM açarı ilə şifrələnir; açar cihazdan çıxmır. Köhnə fayl əsaslı sessiya bir dəfə köçürülüb silinir.
- Bulud ehtiyatı və cihazdan-cihaza köçürmə tam söndürülüb (`allowBackup=false`, `dataExtractionRules`).
- Cleartext HTTP qadağandır (`network_security_config`).
- Yalnız `ACCESS_COARSE_LOCATION` (dəqiq məkan yox), `INTERNET`, `ACCESS_NETWORK_STATE`. Kamera və qalereya icazəsi lazım deyil (Android Photo Picker).
- E-poçt keçidləri yalnız bu cihazda istənilən ünvan üçün qəbul edilir (session fixation qoruması).
- Şifrə dəyişmədən əvvəl hazırkı şifrə yenidən yoxlanılır.
- Release-də `Log.v/d/i` R8 ilə silinir; tokenlər loga yazılmır.

### Qərar

Android tətbiqi mövcud etimadnamələrlə mümkün olan həddə qədər hazırdır: funksiyalar tamamlanıb, canlı backend-ə qarşı test olunub və release yığımı yoxlanılıb. **Google Play-ə göndərmək üçün** 19-cu bölmədəki addımlar (xüsusən Redirect URL, SMTP, upload açarı, privacy policy və hesab silmə) tamamlanmalıdır.
