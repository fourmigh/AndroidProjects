# JNI 入口由 System.loadLibrary 调用，禁止混淆 native 方法所在类
-keep class org.caojun.ai.translate.hub.llama.LlamaBridge { *; }
