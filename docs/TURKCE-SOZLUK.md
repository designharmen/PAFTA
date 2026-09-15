# Türkçe arayüz sözlüğü

PAFTA'nın arayüzünde kullanıcının göreceği **hiçbir İngilizce metin yok**.
Aşağıdaki karşılıklar uygulandı. Referans görseldeki İngilizce etiketler
kopyalanmadı, Türkçeleri kullanıldı.

Değiştirmek istediğiniz bir kelime varsa söyleyin — tek tek değiştirilebilir,
çünkü bütün yazılar tek bir dosyada toplandı
(`app/src/main/res/values/strings.xml`).

## Üst bar

| Referanstaki | PAFTA'da |
| --- | --- |
| Undo / Redo | **Geri al** / **Yeniden yap** |
| — | **Projelere dön** (P işaretine dokununca) |

DOSYA, DÜZENLE, PAYLAŞ, DÜZENLE/GÖRÜNÜM modları ve Aktif · Notlar · Mobilya ·
Duvarlar · Izgara sekmeleri **kaldırıldı**: hiçbiri çizimde bir şeyi
değiştirmiyordu, yani dokunup hiçbir şey olmayan beş ayrı düğmeydi. Yerlerini
gerçekten çalışan çizim araçları aldı.

## Yapı elemanları sütunu (en sağ)

| Referanstaki | PAFTA'da |
| --- | --- |
| Wall | **Duvar** |
| Door | **Kapı** |
| Window | **Pencere** |
| Room / Space | **Mahal** |
| Floor / Slab | **Döşeme** |
| Column | **Kolon** |
| Beam | **Kiriş** |
| Furniture | **Mobilya** |
| Elements | **Yapı** (sütun başlığı) |
| Open / Close panel | **Paneli aç** / **Paneli kapat** |

## Araç ayarları şeridi

| Referanstaki | PAFTA'da |
| --- | --- |
| Thickness | **Kalınlık** |
| Material | **Malzeme** |
| Width | **Genişlik** |
| Fillet radius | **Yuvarlatma yarıçapı** |
| Chamfer distance | **Pah ölçüsü** |
| Measure what | **Ne ölçülecek** |
| Column size | **Kolon ölçüsü** |
| Shape | **Biçim** → **Kare** / **Yuvarlak** |
| Beam width | **Kiriş genişliği** |
| Slab thickness | **Döşeme kalınlığı** |
| What it is | **Ne olacak** → **Kat döşemesi** / **Teras çatı** |

## Kütüphane (Mobilya aracı)

Beş çekmece: **Oturma** · **Yemek** · **Yatak odası** · **Mutfak** · **Banyo**

Berjer · İkili kanepe · Üçlü kanepe · Sehpa · TV ünitesi · Masa (4 kişilik) ·
Masa (6 kişilik) · Yuvarlak masa · Sandalye · Tek kişilik yatak · Çift kişilik
yatak · Gardırop · Komodin · Çalışma masası · Tezgâh · Evye · Ocak · Buzdolabı ·
Klozet · Lavabo · Duş teknesi · Küvet

## Hazır duvarlar (yirmi tip)

Duvar aracının ayar şeridinde, adı **malzeme + kalınlık · görev** biçiminde:
örneğin *Beton 300 · Perde*. Görevler: **Bölme** · **Taşıyıcı** · **Perde** ·
**İstinat** · **Parapet**

## Döşeme kaplamaları (şartnamedeki yirmi kaplama)

Dört aile: **Ahşap** · **Taş ve seramik** · **Esnek ve dökme** · **Tekstil**

Her malzemenin ve her kaplamanın yanında, o malzemeyi anlatan çizilmiş bir
küçük kare var — tuğla için duvar örgüsü, beton için agrega, gazbeton için
blok, ahşap için damar; kaplamalarda tahta, karo, dökme ve hav. Aynı desen,
planda duvarın içini de dolduruyor.

Masif ahşap · Lamine parke · Laminat · Parke · Bambu · Seramik · Porselen ·
Mermer · Granit · Traverten · Karo mozaik · Arduvaz · PVC · Linolyum · Kauçuk ·
Epoksi · Perdahlı beton · Şap · Halı · Karo halı

## Üstteki çizim araçları

| Referanstaki | PAFTA'da |
| --- | --- |
| Select | **Seç** |
| Pencil | **Kalem** |
| Line | **Çizgi** |
| Arc | **Yay** |
| Dim | **Ölçü** |
| Dimensions | **Ölçüler** |
| Hatch | **Tarama** |
| Text | **Metin** |
| Grid | **Izgara** |
| Measure | **Ölç** |
| Layers | **Katmanlar** (sağdaki paneli açıp kapatan düğme) |
| Fillet | **Yuvarlat** |
| Chamfer | **Pah kır** |
| Trim | **Buda** |
| Extend | **Uzat** |
| Mirror | **Aynala** |
| Join | **Birleştir** |
| Scale | **Ölçekle** (sağ panelde) |

"Palet" kaldırıldı: hiçbir zaman bir işe bağlanmamıştı.

## Sağ panel başlıkları

Köşeli parantez biçimi korundu, içindeki kelime Türkçeleştirildi.

| Referanstaki | PAFTA'da |
| --- | --- |
| `[Layers Palette]` | **`[Katman Paleti]`** |
| `[Material Selector]` | **`[Malzeme Seçici]`** |
| `[Properties]` | **`[Özellikler]`** |
| `[Annotation Tools]` | **`[Not Araçları]`** |

## Not araçları

| Referanstaki | PAFTA'da |
| --- | --- |
| Text | **Metin** |
| Arrow | **Ok** |
| Pin | **İşaret** |
| Dimension | **Ölçü** |
| Callout | **Açıklama** |
| Stamp | **Kaşe** |
| Comment | **Yorum** |

## Özellikler tablosu

| Referanstaki | PAFTA'da |
| --- | --- |
| Wall | **Duvar** |
| Length | **Uzunluk** |
| Height | **Yükseklik** |
| Area | **Alan** |
| Layer | **Katman** |
| Material | **Malzeme** |
| Entities | **Öğe sayısı** |
| Not shown | **Gösterilmeyen** |

## Oda etiketleri (örnek plan)

| Referanstaki | PAFTA'da |
| --- | --- |
| LIVING | **Salon** |
| KITCHEN | **Mutfak** |
| BATH | **Banyo** |
| MASTER BED | **Ebeveyn Yatak** |
| TERRACE | **Teras** |

## Malzemeler

Sıva · Meşe · Beton · Pirinç · Arduvaz · Cam

## Proje kütüphanesi

PROJELER · YENİ PROJE · İÇE AKTAR · GÜNCELLE · KAPAT · Okunamayan ·
"henüz proje yok" · "boş bir proje oluştur, ya da elindeki DXF çizimini içe aktar"

Yeni proje sorusu: **Yeni proje** · **Projenin adı** · **OLUŞTUR** · **VAZGEÇ** ·
"Boş bir sayfa açılacak, çizmeye hemen başlayabilirsin."

## Hata mesajları

Hata mesajları kod numarası değil, ne olduğunu anlatan cümleler:

- "PAFTA .docx uzantılı dosyaları tanımıyor"
- "Bu dosyanın içi boş"
- "Dosya 312 MB. En fazla 256 MB kabul ediliyor"
- "Bu DXF çizimi bozuk görünüyor, okunamadı"
- "Bu DXF çizimi açıldı ama içinde çizilecek bir şey yok"
- "Bu DXF çizimi açıldı ama içinde çizilecek bir şey yok. İçinde şunlar var:
  HATCH (1240), SPLINE (12)" — sondaki liste çizimin kendi kayıt adlarıdır;
  çevrilmez, çünkü o adlar dosyanın içindeki gerçek adlardır ve sorunu bulmayı
  sağlayan şey tam olarak onları olduğu gibi göstermektir
- "PAFTA DWG çizimi dosyalarını henüz gösteremiyor. Dosya projenin içine güvenle
  kaydedildi, sonraki sürümde açılabilecek"
- "Proje kaydedilemedi. Cihazda boş yer kalmamış olabilir"
- "PAFTA bu dosyayı okuma izni alamadı"

## Bunun kod tarafındaki karşılığı

Değişken ve fonksiyon isimleri İngilizce kaldı (yazılım standardı). Buna karşılık
programın "şu dosya çok büyük" gibi durumları artık **cümle olarak değil, veri
olarak** taşıyor: kaç bayt, hangi uzantı, hangi sebep. Cümleye çevrilmesi tek bir
yerde, Türkçe metin dosyasına bakılarak yapılıyor. Böylece bir İngilizce cümlenin
kazara ekrana düşmesi mümkün değil — test de bunu kontrol ediyor
(`StoreFailureTest`).

İşletim sisteminin kendi ürettiği teknik mesajlar (ki dili cihaza göre değişir)
hiçbir zaman ekranda gösterilmiyor, yalnızca kayıt için saklanıyor.
