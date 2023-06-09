package org.rsmod.plugins.net

import io.netty.channel.Channel
import io.netty.util.AttributeKey
import org.rsmod.game.client.Client

private val clientAttr: AttributeKey<Client> = AttributeKey.valueOf("client")

public fun Channel.clientAttr(): Client? {
    return if (hasAttr(clientAttr)) attr(clientAttr).get() else null
}

public fun Channel.setClientAttr(client: Client) {
    attr(clientAttr).set(client)
}
