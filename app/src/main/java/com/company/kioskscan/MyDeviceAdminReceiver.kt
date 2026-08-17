package com.company.kioskscan

import android.app.admin.DeviceAdminReceiver

/**
 * Receiver ที่จำเป็นสำหรับการเป็น Device Owner
 * ไม่ต้องมี logic พิเศษ แค่มีไว้ให้ระบบ bind ได้
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver()
