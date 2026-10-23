# Örnek dosyalar

Bu klasör, cihazda bir şey denemek istediğinde elinin altında **ne olduğu
bilinen** dosyalar bulunsun diye var. Buradaki bir dosya tablette açılmıyorsa
hata dosyada değil, bizde.

| Dosya | Ne |
| --- | --- |
| `kat-plani.dxf` | iki odalı bir kat planı — aşağıda ayrıntısı var |
| `ornek-cizim-autocad-2018.dwg` | **gerçek bir AutoCAD 2018 çizimi.** DWG desteğini denemek için: içinde çizgiler, yazılar, bloklar, bir elips, kafes yüzeyleri var |
| `ornek-ev.obj` / `.stl` / `.glb` | aynı küçük evin üç ayrı 3B biçimi: 6 × 4 metre, mahyası 4,3 metrede |

---

## Kat planı

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


---

## AutoCAD çizimi

`ornek-cizim-autocad-2018.dwg` LibreDWG projesinin deneme dosyalarından biri
(GPL-3.0, `docs/LICENCES.md` içinde yazılı) ve **gerçek AutoCAD'in yazdığı bir
dosya** — bizim ürettiğimiz bir şey değil. DWG okuyucusunu denemenin en
doğrudan yolu bu: içinde 68 çizgi, 11 çok köşeli çizgi, 18 paragraf yazı, 10
blok yerleşimi, bir elips ve kafes yüzeyleri var.

Aynı çizimin R14, 2000 ve 2004 ile kaydedilmiş hâlleri
`core/dwg/src/test/resources/` içinde durur ve her testte dördünün aynı sonucu
verdiği denetlenir.
