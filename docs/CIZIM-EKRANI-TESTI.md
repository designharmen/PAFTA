# Çizim ekranı testi — Faz 1'in son adımı

Uygulamanın kütüphane ekranı tablette çalıştığı görüldü: proje listesi göründü,
gerçek bir DWG içeri alındı ve saklandı. **Görülmeyen tek şey çizim ekranı** —
bir çizimin gerçekten ekrana çizildiği henüz hiç kimse tarafından görülmedi.

Bu belge o testi anlatıyor. Yaklaşık 20 dakika sürer.

---

## 1. Bölüm — Çizimi uygulamanın anladığı biçime çevirme

Uygulama bugün **DXF** uzantılı çizimleri açabiliyor. DWG ve Revit dosyaları
içeri alınıyor ve güvenle saklanıyor, ama henüz çizilemiyor. Bu yüzden testte
elindeki bir çizimi bir kez DXF olarak kaydedeceksin.

Küçük ve sade bir çizim seç — tek katlı bir plan, tercihen tek bir kat, çok
katmanlı bir paftadan iyidir. İlk testte "çiziliyor mu" sorusuna bakıyoruz.

### AutoCAD kullanıyorsan

1. Çizimi AutoCAD'de aç.
2. Sol üstteki **A** simgesine (veya **Dosya** menüsüne) tıkla.
3. **Farklı Kaydet** → **Diğer Biçimler** yolunu izle.
4. Açılan pencerede altta **"Kayıt türü"** yazan açılır listeye tıkla.
5. Listeden **AutoCAD 2013/LT2013 DXF (*.dxf)** satırını seç. (Listede 2018,
   2010, R12 gibi başka DXF satırları da olur; hangisi olduğu önemli değil,
   yeter ki sonunda **DXF** yazsın.)
6. Dosyaya bir isim ver, **Kaydet**'e bas.

### Revit kullanıyorsan

1. Görmek istediğin kat planını Revit'te aç (dışa aktarma **açık olan görünümü**
   dışa aktarır).
2. Sol üstteki **Dosya** → **Dışa Aktar** → **CAD Biçimleri** → **DWG/DXF**
   yolunu izle.
3. Açılan pencerede biçim olarak **DXF**'i seç, **İleri**/**Sonraki** ile
   devam et ve kaydet.

### İkisi de yoksa

Open Design Alliance'ın ücretsiz **ODA File Converter** programı DWG dosyasını
DXF'e çevirir. Kurulumu ücretsizdir. Bu durumda bana söyle, indirme ve kullanım
adımlarını ayrıca yazarım.

Sonuçta elinde **`.dxf`** ile biten bir dosya olacak. Bu dosyayı tablete aktar
(USB kablosu, WhatsApp veya e-posta — hangisi kolaysa).

---

## 2. Bölüm — Güncel uygulamayı kurma

Elindeki PAFTA eski olabilir. En yenisini kurmak için
[APK-NASIL-KURULUR.md](APK-NASIL-KURULUR.md) belgesindeki adımları izle —
listenin **en üstündeki** satırı seç, o en yenisidir.

Kurulum sırasında "uygulama yüklenmedi" yazarsa, eskisini kaldırıp tekrar dene
(uygulama simgesine basılı tut → Kaldır).

---

## 3. Bölüm — Testin kendisi

1. PAFTA'yı aç.
2. Sağ üstteki **İÇE AKTAR** düğmesine dokun.
3. Açılan dosya seçme ekranından az önce hazırladığın **`.dxf`** dosyasını seç.
4. Birkaç saniye bekle.

**Görmen gereken:** ekran değişir ve çizim ekranı açılır —

- solda yukarıdan aşağıya araç listesi: **Seç, Kalem, Çizgi, Yay, Ölçü,
  Ölçüler, Tarama, Metin, Izgara, Ölç, Palet, Katmanlar**
- ortada, koyu zemin üzerinde **açık renkli çizgilerle çizimin**
- sağda dört bölüm: **[Katman Paleti]**, **[Malzeme Seçici]**, **[Özellikler]**,
  **[Not Araçları]**
- sağdaki **[Özellikler]** bölümünde çizimin kendi sayıları: öğe sayısı, katman
  sayısı, genişlik ve yükseklik

5. Ortadaki çizime **iki parmağınla dokunup parmaklarını aç** (yakınlaştırma) ve
   **tek parmakla sürükle** (kaydırma). Çizgilerin takılmadan hareket etmesi
   gerekiyor.
6. Sağdaki **[Katman Paleti]** bölümünde bir katmanın adına dokun; o katmanın
   çizgilerinin ekrandan kaybolması gerekiyor. Tekrar dokununca geri gelmeli.

---

## 4. Bölüm — Bana ne yazacaksın

Hangi sonuç çıkarsa çıksın, **ne gördüğünü** yaz. Tek cümle yeter.

| Gördüğün | Anlamı | Bana yaz |
| --- | --- | --- |
| Çizim ekrana geldi, tanıdık geliyor | Faz 1 bitti | "çizim geldi" |
| Ekran açıldı ama orta kısım bomboş | Çizim okundu, çizilemedi | "ekran boş" + sağdaki öğe sayısı |
| Ortada sadece küçük **artı/çarpı işaretleri** var | Aşağıdaki bilinen eksik | "artı işaretleri var" |
| Uygulama kapandı | Çökme | hangi adımda kapandığı |
| Türkçe bir hata mesajı çıktı | Beklenen durum | mesajın kendisi |
| Yakınlaştırırken takılıyor / donuyor | Çizim çok büyük olabilir | "takılıyor" + öğe sayısı |

Ekran görüntüsü alabilirsen daha da iyi; ama şart değil, "şu adımda şunu gördüm"
yeterli.

---

## Kapanan eksik: hazır parçalar (bloklar)

CAD çizimlerinde kapı, pencere, mobilya, antet gibi tekrar eden parçalar
genellikle **hazır parça** olarak yerleştirilir: çizimde bir kez tanımlanır,
sonra defalarca çağrılır.

İlk denemede PAFTA bu çağrıları görüyor ama içlerini açmıyordu — her biri
yerinde küçük bir artı işareti olarak çiziliyordu. **Bu artık düzeltildi:**
hazır parçaların içindeki çizgiler, döndürülmüş ve ölçeklenmiş halleriyle
gerçekten çiziliyor. Aynalanmış parçalar (ters yöne açılan kapılar) da doğru
yöne bakıyor.

Yine de artı işareti görürsen, o parçanın tanımı dosyanın içinde yok demektir —
bu, dosyanın kendi eksiğidir. Gördüğün yeri söyle, beraber bakarız.

## Dosya geri çevrilirse

PAFTA artık okuyabildiği hiçbir DXF dosyasını geri çevirmiyor; çizemese bile
dosyayı projenin içine kaydediyor. Çizilecek bir şey bulamazsa şöyle bir mesaj
veriyor:

> Bu DXF çizimi açıldı ama içinde çizilecek bir şey yok. İçinde şunlar var:
> HATCH (1240), SPLINE (12)

Sondaki listeyi **olduğu gibi bana yaz**. Dosyanın içinde tam olarak neyin
bulunduğunu söyleyen kısım orasıdır ve sıradaki işi o belirler. Liste hiç
çıkmazsa, o da ayrı bir bilgi: dosyanın çizim bölümü tamamen boş demektir.
