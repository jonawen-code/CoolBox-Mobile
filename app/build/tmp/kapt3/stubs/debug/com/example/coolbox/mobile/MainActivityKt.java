package com.example.coolbox.mobile;

import java.lang.System;

@kotlin.Metadata(mv = {1, 8, 0}, k = 2, d1 = {"\u0000*\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u001a:\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00010\u00072\u0012\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00010\tH\u0007\u001a\u0010\u0010\n\u001a\u00020\u00012\u0006\u0010\u000b\u001a\u00020\fH\u0007\u001a\u001e\u0010\r\u001a\u00020\u00012\u0006\u0010\u0004\u001a\u00020\u00052\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00010\u0007H\u0007\u001a\u001e\u0010\u000f\u001a\u00020\u00012\f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00010\u00072\u0006\u0010\u0004\u001a\u00020\u0005H\u0007\u00a8\u0006\u0011"}, d2 = {"EditFoodDialog", "", "result", "Lcom/example/coolbox/mobile/util/ParsedResult;", "viewModel", "Lcom/example/coolbox/mobile/MainViewModel;", "onDismiss", "Lkotlin/Function0;", "onConfirm", "Lkotlin/Function1;", "FoodItemCard", "item", "Lcom/example/coolbox/mobile/data/FoodEntity;", "MainScreen", "onSettingsClick", "SettingsScreen", "onBack", "app_debug"})
public final class MainActivityKt {
    
    @androidx.compose.runtime.Composable
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class, androidx.compose.material.ExperimentalMaterialApi.class})
    public static final void MainScreen(@org.jetbrains.annotations.NotNull
    com.example.coolbox.mobile.MainViewModel viewModel, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onSettingsClick) {
    }
    
    @androidx.compose.runtime.Composable
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    public static final void SettingsScreen(@org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onBack, @org.jetbrains.annotations.NotNull
    com.example.coolbox.mobile.MainViewModel viewModel) {
    }
    
    @androidx.compose.runtime.Composable
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    public static final void EditFoodDialog(@org.jetbrains.annotations.NotNull
    com.example.coolbox.mobile.util.ParsedResult result, @org.jetbrains.annotations.NotNull
    com.example.coolbox.mobile.MainViewModel viewModel, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.example.coolbox.mobile.util.ParsedResult, kotlin.Unit> onConfirm) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void FoodItemCard(@org.jetbrains.annotations.NotNull
    com.example.coolbox.mobile.data.FoodEntity item) {
    }
}