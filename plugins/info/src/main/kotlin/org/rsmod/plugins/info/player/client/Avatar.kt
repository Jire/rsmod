package org.rsmod.plugins.info.player.client

import org.rsmod.plugins.info.model.coord.HighResCoord
import org.rsmod.plugins.info.model.coord.LowResCoord

public class Avatar(
    public var registered: Boolean = false,
    public var coords: HighResCoord = HighResCoord.ZERO,
    public var loCoords: LowResCoord = coords.toLowRes(),
    public var prevCoords: HighResCoord = HighResCoord.ZERO,
    public var loPrevCoords: LowResCoord = prevCoords.toLowRes(),
    public var extendedInfoLength: Int = 0,
    public var dynamicExtInfoUpdateClock: Int = 0
)

public inline val Avatar.isValid: Boolean get() = registered
public inline val Avatar.isInvalid: Boolean get() = !isValid

public fun Avatar.clean() {
    registered = false
    coords = HighResCoord.ZERO
    loCoords = LowResCoord.ZERO
    prevCoords = HighResCoord.ZERO
    loPrevCoords = LowResCoord.ZERO
    extendedInfoLength = 0
    dynamicExtInfoUpdateClock = 0
}
