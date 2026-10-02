package com.hardbasseq.eq.text

import android.content.Context
import androidx.annotation.StringRes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

// Translated texts for code that has no Context (ViewModels, repositories): messages are looked up in
// the current app language when they are created. Tests use a fake instead of Android resources.
interface TextProvider {
    fun get(
        @StringRes id: Int,
        vararg args: Any,
    ): String
}

@Singleton
class AndroidTextProvider
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : TextProvider {
        override fun get(
            id: Int,
            vararg args: Any,
        ): String = context.getString(id, *args)
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class TextModule {
    @Binds
    @Singleton
    abstract fun bindTextProvider(impl: AndroidTextProvider): TextProvider
}
