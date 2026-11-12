[English](README.md)

# آوا — موزیک‌پلیر آفلاین و حرفه‌ای اندروید

یک موزیک‌پلیر کاملاً آفلاین برای اندروید. بدون حساب کاربری، بدون اینترنت، بدون تبلیغ و بدون ردیابی — فقط موزیک شما.

[![Release](https://img.shields.io/github/v/release/amirlwf/ava)](https://github.com/amirlwf/ava/releases)
[![Build](https://github.com/amirlwf/ava/actions/workflows/android.yml/badge.svg)](https://github.com/amirlwf/ava/actions/workflows/android.yml)
[![Downloads](https://img.shields.io/github/downloads/amirlwf/ava/total)](https://github.com/amirlwf/ava/releases)

## آوا چیست؟

آوا یک موزیک‌پلیر آفلاین و حرفه‌ای برای اندروید است که با Kotlin و Jetpack Compose و Media3 و Room و DataStore ساخته شده. موزیک‌هایی را پخش می‌کند که همین حالا روی گوشی شماست — بدون استریم، بدون حساب کاربری، بدون تبلیغ.

## امکانات

- 🎵 **کتابخانه** — اسکن MediaStore با بخش‌های آهنگ‌ها، خواننده‌ها، آلبوم‌ها، ژانرها و پوشه‌ها
- ▶️ **پخش پس‌زمینه** — با MediaSession و کنترل پخش در اعلان و صفحه قفل
- 📋 **صف پخش** — جابه‌جایی با کشیدن و رها کردن، پخش تصادفی، تکرار (خاموش / همه / یکی)، سرعت ۰٫۷۵ تا ۲ برابر
- 🎤 **متن ترانه همگام** — فایل‌های LRC با خط‌های برجسته و دکمه دنبال‌کردن
- 😴 **تایمر خواب** — زمان‌های آماده و دلخواه، با محو تدریجی صدا در ۱۰ ثانیه آخر
- 🎚️ **اکولایزر** — اکولایزر سیستمی با حالت‌های آماده و تنظیم هر باند
- 🎨 **تم‌ها** — تیره، روشن، سیستمی و AMOLED با رنگ‌های متنوع
- 🌐 **زبان‌ها** — فارسی کامل (راست‌به‌چپ) و انگلیسی (چپ‌به‌راست)، قابل تغییر داخل برنامه
- 🔄 **ادامه پخش** — از همان جایی که مانده‌اید ادامه می‌دهد، با کل صف پخش

## دانلود

| نسخه | فایل | حجم |
| ---- | ---- | --- |
| [v0.7.1](https://github.com/amirlwf/ava/releases/tag/v0.7.1) | [ava-0.7.1-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.7.1/ava-0.7.1-debug.apk) | ۵۸ مگابایت |
| [v0.7.0](https://github.com/amirlwf/ava/releases/tag/v0.7.0) | [ava-0.7.0-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.7.0/ava-0.7.0-debug.apk) | ۵۷ مگابایت |
| [v0.6.0](https://github.com/amirlwf/ava/releases/tag/v0.6.0) | [ava-0.6.0-debug.apk](https://github.com/amirlwf/ava/releases/latest/download/ava-0.6.0-debug.apk) | ۵۷ مگابایت |
| [v0.5.7](https://github.com/amirlwf/ava/releases/tag/v0.5.7) | [ava-0.5.7-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.5.7/ava-0.5.7-debug.apk) | ۵۷ مگابایت |

## نصب

۱. فایل APK را از جدول بالا دانلود کنید.
۲. آن را روی گوشی باز کنید و نصب کنید. اگر مرورگر نصب را مسدود کرد، «نصب برنامه‌های ناشناس» را برای آن مجاز کنید.
۳. در اولین اجرا، دسترسی **موسیقی و صدا** را بدهید تا آوا بتواند آهنگ‌های شما را اسکن کند.
۴. دسترسی **اعلان‌ها** را بدهید اگر می‌خواهید کنترل پخش در نوار اعلان و صفحه قفل باشد.

## ساخت از سورس

پیش‌نیازها: JDK 17 و Android SDK 36 (‏compileSdk ‏۳۶؛ ‏targetSdk ‏۳۴ می‌ماند).

```bash
./gradlew assembleDebug
```

فایل APK در `app/build/outputs/apk/debug/app-debug.apk` ساخته می‌شود.

اگر Gradle در محیط شما اجرا نمی‌شود، اسکریپت `scripts/manual-build.sh` همان APK دیباگ را به‌صورت دستی می‌سازد (aapt2، Room KSP، kotlinc، d8، zipalign، apksigner).

## وضعیت پروژه

**نسخه ۰٫۷٫۱ آیکون اختصاصی هارپر است.** آیکون لانچر حالا طرح خود هارپر است (دختر انیمه‌ای با نت‌های درخشان روی مشکی)، گرد برش‌خورده: قرمز پیش‌فرض است و بنفش و سبز از تنظیمات قابل انتخاب‌اند. همه‌چیز از ۰٫۷٫۰ سر جایش است: هشت تم کامل و کاملاً متفاوت (بهار، تابستان، پاییز، زمستان، زمردی، بنفش، اقیانوس، غروب) — هر کدام با پالت، گرادیان، شیپ و سطوح خودش — قابل انتخاب در تنظیمات و اعمال‌شده در کل اپ، شامل اکسنت نوتیفیکیشن. **هیچ فونت سیستمی در هیچ‌جای اپ نیست:** همه استایل‌های متن، دیالوگ‌ها، منوها و فیلدها فقط از فونت‌های خود اپ استفاده می‌کنند (وزیرمتن برای بدنه + لاله‌زار نمایشی). **آمار گوش دادن:** صفحه جدید با پرگوش‌ترین آهنگ‌ها، خواننده‌ها، آلبوم‌ها و ژانرها، زمان گوش دادن (امروز / این هفته / این ماه)، روند ۷ روز گذشته و عادت‌ها — همه روی خود گوشی و از جدول جدید تاریخچه گوش دادن (Room نسخه ۳) محاسبه می‌شود. **موشن:** ترنزیشن shared-element (کارت آهنگ → صفحه در حال پخش)، skeleton loading، haptics، انیمیشن‌های spring و ترنزیشن انیمیشنی عوض کردن تم. منشور هنوز باندل نشده (پولی است و خریده نشده)؛ دانا هم بیرون است. همچنان یک نسخه تشخیصی است: اگر به کرش خوردید، لطفاً فایل `Downloads/Ava/ava-crash-log.txt` یا `Downloads/Ava/ava-startup-trace.txt` را بفرستید — یا از دکمه «اشتراک‌گذاری لاگ کرش» در حالت امن استفاده کنید. این بیلد هنوز روی دستگاه واقعی تست نشده است.

## مشارکت

گزارش مشکل و pull request خوش‌آمد است. لطفاً تغییرها را متمرکز نگه دارید و بنویسید چه چیزی را تست کرده‌اید.

## قدردانی

- [Media3 / ExoPlayer](https://developer.android.com/media/media3) برای پخش
- [Jetpack Compose](https://developer.android.com/jetpack/compose) و [Room](https://developer.android.com/training/data-storage/room) برای رابط کاربری و لایه داده
- فونت [Vazirmatn](https://github.com/rastikerdar/vazirmatn) با لایسنس SIL Open Font License
- فونت [Lalezar](https://github.com/BornaIz/Lalezar) با لایسنس SIL Open Font License

## لایسنس

سورس کد همان‌طور که هست، برای استفاده شخصی ارائه می‌شود. فونت‌های Vazirmatn و Lalezar تحت لایسنس SIL Open Font License هستند.
