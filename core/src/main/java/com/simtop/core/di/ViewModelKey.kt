package com.simtop.core.di

import androidx.lifecycle.ViewModel
import kotlin.reflect.KClass
import dev.zacsweers.metro.MapKey

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class ViewModelKey(val value: KClass<out ViewModel>)
