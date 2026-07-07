package com.shikhi.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shikhi.app.ui.home.HomeScreen
import com.shikhi.app.ui.lesson.LessonScreen

@Composable
fun ShikhiNavHost() {
	val nav = rememberNavController()
	NavHost(navController = nav, startDestination = "home") {
		composable("home") {
			HomeScreen(onOpenLesson = { lessonId -> nav.navigate("lesson/$lessonId") })
		}
		composable("lesson/{lessonId}") {
			LessonScreen(onExit = { nav.popBackStack() })
		}
	}
}
