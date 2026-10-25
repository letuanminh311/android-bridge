plugins {
    id("com.android.library")
}

android {
    namespace = "com.demo.bytebridge.lib"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // để trống: AAR không phụ thuộc thư viện ngoài
}