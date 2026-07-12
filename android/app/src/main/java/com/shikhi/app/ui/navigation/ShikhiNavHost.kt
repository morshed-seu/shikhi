package com.shikhi.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shikhi.app.ui.home.HomeScreen
import com.shikhi.app.ui.lesson.LessonScreen
import com.shikhi.app.ui.practice.PracticeScreen
import com.shikhi.app.ui.profile.ProfileScreen

@Composable
fun ShikhiNavHost() {
	val nav = rememberNavController()
	NavHost(navController = nav, startDestination = "home") {
		composable("home") {
			HomeScreen(
				onOpenLesson = { lessonId -> nav.navigate("lesson/$lessonId") },
				onStartPractice = { nav.navigate("practice") },
				onOpenProfile = { nav.navigate("profile") },
			)
		}
		composable("lesson/{lessonId}") {
			LessonScreen(onExit = { nav.popBackStack() })
		}
		composable("practice") {
			PracticeScreen(onExit = { nav.popBackStack() })
		}
		composable("profile") {
			ProfileScreen(onBack = { nav.popBackStack() })
		}
	}
}
