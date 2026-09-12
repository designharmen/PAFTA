# PAFTA'yı tabletinize / telefonunuza kurma

Bilgisayarınıza hiçbir program kurmanız gerekmiyor. Uygulama dosyası internette,
GitHub sayfasında hazırlanıyor; siz sadece indirip kuruyorsunuz.

Aşağıdaki adımları ilk kez yapacak biri gibi, tek tek yazdım.

---

## 0. Bölüm — İkinci kurulumdan sonra: uygulamanın kendi GÜNCELLE tuşu

**Bu bölümü bir kez okuduktan sonra 1. ve 2. bölümlere bir daha ihtiyacın
olmayacak.** Aşağıdaki uzun yol yalnızca *ilk* kurulum için.

PAFTA'nın kütüphane ekranında, sağ üstte **GÜNCELLE** yazan bir düğme var.
Dokununca sırayla şunlar olur:

1. Yeni bir sürüm var mı diye bakar. Yoksa **"En güncel sürüm zaten kurulu"**
   yazar, başka bir şey yapmaz.
2. Varsa indirmeye başlar. Alttaki çizgide **"yapım 14 indiriliyor · %37"** gibi
   ilerlemeyi görürsün.
3. İnince Android'in kendi **kurulum ekranı** açılır. **Yükle**'ye dokunursun,
   birkaç saniye sonra biter.

Eski sürümü silmene gerek yok, dosya indirip çıkarmana da.

### İlk seferde bir kez izin

Android, bir uygulamanın başka bir uygulamayı kurmasına kendiliğinden izin
vermez. İlk GÜNCELLE denemende şunu göreceksin:

> *Kurulum için bir kez izin vermen gerekiyor. Açılan ayarda PAFTA'ya izin ver,
> sonra GÜNCELLE'ye tekrar dokun*

Düğmeye tekrar dokun, açılan ayar sayfasında **PAFTA**'nın yanındaki anahtarı
aç, geri dön ve **GÜNCELLE**'ye bir kez daha dokun. Bu izni ömründe bir kez
veriyorsun.

### Neden tamamen otomatik değil

Android hiçbir uygulamanın, sen onaylamadan başka bir uygulama kurmasına izin
vermiyor — bu bir eksiklik değil, telefonunu koruyan bir kural. Yapabildiğimiz
en iyisi, yedi adımlık işi **tek dokunuş + tek onay**'a indirmek. O da yapıldı.

---

## 1. Bölüm — Uygulama dosyasını indirme

Bu adımları **bilgisayardan** yapmak daha kolay, ama tabletten de yapılabilir.

1. İnternet tarayıcınızı açın ve şu adrese gidin:

   `https://github.com/designharmen/PAFTA/actions`

2. Ekranın ortasında bir liste göreceksiniz. Her satır, bir "uygulama hazırlama"
   denemesidir. En üstteki satır en yeni olanıdır.

3. Satırın solundaki işarete bakın:
   - **Yeşil tik (✓)** → hazırlama başarılı, dosya indirilebilir.
   - **Kırmızı çarpı (✗)** → bir hata var, dosya yok. Bu durumda bana haber
     verin, düzeltirim.
   - **Sarı dönen daire** → hâlâ çalışıyor. 3–5 dakika bekleyip sayfayı
     yenileyin (tarayıcıdaki yuvarlak oklu yenile düğmesi).

4. En üstteki satırın **yazısına tıklayın** (işaretine değil, yanındaki yazıya).

5. Açılan sayfayı **en aşağıya kaydırın**. Orada **"Artifacts"** yazan bir bölüm
   var. İçinde **`PAFTA-apk`** yazan bir satır göreceksiniz.

6. `PAFTA-apk` yazısına tıklayın. Bilgisayarınıza bir sıkıştırılmış dosya
   (`PAFTA-apk.zip`) inecek.

7. İnen dosyaya sağ tıklayıp **"Tümünü ayıkla"** / **"Buraya çıkart"** deyin.
   İçinden `app-debug.apk` adlı dosya çıkacak. **Kuracağımız dosya budur.**

8. Bu `app-debug.apk` dosyasını tabletinize aktarın. En kolay yolları:
   - USB kablosuyla bilgisayara bağlayıp dosyayı tabletin "İndirilenler"
     klasörüne kopyalamak, veya
   - Dosyayı kendinize WhatsApp / e-posta ile göndermek, tabletten indirmek.

> **Not:** Tabletten indirirseniz 6. adımdaki dosya doğrudan tablete iner,
> aktarmaya gerek kalmaz.

---

## 2. Bölüm — Tablette kurulum izni verme

Android, internetten indirilen uygulamaları güvenlik gereği ilk seferde
engeller. Bir kez izin vermeniz gerekiyor. Bu normaldir ve geri alınabilir.

1. Tablette **Dosyalar** (veya **Dosya Yöneticisi**) uygulamasını açın.
2. **İndirilenler** klasörüne girin.
3. `app-debug.apk` dosyasına dokunun.
4. Ekrana şuna benzer bir yazı gelecek:

   > *"Güvenlik nedeniyle telefonunuz bu kaynaktan bilinmeyen uygulamaların
   > yüklenmesine izin vermiyor."*

5. Aynı ekranda **"Ayarlar"** düğmesine dokunun.
6. Açılan ayar sayfasında **"Bu kaynaktan izin ver"** yazısının yanındaki
   anahtarı açın (gri anahtar sağa kaydırılınca renkli olur).
7. Tabletteki **geri** düğmesine basın.
8. Tekrar `app-debug.apk` dosyasına dokunun.
9. Bu kez **"Yükle"** düğmesi çıkacak. Dokunun.
10. Birkaç saniye sonra **"Uygulama yüklendi"** yazacak. **"Aç"** düğmesine
    dokunun.

Artık uygulama listenizde **PAFTA** adıyla, koyu zeminde "P" harfi olan
simgesiyle duruyor.

---

## 3. Bölüm — İlk açılışta ne görmeniz gerekiyor

1. Ekran koyu (neredeyse siyah) açılır.
2. Üstte sol köşede ince çerçeveli kutu içinde **P** harfi, yanında
   **PROJELER** yazısı, sağ üstte **İÇE AKTAR** düğmesi olur.
3. Ortada **"henüz proje yok"** ve altında
   **"başlamak için bir DXF çizimi içe aktarın"** yazar.
4. **İÇE AKTAR**'a dokunun. Android'in kendi dosya seçme ekranı açılır.
5. Bir `.dxf` çizim dosyası seçin.
6. Çizim ekranı açılır: solda araç listesi (Seç, Kalem, Çizgi, Yay…), ortada
   çiziminiz, sağda **[Katman Paleti]**, **[Malzeme Seçici]**, **[Özellikler]**,
   **[Not Araçları]** bölümleri.

Bunlardan biri böyle olmazsa — özellikle uygulama **açılır açılmaz kapanırsa** —
bana "şu adımda şunu gördüm" diye yazın, yeter. Hangi adımda durduğunu bilmem
sorunu bulmak için kâfi.

---

## Sık çıkan durumlar

**"Uygulama yüklenmedi" yazıyor.**
Telefonda aynı isimli eski bir sürüm olabilir. Eski PAFTA'yı kaldırıp tekrar
deneyin (uygulama simgesine basılı tutun → Kaldır).

**Dosya seçme ekranında çizimim görünmüyor.**
Sol üstteki üç çizgili menüden çizimin bulunduğu yeri (İndirilenler, Drive vb.)
seçin. PAFTA bütün dosya türlerini gösterir, bu yüzden filtre yüzünden gizlenmiş
olamaz.

**Dosyayı seçtim ama hata mesajı çıktı.**
Mesajı okuyun — ne olduğunu Türkçe yazıyor (örnek: "Bu DXF çizimi bozuk
görünüyor, okunamadı"). Mesajı bana aktarın.
