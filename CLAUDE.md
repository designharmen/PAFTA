# PAFTA — çalışma kuralları

Bu dosya, proje sahibinin her sohbette tekrar anlatmak zorunda kalmaması için
yazıldı. Yeni bir sohbet başladığında **önce bu dosya, sonra `docs/ROADMAP.md`**
okunur. Buradaki kurallar projenin tamamı için geçerlidir ve tek tek onay
gerektirmez.

---

## 1. Proje sahibiyle nasıl konuşulur (en önemli kural)

Proje sahibi yazılımdan hiç anlamıyor.

- **Hiçbir teknik terim açıklamadan kullanılmaz.** "build", "commit", "dal",
  "bağımlılık", "depo", "günlük", "log", "branch", "merge" gibi kelimelerin
  bilinmediği varsayılır.
- Ondan bir şey yapması istenirken, **daha önce hiç yapmamış biriyle konuşur
  gibi**, ekranda tam olarak ne göreceği tarif edilerek adım adım anlatılır.
- Durum anlatımı hep şu sırayla: **ne yapıldı → şu an neredeyiz → ondan ne
  bekleniyor → sıradaki adım ne.** Günlük dille. Teknik ayrıntı arka planda
  tutulur; sorarsa anlatılır.
- Hata çıkınca "hata kodu X" denmez. **"Şu problemle karşılaştık, anlamı şu,
  senden istediğim şu"** denir.
- Bir şeyi test etmesi istenecekse; dosyayı nereden indireceği, hangi ayarı
  açacağı, hangi tuşa basacağı da yazılır.
- **Her zaman Türkçe yazılır.**

Teknik ustalık azalmaz; sadece proje sahibiyle konuşurken tercümanlık yapılır.

---

## 2. Uygulamanın dili: istisnasız Türkçe

Kullanıcının göreceği her yazı Türkçedir: menüler, sekmeler, araç isimleri,
panel başlıkları, mesajlar, hata metinleri, birim etiketleri, tarihler. Hiçbir
yerde İngilizce görünmez.

Bunu koruyan yapı şöyle kuruldu — **bozulmaz**:

- Kullanıcıya görünen her metin yalnızca
  `app/src/main/res/values/strings.xml` içindedir. Kotlin kodunda kullanıcıya
  görünen metin olması bir hatadır.
- Etiketler ve özellik anahtarları `String` değil **`@StringRes Int`** tipindedir.
  Tipi sayı olan bir şey yanlışlıkla İngilizce kelime olamaz.
- Hata tipleri cümle değil **veri** taşır: `UnknownFormat(uzantı)`,
  `TooLarge(boyut, sınır)`, `Unreadable(biçim, sebep)`, `Io(sebep)`. Bunları
  Türkçeye çeviren tek yer `app/.../ui/UiText.kt`.
  Bir hata tipine tekrar `message: String` eklemek, Türkçe ekrana düşmeyi
  bekleyen İngilizce cümle demektir — **yapılmaz**. `StoreFailureTest` bunu
  bekçilik eder.
- İşletim sisteminin ürettiği hata metinleri sadece kayıt içindir, ekrana asla
  çıkmaz (dili cihaza göre değişir).
- Tarih biçimi cihaz diline değil, sabit Türkçeye bağlıdır.
- Değişken ve fonksiyon isimleri İngilizce kalır, bu normaldir.

Kararlaştırılmış sözlük: `docs/TURKCE-SOZLUK.md`. Bir kelimeyi değiştirmek kod
değişikliği değil, **dil değişikliğidir** — sözlük de birlikte güncellenir.

---

## 3. Gerçekçi başarı tanımı

Bu bir "tek seferde hatasız uygulama" projesi değil. Native C++ bağımlılıkları
olan gerçek bir Android uygulaması.

- **Başarı ölçütü:** her fazın sonunda cihazda çalışan, test edilmiş bir kurulum
  dosyası.
- Hata çıkması normaldir. Çıkan her hata **açıkça raporlanır**, düzeltilir, sonra
  bir sonraki faza geçilir.
- **Uydurma veya varsayılmış "başarılı" sonuç asla raporlanmaz.** "Muhtemelen
  çalışır" bir sonuç değildir; gerçek çıktı paylaşılır.
- **Derlemenin başarılı olması uygulamanın açıldığını KANITLAMAZ.** Bunu sadece
  cihaz testi gösterir.
- Bir faz tıkanırsa hangi kısmın tıkandığı söylenir, geri kalanı eksiksiz teslim
  edilir.
- Her fazın sonunda kayıt atılır, böylece bozulursa önceki çalışan sürüme
  dönülebilir.

---

## 4. Kendi düzenlemene güvenme, doğrula

Bu projede en pahalı hata beş kez tekrarlandı: yapıldığı sanılan bir
düzenlemenin dosyaya hiç yazılmamış olması — ancak dakikalar sonra derleyici
yakaladı. Beşinin de tek sebebi, dosyayı yazmadan sonraki dosyaya geçen bir
betikti. (Ayrıntısı `docs/ROADMAP.md` → *The recurring mistake, and its actual
cause*.)

**Kural:** her dosya bir sonrakine geçmeden yazılır; sonra kaynakta
**"artık şu olmalı"** ve **"artık şu olmamalı"** diye aranır. Varsayma, doğrula.
İkincisi — olmaması gerekenin aranması — asıl yakalayan kontroldür.

---

## 5. Her derlemeden önce

```bash
python3 tools/check-strings.py
./gradlew -PpaftaCoreOnly=true test
```

Birincisi saniyeler sürer ve Android'in dakikalar sonra bulacağı hataları hemen
yakalar: kaçırılmamış kesme işareti ve tırnak, `@` veya `?` ile başlayan değer,
sırasız çok argümanlı metin, tanımsız `R.string`, `R`'yi içe almadan `R.string`
kullanan dosya, `R` alanından `const val`, karışık `Modifier.padding`, ve
`StoreFailure.message` kalıntısı.

**Yeni bir hata türü bir derlemeye mal olduysa, oraya bir kontrol daha eklenir.**

İkincisi saf Kotlin çekirdeği test eder ve Android araçları gerektirmez.

Kurulum dosyası (APK) GitHub'da otomatik üretilir
(`.github/workflows/apk.yml`). Proje sahibinin nasıl indirip kuracağı
`docs/APK-NASIL-KURULUR.md` içinde adım adım yazılıdır.

---

## 6. Lisans kısıtı

Yalnızca **MIT / BSD / Apache-2.0 / LGPL / GPL / AGPL** bileşenler. Ticari SDK
yok, üyelik yok, ödeme gerektiren hiçbir şey yok.

**DİKKAT:** DWG okuyan tek ücretsiz kütüphane **LibreDWG** ve lisansı
**GPL-3.0**. Kullanılırsa uygulamanın **TAMAMI** o lisansla dağıtılmak zorunda
kalır — yani PAFTA kapalı kaynak ticari ürün olarak satılamaz. Kısıta uyuyor ama
bu, proje sahibinin **bilerek vermesi gereken** bir karardır.
**O onaylamadan bu işe başlanmaz.** Aynı dikkat **CuraEngine (AGPL-3.0)** için de
geçerlidir.

Ayrıntı: `docs/LICENCES.md`

---

## 7. Çalışma yöntemi

Her faz tek paragraflık özetle başlar: **amaç, teknik yaklaşım, dosya listesi,
risk, test planı.** Kod yazılır, gerçekten derlenir, çalıştırılır. Çıkan hatalar
olduğu gibi raporlanır. Faz kayıtla kapanır.

Cihaz olmadan test edilebilen her şey **`core/` altında** olmalıdır. Birimler,
geometri, DXF motoru, ölçüm, proje dosyası, geri al ve otomatik kayıt mantığı bu
yüzden saf Kotlin ve bu yüzden test edilebiliyor.

---

## Dosya haritası (hızlı başvuru)

| Nerede | Ne |
| --- | --- |
| `docs/ROADMAP.md` | fazlar, bulunan her hata ve neden öyle çözüldüğü |
| `docs/DESIGN-SYSTEM.md` | Harmen Design: renkler, yazı tipleri, yerleşim |
| `docs/TURKCE-SOZLUK.md` | arayüzün Türkçe karşılıkları |
| `docs/LICENCES.md` | bileşenler ve lisansları |
| `docs/PHASE0-ENVIRONMENT.md` | geliştirme ortamında neyin olup neyin olmadığı |
| `docs/APK-NASIL-KURULUR.md` | proje sahibi için kurulum anlatımı |
| `tools/check-strings.py` | derlemeden önceki hızlı kontroller |
