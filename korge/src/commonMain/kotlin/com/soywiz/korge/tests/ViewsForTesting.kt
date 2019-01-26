package com.soywiz.korge.tests

import com.soywiz.klock.*
import com.soywiz.korag.log.*
import com.soywiz.korev.*
import com.soywiz.korge.*
import com.soywiz.korge.input.*
import com.soywiz.korge.internal.*
import com.soywiz.korge.scene.*
import com.soywiz.korge.view.*
import com.soywiz.korio.async.*
import com.soywiz.korio.lang.*
import com.soywiz.korio.util.*
import com.soywiz.korma.geom.*
import kotlinx.coroutines.*

open class ViewsForTesting(val frameTime: TimeSpan = 10.milliseconds) {
	var time = DateTime(0.0)
	//val dispatcher2 = TestCoroutineDispatcher(frameTime)
	val dispatcher = Dispatchers.Default

	val timeProvider: TimeProvider = object : TimeProvider {
		override fun now(): DateTime = time
	}
	val koruiEventDispatcher = EventDispatcher()
	val viewsLog = ViewsLog(dispatcher, ag = LogAG(DefaultViewport.WIDTH, DefaultViewport.HEIGHT))
	val injector get() = viewsLog.injector
	val ag get() = viewsLog.ag
	val input get() = viewsLog.input
	val views get() = viewsLog.views
	val stats get() = views.stats
	val mouse get() = input.mouse

	suspend fun mouseMoveTo(x: Number, y: Number) {
		koruiEventDispatcher.dispatch(MouseEvent(type = MouseEvent.Type.MOVE, id = 0, x = x.toInt(), y = y.toInt()))
		//views.update(frameTime)
		time += frameTime
		ag.onRender(ag)
		delay(frameTime * 2) // Required because some events launch a coroutine
	}

	suspend fun mouseDown() {
		koruiEventDispatcher.dispatch(
			MouseEvent(
				type = MouseEvent.Type.DOWN,
				id = 0,
				x = mouse.x.toInt(),
				y = mouse.y.toInt(),
				button = MouseButton.LEFT,
				buttons = 1
			)
		)
		time += frameTime
		ag.onRender(ag)
		delay(frameTime * 2) // Required because some events launch a coroutine
	}

	suspend fun mouseUp() {
		koruiEventDispatcher.dispatch(
			MouseEvent(
				type = MouseEvent.Type.UP,
				id = 0,
				x = input.mouse.x.toInt(),
				y = input.mouse.y.toInt(),
				button = MouseButton.LEFT,
				buttons = 0
			)
		)
		time += frameTime
		ag.onRender(ag)
		delay(frameTime * 2) // Required because some events launch a coroutine
	}

	//@Suppress("UNCHECKED_CAST")
	//fun <T : Scene> testScene(
	//	module: Module,
	//	sceneClass: KClass<T>,
	//	vararg injects: Any,
	//	callback: suspend T.() -> Unit
	//) = viewsTest {
	//	//disableNativeImageLoading {
	//	val sc = Korge.test(
	//		Korge.Config(
	//			module,
	//			sceneClass = sceneClass,
	//			sceneInjects = injects.toList(),
	//			container = canvas,
	//			eventDispatcher = eventDispatcher,
	//			timeProvider = TimeProvider { testDispatcher.time })
	//	)
	//	callback(sc.currentScene as T)
	//	//}
	//}

	suspend fun View.simulateClick() {
		this.mouse.onClick(this.mouse)
		ag.onRender(ag)
		delayFrame()
	}

	suspend fun View.simulateOver() {
		this.mouse.onOver(this.mouse)
		ag.onRender(ag)
		delayFrame()
	}

	suspend fun View.simulateOut() {
		this.mouse.onOut(this.mouse)
		ag.onRender(ag)
		delayFrame()
	}

	suspend fun View.isVisibleToUser(): Boolean {
		if (!this.visible) return false
		if (this.alpha <= 0.0) return false
		val bounds = this.getGlobalBounds()
		if (bounds.area <= 0.0) return false
		val module = injector.get<Module>()
		val visibleBounds = Rectangle(0, 0, module.windowSize.width, module.windowSize.height)
		if (!bounds.intersects(visibleBounds)) return false
		return true
	}

	// @TODO: Run a faster eventLoop where timers happen much faster
	fun viewsTest(block: suspend CoroutineScope.() -> Unit): Unit = suspendTest {
		if (OS.isNative) return@suspendTest // @TODO: kotlin-native SKIP NATIVE FOR NOW: kotlin.IllegalStateException: Cannot execute task because event loop was shut down
		Korge.prepareViews(views, koruiEventDispatcher, fixedSizeStep = frameTime)
		val el = viewsLog.animationFrameLoopKorge {
			time += frameTime
			ag.onRender(ag)
		}
		try {
			withTimeout(10.seconds) {
				block(views)
			}
		} finally {
			el.close()
		}
	}
}

private fun CoroutineScope.animationFrameLoopKorge(callback: suspend (Closeable) -> Unit): Closeable {
	var job: Job? = null
	val close = Closeable {
		job?.cancel()
	}
	job = launchImmediately {
		while (true) {
			callback(close)
			delayFrame()
		}
	}
	return close
}

suspend private fun delayFrame() {
	delay(16.milliseconds)
}