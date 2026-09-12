# İmza anahtarı

`pafta-imza.p12.enc` — PAFTA'nın kurulum dosyalarını imzalayan anahtar,
**şifrelenmiş** hâlde.

## Neden var

Android, kurulu bir uygulamanın üzerine **farklı bir anahtarla** imzalanmış
sürümü kurmayı reddeder. Her derlemenin kendi ürettiği geçici anahtarla
imzalandığı sürece güncelleme tuşu indirir, kurulum ekranı açılır ve Android
"uygulama yüklenmedi" der. Bu dosya, bütün derlemelerin aynı anahtarla
imzalanmasını sağlıyor.

## Neden şifreli

Depo şu an herkese açık. Anahtarın kendisi burada düz dursaydı, herhangi biri
Android'in "PAFTA'nın güncellemesi" diye kabul edeceği bir dosya
imzalayabilirdi. Şifreli hâli tek başına işe yaramaz.

Şifre GitHub'da **PAFTA_IMZA_SIFRESI** adlı gizli değerde duruyor; depoda,
derleme kaydında veya kurulum dosyasının içinde hiçbir yerde geçmiyor.
Derleme sırasında yalnızca GitHub'ın kendi bilgisayarında, geçici bir dosyaya
çözülüyor.

## Kaybolursa

Anahtar kaybolursa yenisi üretilir, ama o noktada **kurulu PAFTA'nın elle
kaldırılıp yeniden kurulması gerekir** — Android farklı anahtarı üzerine
yazdırmaz. Bu yüzden şifreyi bir yere not et.

- Tür: PKCS12, RSA 4096 bit, 30 yıl geçerli
- Takma ad: `pafta`
- Anahtarın parmak izi (SHA-256):
  `E2:C1:01:B0:56:43:5D:C6:21:0A:42:4C:45:10:F6:E9:E3:C6:07:69:02:49:74:B8:48:5D:E3:05:4E:BB:3D:CB`
