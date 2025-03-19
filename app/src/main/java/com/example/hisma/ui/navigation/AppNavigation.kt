package com.example.hisma.ui.navigation

import androidx.compose.runtime.Composable
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


// etc.

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



}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login_screen") {
        composable("login_screen") {
            LoginScreen(navController)
        }
        composable("home_owner_screen") {
            HomeOwnerScreen(navController)
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

       // más rutas................................


        composable(Screen.RegisterEmployee.route) {
            RegisterEmployeeScreen(navController) // Registro de empleado
        }

        composable(Screen.HomeEmployee.route) {
            HomeEmployeeScreen(navController)
        }

    }
}
