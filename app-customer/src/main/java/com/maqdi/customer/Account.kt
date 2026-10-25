package com.maqdi.customer

import androidx.compose.runtime.Composable
import com.maqdi.core.Profile
import com.maqdi.core.ProfileScreen

/** حساب الزبون: الملف الشخصي المشترك مع حقل العنوان الافتراضي */
@Composable
fun AccountScreen(profile: Profile) = ProfileScreen(profile, showAddress = true)
