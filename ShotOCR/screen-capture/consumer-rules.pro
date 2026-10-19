# UserService 由 Shizuku 通过类名加载，需避免 R8 重命名。
-keep class org.caojun.screen.capture.ScreenCaptureService { *; }
-keep class org.caojun.screen.capture.IShizukuScreen$Stub { *; }
-keep interface org.caojun.screen.capture.IShizukuScreen { *; }
