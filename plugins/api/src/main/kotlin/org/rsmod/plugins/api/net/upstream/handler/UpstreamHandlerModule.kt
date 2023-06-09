package org.rsmod.plugins.api.net.upstream.handler

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder

internal object UpstreamHandlerModule : AbstractModule() {

    private val handlers = listOf(
        ClientCheatHandler::class.java,
        IfButton1Handler::class.java,
        IfButton2Handler::class.java,
        IfButton3Handler::class.java,
        IfButton4Handler::class.java,
        IfButton5Handler::class.java,
        IfButton6Handler::class.java,
        IfButton7Handler::class.java,
        IfButton8Handler::class.java,
        IfButton9Handler::class.java,
        IfButton10Handler::class.java,
        MoveGameClickHandler::class.java,
        MoveMinimapClickHandler::class.java
    )

    override fun configure() {
        val binder = Multibinder.newSetBinder(binder(), GENERIC_HANDLER_LITERAL)
        handlers.forEach { binder.addBinding().to(it) }

        bind(UpstreamHandlerTask::class.java).`in`(Scopes.SINGLETON)

        bind(UpstreamHandlerMap::class.java)
            .toProvider(UpstreamHandlerMapProvider::class.java)
            .`in`(Scopes.SINGLETON)
    }

    private val GENERIC_HANDLER_LITERAL = object : TypeLiteral<UpstreamHandler<*>>() {}
}
