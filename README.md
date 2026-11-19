# 🏃‍♂️ TAPAK — Android GPS Activity & Route Tracker

**TAPAK** adalah aplikasi pelacak aktivitas olahraga untuk **Android** dengan nuansa lokal yang *clean* dan modern. Dirancang untuk mencatat rute secara *real-time* menggunakan **OpenStreetMap (OSMDroid)** tanpa bergantung pada API peta berbayar, lengkap dengan **CI/CD GitHub Actions** untuk menghasilkan file **APK** secara otomatis.

---

## ✨ Fitur Utama

* **🗺️ Peta Rute Real-Time (OSMDroid):** Memantau posisi GPS dan menggambar garis jalur (*polyline*) secara langsung saat berolahraga.
* **⚡ Statistik Langsung (Live Stats):** Menghitung **Durasi**, **Jarak (km)**, **Pace rata-rata (/km)**, dan **Estimasi Kalori (kkal)** secara otomatis.
* **🏅 Multi-Mode Olahraga:** Mendukung kategori **Lari**, **Gowes**, dan **Jalan** dengan perhitungan kalori yang disesuaikan.
* **🎯 Target Mingguan & Linimasa:** Memantau progres target jarak mingguan, melihat riwayat aktivitas tersimpan lokal (`SharedPreferences` + `Gson`), memberi **Kudos**, atau menghapus catatan.
* **🤖 Auto-Build APK (GitHub Actions):** Setiap *push* ke branch `main`/`master` otomatis menjalankan build Gradle dan menghasilkan artefak APK siap unduh.

---

## 🛠️ Teknologi yang Digunakan

* **Bahasa & UI:** Java, XML Layouts, Material Components
* **Peta & Lokasi:** OSMDroid (`org.osmdroid:osmdroid-android`), Android `LocationManager`
* **Penyimpanan Lokal:** `SharedPreferences` & Gson (`com.google.code.gson:gson`)
* **Build & Otomatisasi:** Gradle, JDK 17, Android SDK (`compileSdk 35` / `targetSdk 35`), GitHub Actions

---

## 📁 Struktur Project

```text
TAPAK-App/
├── .github/
│   └── workflows/
│       └── build-apk.yml          # Workflow build APK otomatis di GitHub
├── app/
│   ├── build.gradle               # Konfigurasi SDK & dependencies
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/tapak/tracker/
│           │   ├── MainActivity.java
│           │   ├── ActivityModel.java
│           │   └── ActivityAdapter.java
│           └── res/
│               ├── layout/
│               │   ├── activity_main.xml
│               │   └── item_activity.xml
│               └── values/
│                   └── strings.xml
├── build.gradle
└── settings.gradle
```

---

## 🚀 Cara Mendapatkan File APK via GitHub Actions

1. *Push* seluruh kode project ke repositori GitHub ini (branch `main` atau `master`).
2. Buka tab **Actions** di halaman repositori GitHub.
3. Pilih workflow **Build TAPAK Android APK** yang sedang berjalan atau sudah selesai (tanda centang hijau).
4. Gulir ke bagian **Artifacts**, lalu unduh paket **`TAPAK-App-Debug`**.
5. Ekstrak file ZIP tersebut di perangkat Android kamu, lalu instal **`app-debug.apk`**.

---

## 📱 Catatan Penggunaan di Perangkat Android

* **Izin Lokasi & Internet:** Pastikan izin **Lokasi (GPS)** dan **Internet** diaktifkan agar titik koordinat serta ubin peta OpenStreetMap dapat dimuat dengan baik.
* **Android Versi Terbaru:** Untuk pelacakan yang lebih stabil saat layar mati/latar belakang, atur izin lokasi aplikasi ke **Izinkan sepanjang waktu** (*Allow all the time*) pada pengaturan perangkat jika diperlukan.

---

## ⚙️ Parameter yang Mudah Disesuaikan

* **Target Jarak Mingguan:** Ubah konstanta `WEEKLY_GOAL_KM` di dalam `MainActivity.java`.
* **Interval Pembaruan GPS:** Sesuaikan nilai `minTimeMs` dan `minDistanceM` pada pemanggilan `requestLocationUpdates(...)` di `MainActivity.java`.
* **Nama Paket Aplikasi:** Ubah `applicationId` pada file `app/build.gradle` jika ingin membuat varian paket sendiri.

---
