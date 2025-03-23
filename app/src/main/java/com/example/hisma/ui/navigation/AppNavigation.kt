package com.example.hisma.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hisma.ui.screen.LoginScreen
import com.example.hisma.ui.screen.HomeOwnerScreen
import com.example.hisma.ui.screen.EmailVerificationScreen
import com.example.hisma.ui.screen.ForgotPasswordScreen
import com.example.hisma.ui.screen.RegisterScreen
import com.example.hisma.ui.screen.ProfileBusinessScreen
import com.example.hisma.ui.screen.ManageUsersScreen
import com.example.hisma.ui.screen.ReportsScreen
import com.example.hisma.ui.screen.OilChangesListScreen
import com.example.hisma.ui.screen.RegisterEmployeeScreen
import com.example.hisma.ui.screen.HomeEmployeeScreen
// import com.example.hisma.ui.screen.MisSuscripcionesScreen
import com.example.hisma.ui.screen.SubscriptionRequiredScreen
import com.example.hisma.utils.SubscriptionController
import com.example.hisma.utils.SubscriptionStatus
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import kotlinx.coroutines.launch


sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Login : Screen("login_screen")
    object HomeOwner : Screen("home_owner_screen")
    object HomeEmployee : Screen("home_employee_screen")

    object Register : Screen("register_screen")
    object EmailVerification : Screen("email_verification_screen")
    object ForgotPassword : Screen("forgot_password_screen")

    object ProfileBusiness : Screen("profile_business_screen")
    object ManageUsers : Screen("manage_users_screen")
    object Reports : Screen("reports_screen")
    object OilChangesList : Screen("oil_changes_list_screen")
    object RegisterEmployee : Screen("register_employee")
    object MisSuscripciones : Screen("mis_suscripciones_screen")
    object SubscriptionRequired : Screen("subscription_required_screen")



}

@Composable
fun AppNavigation(
    subscriptionController: SubscriptionController,
    subscriptionStatus: SubscriptionStatus?
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var currentSubscriptionStatus by remember { mutableStateOf(subscriptionStatus) }

    // Verificar suscripción al cambiar entre pantallas
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route != Screen.Login.route &&
                destination.route != Screen.Register.route &&
                destination.route != Screen.SubscriptionRequired.route &&
                destination.route != Screen.MisSuscripciones.route &&
                destination.route != Screen.EmailVerification.route &&
                destination.route != Screen.ForgotPassword.route) {

                scope.launch {
                    currentSubscriptionStatus = subscriptionController.checkSubscriptionStatus()

                    // Si no tiene suscripción activa, redirigir a pantalla de suscripción
                    if (currentSubscriptionStatus != SubscriptionStatus.ACTIVE &&
                        currentSubscriptionStatus != SubscriptionStatus.ACTIVE_TRIAL &&
                        currentSubscriptionStatus != SubscriptionStatus.LOW_CHANGES &&
                        currentSubscriptionStatus != SubscriptionStatus.EXPIRING_SOON) {

                        navController.navigate(Screen.SubscriptionRequired.route) {
                            popUpTo(destination.id) { inclusive = true }
                        }
                    }
                }
            }
        }

        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(navController)
        }

        composable(Screen.Register.route) {
            RegisterScreen(navController)
        }

        composable(Screen.EmailVerification.route) {
            EmailVerificationScreen(navController)
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(navController)
        }

        composable(Screen.HomeOwner.route) {
            HomeOwnerScreen(navController)
        }

        composable(Screen.HomeEmployee.route) {
            HomeEmployeeScreen(navController)
        }

        composable(Screen.ProfileBusiness.route) {
            ProfileBusinessScreen(navController)
        }

        composable(Screen.ManageUsers.route) {
            ManageUsersScreen(navController)
        }

        composable(Screen.Reports.route) {
            ReportsScreen(navController)
        }

        composable(Screen.OilChangesList.route) {
            OilChangesListScreen(navController)
        }


        composable(Screen.SubscriptionRequired.route) {
            SubscriptionRequiredScreen(navController)
        }
    }
}
