# Scan Stock Kiosk App (Device Owner Edition)

แอป Android แบบ Kiosk WebView สำหรับเปิดหน้า
`http://192.168.82.199:8003/Scan_Stock.aspx?fac=f1` เท่านั้น
เวอร์ชันนี้ตั้งค่าให้ทำงานผ่าน **Device Owner Mode** เพื่อล็อคเครื่องแบบเต็มรูปแบบ
และ **ไม่มี popup "App is pinned / No thanks / Got it" โผล่ขึ้นมาเลย**

## ฟีเจอร์
- เปิดแอปมาโหลด URL ที่กำหนดไว้ทันที ไม่มี address bar ให้แก้ไข
- อนุญาตนำทางเฉพาะภายใน host `192.168.82.199` เท่านั้น
- ปุ่ม Back ใช้ย้อนกลับหน้าในเว็บเท่านั้น ไม่ปิดแอป
- เข้า Lock Task Mode แบบเงียบ ไม่มี popup ระบบใดๆ (เพราะเป็น Device Owner)
- ปุ่ม Home / Recent Apps ถูกล็อคอย่างสมบูรณ์ ไม่สามารถหลุดออกจากแอปได้เลยนอกจากผ่าน dialog รหัสผ่าน
- จุดลับ Admin: แตะมุมขวาบนของจอ **5 ครั้งติดกันภายใน 3 วินาที**
  → ใส่รหัสผ่าน → เลือก "ออกจากแอป" หรือ "เปลี่ยนรหัสผ่าน"
- รหัสผ่านเริ่มต้น: `1234` (แก้ได้ที่ `strings.xml` ก่อน build หรือเปลี่ยนในแอปทีหลัง)

## ขั้นตอนที่ 1: Build APK ผ่าน GitHub Actions
1. สร้าง repository ใหม่บน GitHub แล้ว push โฟลเดอร์นี้ทั้งหมดขึ้นไป:
   ```bash
   cd kiosk-app
   git init
   git add .
   git commit -m "Kiosk app with Device Owner support"
   git branch -M main
   git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
   git push -u origin main
   ```
2. เข้าแท็บ **Actions** ใน repo รอ build เสร็จ (3-5 นาที) แล้วดาวน์โหลด artifact **kiosk-scan-apk**

## ขั้นตอนที่ 2: Factory Reset เครื่อง Android
1. สำรองข้อมูลที่ต้องการไว้ก่อน (ข้อมูลทั้งหมดในเครื่องจะถูกลบ)
2. ไปที่ `Settings > System > Reset options > Erase all data (factory reset)`
3. เมื่อเปิดเครื่องใหม่ **อย่าเพิ่งล็อกอินบัญชี Google** และ **อย่าเพิ่งเชื่อมต่อ Wi-Fi ผ่านหน้า setup wizard จนจบ** — ให้ข้าม/หยุดที่หน้าแรกๆ ที่ยังไม่ login บัญชี (บางรุ่นข้าม Wi-Fi ได้เลย บางรุ่นต้องต่อ Wi-Fi แต่ห้าม login Google account เด็ดขาด)

## ขั้นตอนที่ 3: ติดตั้ง APK และตั้งเป็น Device Owner ผ่าน ADB
ต้องมีคอมพิวเตอร์ที่ลง Android Platform Tools (adb) แล้ว

1. เปิด **Developer options > USB debugging** บนเครื่อง (ถ้ายังไม่เห็น Developer options ให้กดที่ `Settings > About phone > Build number` รัว ๆ 7 ครั้ง)
2. เชื่อมต่อเครื่องกับคอมพิวเตอร์ด้วยสาย USB แล้วยืนยัน "Allow USB debugging" บนหน้าจอมือถือ
3. ติดตั้ง APK ที่ build ได้:
   ```bash
   adb install app-debug.apk
   ```
4. ตั้งแอปเป็น Device Owner (ต้องทำตอนที่ **ยังไม่มีบัญชีใดๆ ล็อกอินอยู่ในเครื่องเลย** ไม่งั้นคำสั่งจะ error):
   ```bash
   adb shell dpm set-device-owner com.company.kioskscan/.MyDeviceAdminReceiver
   ```
   ถ้าสำเร็จจะขึ้นข้อความ `Success: Device owner set to package com.company.kioskscan`
5. ถอดสาย USB เปิดแอป Scan Stock Kiosk ขึ้นมา — แอปจะเข้าโหมดล็อคทันทีโดยไม่มี popup ใดๆ

> หลังจากเป็น Device Owner แล้ว ค่อยไปตั้งค่า Wi-Fi และอื่นๆ ที่จำเป็นสำหรับใช้งานจริงได้ตามปกติ
> (Device Owner ไม่ได้ปิดกั้นการตั้งค่าเครือข่าย แค่ล็อคไม่ให้ออกจากแอป kiosk เท่านั้น)

## เรื่อง Signing / Keystore (สำคัญมาก - อ่านก่อนอัปเดตแอป)
โปรเจกต์นี้มี `keystore/kiosk-release.jks` แนบมาด้วย และตั้งค่าให้ **ทั้ง debug และ release build
เซ็นด้วย keystore ไฟล์เดียวกันเสมอ** (`app/build.gradle` -> `signingConfigs.release`)
เหตุผลคือ: ถ้าไม่ fix keystore ไว้ ทุกครั้งที่ GitHub Actions build จะสุ่ม debug keystore ใหม่
(เพราะ CI runner เป็นเครื่องใหม่ทุกรอบ) ทำให้ signature ไม่ตรงกับตัวที่ติดตั้งอยู่ในเครื่อง
และ `adb install -r` จะ fail ด้วย error `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

**สิ่งที่ต้องรู้:**
- อย่าลบหรือเปลี่ยนไฟล์ `keystore/kiosk-release.jks` และรหัสผ่านใน `build.gradle`
  ถ้าเปลี่ยน จะกลับมาเจอปัญหา signature ไม่ตรงอีกกับเครื่องที่ติดตั้ง build เก่าไปแล้ว
- keystore ไฟล์นี้ถูก commit เข้า repo โดยตรงเพื่อความง่าย (เหมาะสำหรับ tool ใช้ภายในองค์กร
  บน private repo) ถ้าต้องการความปลอดภัยสูงขึ้น ค่อยย้ายไปเก็บเป็น GitHub Secret แทนได้ภายหลัง
- APK ที่ build จาก workflow นี้ (`assembleDebug`) ตอนนี้เซ็นด้วย keystore ตัวนี้แล้ว
  ทุก build ถัดไปจาก repo เดียวกันจะอัปเดตทับกันได้ปกติด้วย `adb install -r`

**ถ้าเครื่องมี APK รุ่นเก่า (ที่เซ็นด้วย debug keystore แบบสุ่ม) ติดตั้งอยู่แล้ว ต้องทำ migration ครั้งเดียว:**
1. เปิดแอป เข้าเมนู Admin (แตะมุมขวาบน 5 ครั้ง) ใส่รหัสผ่าน เลือก "ออกจากแอป"
2. `adb shell dpm remove-active-admin com.company.kioskscan/.MyDeviceAdminReceiver`
3. `adb uninstall com.company.kioskscan`
4. `adb install app-debug.apk` (ตัวใหม่ที่เซ็นด้วย keystore คงที่แล้ว)
5. `adb shell dpm set-device-owner com.company.kioskscan/.MyDeviceAdminReceiver`

หลังจากขั้นตอนนี้ครั้งเดียว ตัวถัดๆ ไปจะใช้ `adb install -r app-debug.apk` อัปเดตทับได้เลยตลอดไป

## หมายเหตุสำคัญ
- ถ้าเครื่องเคย login บัญชี Google มาก่อนตอน setup ต้อง factory reset ใหม่อีกรอบแล้วทำตามขั้นตอนใหม่ทั้งหมด (ข้อจำกัดของ Android เอง ไม่ใช่ของแอป)
- ถ้าต้องการยกเลิก Device Owner ภายหลัง (เช่นจะเอาเครื่องไปใช้งานทั่วไป) ใช้เมนู "ออกจากแอป" ในแอปนี้ก่อน แล้วรัน:
  ```bash
  adb shell dpm remove-active-admin com.company.kioskscan/.MyDeviceAdminReceiver
  ```
- ไฟล์ debug APK ใช้งานได้ปกติสำหรับใช้ภายในองค์กร ถ้าต้องการ release APK เซ็นด้วย keystore จริง แจ้งเพิ่มได้

## การปรับแต่ง
- เปลี่ยน URL เป้าหมาย / host ที่อนุญาต / รหัสผ่านเริ่มต้น: แก้ไฟล์ `app/src/main/res/values/strings.xml`
