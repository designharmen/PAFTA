# PAFTA'yı tabletinize / telefonunuza kurma

Bilgisayarınıza hiçbir program kurmanız gerekmiyor. Uygulama dosyası internette,
GitHub sayfasında hazırlanıyor; siz sadece indirip kuruyorsunuz.

Aşağıdaki adımları ilk kez yapacak biri gibi, tek tek yazdım.

---

## 1. Bölüm — Uygulama dosyasını indirme

Bu adımları **bilgisayardan** yapmak daha kolay, ama tabletten de yapılabilir.

1. İnternet tarayıcınızı açın ve şu adrese gidin:

   `https://github.com/designharmen/mutfakgroup-fiyat/actions`

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
