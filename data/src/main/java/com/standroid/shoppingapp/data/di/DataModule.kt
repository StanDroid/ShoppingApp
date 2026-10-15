package com.standroid.shoppingapp.data.di

import android.content.Context
import androidx.room.Room
import com.standroid.shoppingapp.data.local.AppDatabase
import com.standroid.shoppingapp.data.local.dao.FavoritesDao
import com.standroid.shoppingapp.data.remote.api.ProductApi
import com.standroid.shoppingapp.data.repository.FavoritesRepositoryImpl
import com.standroid.shoppingapp.data.repository.ProductRepositoryImpl
import com.standroid.shoppingapp.domain.repository.FavoritesRepository
import com.standroid.shoppingapp.domain.repository.ProductRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = createProductOkHttpClient()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = createProductRetrofit(client)

    @Provides
    @Singleton
    fun provideProductApi(retrofit: Retrofit): ProductApi = retrofit.create(ProductApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideFavoritesDao(database: AppDatabase): FavoritesDao = database.favoritesDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindProductRepository(implementation: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        implementation: FavoritesRepositoryImpl
    ): FavoritesRepository
}
