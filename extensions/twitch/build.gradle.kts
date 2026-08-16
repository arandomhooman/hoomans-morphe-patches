import com.android.build.api.dsl.ApplicationExtension

configure<ApplicationExtension> {
    namespace = "app.morphe.extension.twitch"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
