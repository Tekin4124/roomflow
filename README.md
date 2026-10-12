<p align="center"><img src="web-display/assets/favicon.svg" width="96" alt="RoomFlow takvim logosu"></p>

# RoomFlow

**Tablet ve web için açık kaynak toplantı odası takvim ekranı.**

RoomFlow, oda kapısındaki büyük tablet veya kioskta odanın müsaitlik durumunu, mevcut toplantıyı ve sıradaki rezervasyonu gösterir. Tek geliştirici tarafından geliştirildi; native Android uygulaması, web ekranı, oda yönetim paneli ve Microsoft Graph entegrasyonu kaynakları birlikte paylaşılır.

[Android APK indir](https://github.com/Tekin4124/roomflow/releases/latest) · [Kurulum](#web-ve-yönetim-panelini-çalıştır) · [Katkıda bulun](CONTRIBUTING.md) · [MIT](LICENSE)

## Özellikler

- Müsait/meşgul durumu, mevcut toplantı, sıradaki toplantı ve boş süre
- Günlük toplantı akışı, düzenleyen kişi, saat ve doluluk istatistikleri
- Büyük tabletlerde yatay iki panel düzeni; küçük ekranlarda uyarlanabilir yerleşim
- Native Android kiosk görünümü, web ekranı ve bağlantı ayarları
- Oda ekleme/silme, cihaz kaydı ve yönetim paneli
- Microsoft Graph üzerinden kendi oda takvimini okuma
- İstanbul saat dilimi, saat düzeltmesi ve ayarlanabilir yenileme aralığı

**RoomFlow takvimi gösterir; yeni toplantı rezervasyonu oluşturmaz.**

## APK ile başla

[Releases](https://github.com/Tekin4124/roomflow/releases/latest) sayfasından **RoomFlow.apk** indir. Android 6.0 ve üzeri gerekir. Uygulama örnek toplantılarla açılır; hesap, API anahtarı veya sunucu istemez.

**Dolu**, **Müsait** ve **Boş takvim** düğmeleriyle farklı durumları inceleyebilirsin. **Ayarlar** ekranından kendi backend ve oda bağlantını yapılandırabilirsin. Büyük tabletlerde yatay kiosk görünümü önceliklidir; telefon desteği ikincildir.

Bu depo uygulamanın açık kaynak kodudur. Başlangıç kurulumu **örnek veri modu** kullanır; Microsoft hesabına erişmez. Kendi takvimine bağlamak için Graph sağlayıcısını etkinleştirmen gerekir.

## Web ve yönetim panelini çalıştır

Node.js 24 veya üzeri:

```sh
git clone https://github.com/Tekin4124/roomflow.git
cd roomflow
npm ci
npm start
```

| Ekran | Adres |
| --- | --- |
| Oda takvimi | [localhost:8088](http://localhost:8088) |
| Oda ve cihaz yönetimi | [localhost:3000/admin](http://localhost:3000/admin) |

Portlar doluysa:

```sh
BACKEND_PORT=3016 WEB_PORT=8089 npm start
```

İki servis varsayılan olarak yalnız `127.0.0.1` üzerinde dinler. Örnek kurulum kendini hazırlar; API anahtarı girmen gerekmez. Atlas ve Orion odaları ile toplantılar hayalidir. Örnek oda/cihaz kayıtları ignored `backend/.data/demo/` dizininde saklanır. Sıfırlamak için servisleri durdurup yalnız bu dizini kaldır.

Android örnek senaryoları yereldir; webdeki senaryolarla eşzamanlı değildir. Cihaz kaydı bulunan bir odayı silmeden önce yönetim panelinden ilgili cihaz kaydını kaldırmalısın. Cihazın oda ataması ekranın kendi ayarlarından yapılır.

## Android'i derle

JDK 17 ve Android SDK 34 ile:

```sh
cd mobile-android
./gradlew testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Deneme APK'sı Android tooling tarafından üretilen yerel debug anahtarıyla imzalanır. Kendi dağıtımın için release imzası kullan; anahtarlarını Git'e ekleme. `com.roomflow.demo` paket adı teknik uyumluluk için korunur.

## Kendi Microsoft takvimine bağla

```sh
cp backend/.env.example backend/.env
```

Şablondaki `CALENDAR_PROVIDER=graph` ile kendi `API_KEY`, `TENANT_ID`, `CLIENT_ID`, `CLIENT_SECRET` ve `ROOMS` değerlerini tanımla. Microsoft tenant'ında Graph takvim uygulama izinleri ve yönetici onayı gerekir. Başka bir kuruluşun hesap veya oda bilgisi bu depoda bulunmaz.

Graph modunda gerekli değerler yoksa veya örnek anahtar yeniden kullanılırsa backend başlamaz. Web backend'i farklı bir yerdeyse web servisinin `BACKEND_URL` değişkenini yapılandır. Proxy yalnız sunucuda seçilen backend'e erişir; kullanıcı serbest hedef adresiyle proxy oluşturamaz.

Android gerçek bağlantısında HTTPS kullan. Yerel HTTP yalnız `localhost`, `127.0.0.1` ve emulator host `10.0.2.2` için izinlidir. USB testinde `adb reverse tcp:3000 tcp:3000` kullanılabilir. Android bağlantı ayarlarını şifreli preferences'ta, web ise o tarayıcının local storage'ında tutar. Ortak kullanılan kioskta gerçek hesapla test sonrası tarayıcı verilerini temizle.

## Kaynak yapısı ve testler

```text
mobile-android/  Kotlin + Compose uygulama ve Android testleri
web-display/     Oda ekranı, responsive CSS ve kısıtlı HTTP proxy
backend/         Express API, Graph/örnek takvim sağlayıcıları, yönetim paneli
tools/           Web ve backend'i birlikte başlatan launcher
tests/           API entegrasyon ve takvim sınır testleri
.github/         Node ve Android build/test akışları
```

```sh
npm test
```

Yerel doğrulamada **9 Node testi ve 5 Android testi** geçti; APK derlendi. Fiziksel telefonda yatay ekran, kaydırma ve kontrol alanı kontrol edildi. Gerçek Microsoft tenant bağlantısı ve farklı büyük tablet donanımları ayrıca test edilmelidir.

## Yayınlama ve veri sınırı

Başlangıçtaki `local-demo-key` belgelenmiş bir örnek değerdir; internet ortamında erişim koruması sayılmaz. VPS üzerinde herkese açık deneme için ayrı, sadece uydurma veri kullanan bir kurulum tasarla; gerçek takvimi veya yönetim/cihaz mutasyon uçlarını korumasız yayımlama. Gerçek kurulum kendi anahtarı, HTTPS ve erişim kontrolüyle işletilmelidir.

Paylaşılan odalar, toplantılar ve e-postalar hayalidir. Gerçek kurum logosu, çalışan kayıtları, servis hesapları, ortam dosyaları, takvim yedekleri ve özel depoların Git geçmişi yayına dahil edilmez.

## Lisans ve katkı

**[MIT](LICENSE)** lisansıyla paylaşılır. İnceleyebilir, değiştirebilir ve kendi tablet/kiosk projelerinde kullanabilirsin. Üçüncü taraf bağımlılıkları kendi lisanslarına tabidir.

[CONTRIBUTING.md](CONTRIBUTING.md) · [SECURITY.md](SECURITY.md)

Geliştirici: [Tekin4124](https://github.com/Tekin4124)
