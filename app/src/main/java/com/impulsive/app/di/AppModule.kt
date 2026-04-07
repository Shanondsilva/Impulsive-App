package com.impulsive.app.di

import androidx.room.Room
import com.impulsive.app.backup.BackupManager
import com.impulsive.app.data.db.AppDatabase
import com.impulsive.app.data.repository.ImpulsiveRepository
import com.impulsive.app.eval.EvalExporter
import com.impulsive.app.viewmodel.FocusViewModel
import com.impulsive.app.viewmodel.InterceptViewModel
import com.impulsive.app.viewmodel.OnboardingViewModel
import com.impulsive.app.viewmodel.RelapseViewModel
import com.impulsive.app.viewmodel.WeeklyCheckInViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "impulsive.db"
        ).fallbackToDestructiveMigration().build()
    }

    single { get<AppDatabase>().userProfileDao() }
    single { get<AppDatabase>().triggerLogDao() }
    single { get<AppDatabase>().weeklyTargetDao() }
    single { get<AppDatabase>().evalMetricsDao() }
    single { get<AppDatabase>().bypassEventDao() }

    single {
        ImpulsiveRepository(
            userProfileDao  = get(),
            triggerLogDao   = get(),
            weeklyTargetDao = get(),
            evalMetricsDao  = get(),
            bypassEventDao  = get()
        )
    }

    // Phase 5: export + backup
    single { EvalExporter(androidContext(), get()) }
    single { BackupManager(androidContext()) }

    viewModel { OnboardingViewModel(get()) }
    viewModel { InterceptViewModel(get()) }
    viewModel { RelapseViewModel(get()) }
    viewModel { FocusViewModel(get()) }
    viewModel { WeeklyCheckInViewModel(get()) }
}
