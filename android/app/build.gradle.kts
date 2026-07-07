import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.ksp)
	alias(libs.plugins.hilt)
	alias(libs.plugins.openapi.generator)
}

// MA1 spike (ADR-0012): client generated from the shared contract. Output is inspected
// but not yet compiled into the app; adoption decision is recorded in the ADR.
openApiGenerate {
	generatorName.set("kotlin")
	library.set("jvm-retrofit2")
	inputSpec.set("$rootDir/../docs/43-api-contract.openapi.yaml")
	outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
	packageName.set("com.shikhi.app.generated")
	configOptions.set(
		mapOf(
			"serializationLibrary" to "kotlinx_serialization",
			"useCoroutines" to "true",
			"dateLibrary" to "java8",
		),
	)
}

// Debug API target. The emulator reaches the host machine at 10.0.2.2; for a physical
// device pass -PapiBaseUrl=http://<LAN-IP>:8080/v1/ or use `adb reverse tcp:8080 tcp:8080`.
// Must end with a slash and include the /v1 prefix (Retrofit resolves relative paths).
val debugApiBaseUrl: String = (findProperty("apiBaseUrl") as String?)
	?: "http://10.0.2.2:8080/v1/"

// Hosted backend (chore/deployable-stack, Render free tier — expect cold starts).
val releaseApiBaseUrl: String = (findProperty("releaseApiBaseUrl") as String?)
	?: "https://shikhi-backend.onrender.com/v1/"

val keystoreProps = Properties().apply {
	val f = rootProject.file("keystore.properties")
	if (f.exists()) f.inputStream().use { load(it) }
}

android {
	namespace = "com.shikhi.app"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.shikhi.app"
		minSdk = 26
		targetSdk = 36
		// versionCode = 10000*major + 100*minor + patch (docs/70 §13)
		versionCode = 100
		versionName = "0.1.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	signingConfigs {
		if (keystoreProps.isNotEmpty()) {
			create("release") {
				storeFile = file(keystoreProps.getProperty("storeFile"))
				storePassword = keystoreProps.getProperty("storePassword")
				keyAlias = keystoreProps.getProperty("keyAlias")
				keyPassword = keystoreProps.getProperty("keyPassword")
			}
		}
	}

	buildTypes {
		debug {
			applicationIdSuffix = ".debug"
			buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
		}
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
			signingConfig = signingConfigs.findByName("release")
		}
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
	}
}

dependencies {
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.compose.material3)
	implementation(libs.compose.ui.tooling.preview)
	debugImplementation(libs.compose.ui.tooling)

	implementation(libs.activity.compose)
	implementation(libs.lifecycle.runtime.compose)
	implementation(libs.lifecycle.viewmodel.compose)

	implementation(libs.hilt.android)
	implementation(libs.hilt.navigation.compose)
	ksp(libs.hilt.compiler)

	implementation(libs.coroutines.android)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.retrofit)
	implementation(libs.retrofit.kotlinx.serialization)
	implementation(libs.okhttp)
	debugImplementation(libs.okhttp.logging)

	implementation(libs.datastore.preferences)

	testImplementation(libs.junit)
	testImplementation(libs.mockk)
	testImplementation(libs.coroutines.test)
	testImplementation(libs.turbine)
	testImplementation(libs.mockwebserver)
}
