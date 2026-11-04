# Shizuku 通过类名加载 UserService，需避免 R8 重命名。
-keep class org.caojun.shizuku.automation.ShizukuAutomationService { *; }
-keep class org.caojun.shizuku.automation.IShizukuAutomation$Stub { *; }
-keep interface org.caojun.shizuku.automation.IShizukuAutomation { *; }
