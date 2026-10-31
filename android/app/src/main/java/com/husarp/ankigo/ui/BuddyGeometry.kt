package com.husarp.ankigo.ui

data class BuddySpace(val width: Float, val height: Float, val scale: Float, val offsetX: Float, val offsetY: Float)

/** Same coordinates for the still canvas and the RemoteViews animation overlay. */
data class BuddyGeometry(val cx: Float, val top: Float, val size: Float, val spriteLeft: Float, val spriteTop: Float, val spriteSize: Float) {
    fun scaled(percent: Int): BuddyGeometry {
        val next = spriteSize * (percent.coerceIn(50, 100) / 100f)
        return copy(spriteLeft = spriteLeft + (spriteSize-next)/2, spriteTop = spriteTop + (spriteSize-next)/2, spriteSize = next)
    }
    companion object {
        fun space(w: Float, h: Float): BuddySpace {
            val rw=if(w>=220f)286f else 140f
            val rh=if(h<100f)67f else 140f
            val scale=minOf(w/rw,h/rh)
            return BuddySpace(rw,rh,scale,(w-rw*scale)/2,(h-rh*scale)/2)
        }
        fun of(w: Float, h: Float, layout: String): BuddyGeometry {
            val wide = w >= 220
            val short = h < 100
            val size = when { short -> if(layout=="C")61f else 57f; layout == "C" -> if (wide) 130f else 116f; wide -> 118f; else -> 88f }
            val cx = when { short -> if(layout=="C")35.5f else 33.5f; wide && layout == "C" -> 201f; wide -> 71f; else -> 70f }
            val top = when { short -> if(layout=="C")3f else 5f; layout == "C" -> if(wide)4f else 2f; wide -> 11f; else -> 6f }
            if (layout == "B") {
                val sz = if(short)58f else if(wide)124f else 82f
                return BuddyGeometry(cx,top,size,if(short)3f else if(wide)8f else 30f,if(short)5f else if(wide)8f else 0f,sz)
            }
            // Inscribe the entire image rectangle (including opaque corners) inside the
            // ring's inner edge, with a 2dp gap. This also bounds every animated frame.
            val innerRadius = size / 2 - 3f - size * .065f / 2 - 2f
            val spriteSize = minOf(innerRadius * kotlin.math.sqrt(2f),
                if (layout == "C") size * .52f else size)
            return BuddyGeometry(cx, top, size, cx-spriteSize/2, top+size/2-spriteSize/2, spriteSize)
        }
    }
}
