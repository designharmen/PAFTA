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
| FILE | **DOSYA** |
| EDIT (menü) | **DÜZENLE** |
| SHARE | **PAYLAŞ** |
| EDIT (mod) | **DÜZENLE** |
| VIEW (mod) | **GÖRÜNÜM** |
| Undo / Redo | **Geri al** / **Yeniden yap** |
| — | **PROJELER** (kütüphaneye dönüş) |

## Sekme grubu

| Referanstaki | PAFTA'da |
| --- | --- |
| Active | **Aktif** |
| Annotations | **Notlar** |
| Furniture | **Mobilya** |
| Walls | **Duvarlar** |
| Grid | **Izgara** |

## Sol araç paneli

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
| Palette | **Palet** |
| Layers | **Katmanlar** |

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

PROJELER · İÇE AKTAR · KAPAT · Okunamayan ·
"henüz proje yok" · "başlamak için bir DXF çizimi içe aktarın"

## Hata mesajları

Hata mesajları kod numarası değil, ne olduğunu anlatan cümleler:

- "PAFTA .docx uzantılı dosyaları tanımıyor"
- "Bu dosyanın içi boş"
- "Dosya 312 MB. En fazla 256 MB kabul ediliyor"
- "Bu DXF çizimi bozuk görünüyor, okunamadı"
- "Bu DXF çizimi açıldı ama içinde çizilecek bir şey yok"
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
