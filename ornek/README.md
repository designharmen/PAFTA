# Örnek çizim

`kat-plani.dxf` — 12 × 9 metrelik, iki odalı bir kat planı. **Bu dosya bilerek
üretildi**, internetten bulunmadı: içinde ne olduğu biliniyor ve her seferinde
sınanıyor.

Neden var: çizim ekranının ilk cihaz denemesi, arama sonucundan indirilmiş bir
DXF ile yapıldı ve o dosyanın içinde çizilecek hiçbir şey çıkmadı. Dolayısıyla
deneme, uygulama hakkında hiçbir şey söylemedi. Bu dosya o belirsizliği
tamamen kaldırıyor: **bu çizim cihazda görünmüyorsa hata bizdedir.**

İçindekiler — gerçek bir CAD planında ne varsa:

| Katman | Ne var |
| --- | --- |
| `DUVAR` | dış duvarın iki yüzü (kapalı çokgen) ve dört iç duvar |
| `KAPI` | hazır parça olarak yerleştirilmiş dört kapı — döndürülmüş ve aynalanmış olanlar dahil |
| `PENCERE` | dört pencere, her biri üç çizgi |
| `MOBILYA` | yatak, yemek masası, mutfak tezgahı, küvet |
| `METIN` | SALON, MUTFAK, BANYO, YATAK ODASI, ANTRE ve bir başlık |

Toplam 40 öğe. Ölçü birimi milimetre.

Dosyayı üreten betik: `tools/ornek-plan-uret.py`. Okunduğunda ne çıkması
gerektiği `core/dxf` ve `core/project` testlerinde yazılı
(`SamplePlanFileTest`, `SamplePlanImportTest`), yani dosya ile okuyucu birbirinden
habersiz bozulamaz.
