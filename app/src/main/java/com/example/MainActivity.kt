package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.AiQuestionGenerator
import com.example.data.repository.CbtRepository
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.firebase.signOutGoogle
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExamBrowserScreen
import com.example.ui.screens.ExamDetailScreen
import com.example.ui.screens.HomeHubScreen
import com.example.ui.screens.StudentEntryScreen
import com.example.ui.screens.StudentExamScreen
import com.example.ui.screens.TeacherDashboardScreen
import com.example.ui.theme.CbtNusantaraTheme
import com.example.ui.viewmodel.CbtViewModel
import com.example.util.LocalPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_HOME = "home"
private const val ROUTE_TEACHER = "teacher"
private const val ROUTE_EXAM_DETAIL = "exam_detail/{examId}"
private const val ROUTE_STUDENT_ENTRY = "student_entry"
private const val ROUTE_STUDENT_EXAM = "student_exam/{examId}"
private const val ROUTE_EXAM_BROWSER = "exam_browser?url={url}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before UI/ViewModels/Firebase ops
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            CbtNusantaraTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
    } else {
        key(user.uid) {
            AuthenticatedCbtApp(
                currentUserId = user.uid,
                userEmail = user.email ?: "Akun Google Terverifikasi"
            )
        }
    }
}

@Composable
private fun AuthenticatedCbtApp(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()

    val viewModel: CbtViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CbtViewModel(
                    repository = CbtRepository(db),
                    aiGenerator = AiQuestionGenerator(),
                    localPrefs = LocalPreferences(app.applicationContext),
                    currentUserId = currentUserId
                )
            }
        }
    )

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { outerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = outerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME
            ) {
                composable(ROUTE_HOME) {
                    HomeHubScreen(
                        viewModel = viewModel,
                        userEmail = userEmail,
                        onOpenTeacherPanel = { navController.navigate(ROUTE_TEACHER) },
                        onOpenStudentPortal = { navController.navigate(ROUTE_STUDENT_ENTRY) },
                        onQuickStartActiveExam = { examId ->
                            navController.navigate("student_exam/${Uri.encode(examId)}")
                        },
                        onSignOut = {
                            signOutGoogle(credentialManager, scope)
                        }
                    )
                }

                composable(ROUTE_TEACHER) {
                    TeacherDashboardScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenExamDetail = { examId ->
                            navController.navigate("exam_detail/${Uri.encode(examId)}")
                        }
                    )
                }

                composable(
                    route = ROUTE_EXAM_DETAIL,
                    arguments = listOf(navArgument("examId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val examId = backStackEntry.arguments?.getString("examId").orEmpty()
                    ExamDetailScreen(
                        examId = examId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onPreviewAsStudent = { id ->
                            navController.navigate("student_exam/${Uri.encode(id)}")
                        }
                    )
                }

                composable(ROUTE_STUDENT_ENTRY) {
                    StudentEntryScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenCloudExam = { examId ->
                            navController.navigate("student_exam/${Uri.encode(examId)}")
                        },
                        onOpenExternalBrowser = { url ->
                            navController.navigate("exam_browser?url=${Uri.encode(url)}")
                        }
                    )
                }

                composable(
                    route = ROUTE_STUDENT_EXAM,
                    arguments = listOf(navArgument("examId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val examId = backStackEntry.arguments?.getString("examId").orEmpty()
                    StudentExamScreen(
                        examId = examId,
                        viewModel = viewModel,
                        onExit = { navController.popBackStack() }
                    )
                }

                composable(
                    route = ROUTE_EXAM_BROWSER,
                    arguments = listOf(
                        navArgument("url") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    val url = backStackEntry.arguments?.getString("url").orEmpty()
                    ExamBrowserScreen(
                        url = url,
                        onExit = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
