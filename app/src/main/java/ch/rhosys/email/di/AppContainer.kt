package ch.rhosys.email.di

import android.content.Context
import androidx.room.Room
import ch.rhosys.email.BuildConfig
import ch.rhosys.email.data.auth.AuthressAuthManager
import ch.rhosys.email.data.auth.TokenStore
import ch.rhosys.email.data.local.EmailDatabase
import ch.rhosys.email.data.remote.api.AuthInterceptor
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.repository.AccountRepositoryImpl
import ch.rhosys.email.data.repository.AdminRepository
import ch.rhosys.email.data.repository.ComposeRepositoryImpl
import ch.rhosys.email.data.repository.LabelRepositoryImpl
import ch.rhosys.email.data.repository.RuleRepositoryImpl
import ch.rhosys.email.data.repository.SettingsRepository
import ch.rhosys.email.data.repository.StatsRepository
import ch.rhosys.email.data.repository.SupportRepository
import ch.rhosys.email.data.repository.TemplateRepositoryImpl
import ch.rhosys.email.data.repository.ThreadRepositoryImpl
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ComposeRepository
import ch.rhosys.email.domain.repository.LabelRepository
import ch.rhosys.email.domain.repository.RuleRepository
import ch.rhosys.email.domain.repository.TemplateRepository
import ch.rhosys.email.domain.repository.ThreadRepository
import com.squareup.moshi.Moshi
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

    val tokenStore: TokenStore by lazy { TokenStore(context) }
    val authManager: AuthressAuthManager by lazy { AuthressAuthManager(context, tokenStore) }

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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
        Room.databaseBuilder(context, EmailDatabase::class.java, EmailDatabase.NAME).build()
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepositoryImpl(apiService, database.accountDao(), tokenStore)
    }

    val threadRepository: ThreadRepository by lazy {
        ThreadRepositoryImpl(context, apiService, database)
    }

    val composeRepository: ComposeRepository by lazy {
        ComposeRepositoryImpl(apiService, database.draftDao())
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
    val adminRepository: AdminRepository by lazy { AdminRepository(apiService) }
    val supportRepository: SupportRepository by lazy { SupportRepository(apiService) }

    val preferencesStore: ch.rhosys.email.data.local.PreferencesStore by lazy {
        ch.rhosys.email.data.local.PreferencesStore(context)
    }

    val pendingSendManager: ch.rhosys.email.sync.PendingSendManager by lazy {
        ch.rhosys.email.sync.PendingSendManager(context, composeRepository)
    }
}
