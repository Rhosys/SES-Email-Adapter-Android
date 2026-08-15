package ch.rhosys.email.di

import android.content.Context
import androidx.room.Room
import ch.rhosys.email.BuildConfig
import ch.rhosys.email.data.auth.AuthressCookieJar
import ch.rhosys.email.data.auth.AuthressLoginClient
import ch.rhosys.email.data.auth.TokenStore
import ch.rhosys.email.data.local.EmailDatabase
import ch.rhosys.email.data.log.AppLogger
import ch.rhosys.email.data.remote.api.ApiLoggingInterceptor
import ch.rhosys.email.data.remote.api.AuthInterceptor
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.api.UserAgentInterceptor
import ch.rhosys.email.data.repository.AccountRepositoryImpl
import ch.rhosys.email.data.repository.ComposeRepositoryImpl
import ch.rhosys.email.data.repository.LabelRepositoryImpl
import ch.rhosys.email.data.repository.RuleRepositoryImpl
import ch.rhosys.email.data.repository.SettingsRepository
import ch.rhosys.email.data.repository.StatsRepository
import ch.rhosys.email.data.repository.TemplateRepositoryImpl
import ch.rhosys.email.data.repository.ThreadRepositoryImpl
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ComposeRepository
import ch.rhosys.email.domain.repository.LabelRepository
import ch.rhosys.email.domain.repository.RuleRepository
import ch.rhosys.email.domain.repository.TemplateRepository
import ch.rhosys.email.domain.repository.ThreadRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual dependency graph (decision #76: no DI framework). Built once in
 * [ch.rhosys.email.EmailApp.onCreate] and threaded down through Compose via
 * [ch.rhosys.email.di.LocalAppContainer].
 */
class AppContainer(private val context: Context) {

    /** Long-lived scope for background writes (log entries) that must outlive any single screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tokenStore: TokenStore by lazy { TokenStore(context) }

    val appLogger: AppLogger by lazy { AppLogger(database.logDao(), appScope) }

    private val cookieJar: AuthressCookieJar by lazy {
        AuthressCookieJar(context, BuildConfig.AUTHRESS_CUSTOM_DOMAIN)
    }

    val authManager: AuthressLoginClient by lazy {
        AuthressLoginClient(context, cookieJar, authHttpClient, appLogger)
    }

    private val moshi: Moshi by lazy {
        // SignalDto is a polymorphic union discriminated by `type`; Moshi needs the
        // factory to pick the concrete variant before deserializing.
        Moshi.Builder()
            .add(ch.rhosys.email.data.remote.dto.SignalDtoAdapter.Factory)
            .build()
    }

    /**
     * Authress's own calls must never carry [AuthInterceptor]: Authress sessions
     * live in cookies, not a bearer token, so the interceptor has nothing to add
     * there — it exists for the Email API client below.
     */
    private val authHttpClient: OkHttpClient by lazy {
        // No debug HttpLoggingInterceptor here: AuthressLoginClient.execute() already
        // logs method/path/status/duration through AppLogger for every Authress call,
        // in both debug and release, making it redundant.
        OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * For the Email API only. Adds bearer auth (from whatever token is cached right
     * now — see [AuthInterceptor], deliberately non-blocking) and the same
     * timing-log visibility Authress calls get, plus debug body logging.
     */
    private val okHttpClient: OkHttpClient by lazy {
        authHttpClient.newBuilder()
            .addInterceptor(AuthInterceptor { authManager.getToken() })
            .addInterceptor(ApiLoggingInterceptor(appLogger))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .build()
    }

    val apiService: EmailApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(EmailApiService::class.java)
    }

    val database: EmailDatabase by lazy {
        // The v1 schema described an API that does not exist, so there is nothing
        // worth migrating — the cache simply refetches against the real one.
        Room.databaseBuilder(context, EmailDatabase::class.java, EmailDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepositoryImpl(apiService, database.accountDao(), tokenStore)
    }

    val threadRepository: ThreadRepository by lazy {
        ThreadRepositoryImpl(apiService, database, appLogger)
    }

    val composeRepository: ComposeRepository by lazy {
        ComposeRepositoryImpl(apiService, database.signalDao())
    }

    val labelRepository: LabelRepository by lazy {
        LabelRepositoryImpl(apiService, database.labelDao())
    }

    val ruleRepository: RuleRepository by lazy {
        RuleRepositoryImpl(apiService, database.ruleDao())
    }

    val templateRepository: TemplateRepository by lazy {
        TemplateRepositoryImpl(apiService, database.templateDao())
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(apiService) }
    val statsRepository: StatsRepository by lazy { StatsRepository(apiService) }

    val preferencesStore: ch.rhosys.email.data.local.PreferencesStore by lazy {
        ch.rhosys.email.data.local.PreferencesStore(context)
    }
}
