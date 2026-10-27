package com.example.game.level

import com.example.game.model.CalloutMoveType
import com.example.game.model.ChalkBit
import com.example.game.model.GuideStroke
import com.example.game.model.LevelDefinition
import com.example.game.model.Platform
import com.example.game.model.ScribblerEnemy
import com.example.game.model.ScriptedCallout
import com.example.game.model.ThumbtackCluster
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants

/**
 * Provides curated levels (including the required Level 1 test level) and endless
 * procedural levels featuring Chalk Bits, Gold Stars, Thumbtacks, Red Scribblers,
 * and Callout Moves.
 */
object LevelCatalog {

  fun getLevel(levelNumber: Int): LevelDefinition {
    return when (levelNumber) {
      1 -> createLevel1()
      2 -> createLevel2()
      3 -> createLevel3()
      4 -> createLevel4()
      else -> createProceduralLevel(levelNumber)
    }
  }

  /**
   * Initial Test Level (Level 1):
   * - Safe starting platform
   * - First gap requiring a bridge (with floating Chalk Bits)
   * - Middle safe platform
   * - Second wider gap with a Gold Star & Chalk Bits
   * - Finish platform and finish line
   */
  private fun createLevel1(): LevelDefinition {
    val groundY = WorldConstants.DEFAULT_GROUND_Y
    return LevelDefinition(
      levelNumber = 1,
      title = "Homeroom Sketch",
      subtitle = "Bridge the gaps, grab Chalk Bits to refill ink, or switch to Bounce/Turbo chalk!",
      runSpeed = 175f,
      spawnPosition = Vec2(x = 110f, y = groundY),
      finishLineX = 1980f,
      levelEndX = 2220f,
      maxInkPerLevel = 1000f,
      inkRechargeRate = 120f,
      parInkUsage = 920f,
      maxStrokeLength = 760f,
      platforms = listOf(
        Platform(
          startX = -80f,
          endX = 560f,
          topY = groundY,
          label = "START"
        ),
        Platform(
          startX = 860f,
          endX = 1280f,
          topY = groundY,
          label = "CHECKPOINT"
        ),
        Platform(
          startX = 1760f,
          endX = 2350f,
          topY = groundY,
          label = "FINISH"
        )
      ),
      guideStrokes = listOf(
        GuideStroke(
          points = listOf(
            Vec2(535f, groundY - 4f),
            Vec2(710f, groundY - 22f),
            Vec2(885f, groundY - 4f)
          ),
          caption = "DRAG HERE TO BRIDGE GAP 1"
        ),
        GuideStroke(
          points = listOf(
            Vec2(1255f, groundY - 4f),
            Vec2(1520f, groundY - 30f),
            Vec2(1785f, groundY - 4f)
          ),
          caption = "DRAW LONGER BRIDGE"
        )
      ),
      chalkBits = listOf(
        ChalkBit(id = 1, x = 360f, y = groundY - 34f),
        ChalkBit(id = 2, x = 650f, y = groundY - 44f),
        ChalkBit(id = 3, x = 760f, y = groundY - 44f),
        ChalkBit(id = 4, x = 1060f, y = groundY - 36f),
        ChalkBit(id = 5, x = 1420f, y = groundY - 48f),
        ChalkBit(id = 6, x = 1520f, y = groundY - 76f, isStar = true, inkAmount = 220f),
        ChalkBit(id = 7, x = 1620f, y = groundY - 48f)
      ),
      calloutScript = listOf(
        ScriptedCallout(triggerX = 890f, type = CalloutMoveType.BLITZ)
      )
    )
  }

  /**
   * Level 2: Elevated platforms, Red Scribblers, and Bounce/Vault mechanics.
   */
  private fun createLevel2(): LevelDefinition {
    return LevelDefinition(
      levelNumber = 2,
      title = "Recess Bounce",
      subtitle = "Use Blue Bounce chalk or upward ramps to climb, and bonk Red Scribblers with walls!",
      runSpeed = 185f,
      spawnPosition = Vec2(x = 110f, y = 430f),
      finishLineX = 2320f,
      levelEndX = 2560f,
      maxInkPerLevel = 1000f,
      inkRechargeRate = 125f,
      parInkUsage = 960f,
      maxStrokeLength = 780f,
      platforms = listOf(
        Platform(startX = -80f, endX = 520f, topY = 430f, label = "START"),
        Platform(startX = 860f, endX = 1260f, topY = 370f, label = "LEDGE 1"),
        Platform(startX = 1680f, endX = 2000f, topY = 320f, label = "HIGH LEDGE"),
        Platform(startX = 2180f, endX = 2680f, topY = 360f, label = "FINISH")
      ),
      guideStrokes = listOf(
        GuideStroke(
          points = listOf(Vec2(495f, 426f), Vec2(690f, 395f), Vec2(880f, 366f)),
          caption = "DRAW RAMP OR BLUE BOUNCE PAD"
        )
      ),
      chalkBits = listOf(
        ChalkBit(id = 10, x = 620f, y = 370f),
        ChalkBit(id = 11, x = 740f, y = 345f),
        ChalkBit(id = 12, x = 1060f, y = 335f),
        ChalkBit(id = 13, x = 1460f, y = 295f),
        ChalkBit(id = 14, x = 1560f, y = 265f, isStar = true, inkAmount = 220f),
        ChalkBit(id = 15, x = 2090f, y = 305f)
      ),
      thumbtacks = listOf(
        ThumbtackCluster(x = 690f, y = 455f, count = 3)
      ),
      scribblers = listOf(
        ScribblerEnemy(id = 201, x = 1160f, y = 370f, speed = 110f),
        ScribblerEnemy(id = 202, x = 1910f, y = 320f, speed = 120f)
      ),
      calloutScript = listOf(
        ScriptedCallout(triggerX = 320f, type = CalloutMoveType.JUMP),
        ScriptedCallout(triggerX = 1320f, type = CalloutMoveType.GRAVITY)
      )
    )
  }

  /**
   * Level 3: Field Trip Canyon with Low Gravity, Scribblers, and Eraser Wipeout.
   */
  private fun createLevel3(): LevelDefinition {
    return LevelDefinition(
      levelNumber = 3,
      title = "Field Trip Canyon",
      subtitle = "Ride Low Gravity arcs, ram Red Scribblers with Gold Turbo chalk, and survive Wipeout!",
      runSpeed = 195f,
      spawnPosition = Vec2(x = 110f, y = 310f),
      finishLineX = 2580f,
      levelEndX = 2820f,
      maxInkPerLevel = 1050f,
      inkRechargeRate = 130f,
      parInkUsage = 1000f,
      maxStrokeLength = 800f,
      platforms = listOf(
        Platform(startX = -80f, endX = 480f, topY = 310f, label = "HIGH CLIFF"),
        Platform(startX = 980f, endX = 1320f, topY = 430f, label = "VALLEY"),
        Platform(startX = 1680f, endX = 1860f, topY = 390f, label = "ISLAND"),
        Platform(startX = 2380f, endX = 2950f, topY = 380f, label = "FINISH")
      ),
      chalkBits = listOf(
        ChalkBit(id = 20, x = 640f, y = 320f),
        ChalkBit(id = 21, x = 790f, y = 355f),
        ChalkBit(id = 22, x = 1150f, y = 390f),
        ChalkBit(id = 23, x = 1500f, y = 365f),
        ChalkBit(id = 24, x = 2020f, y = 325f, isStar = true, inkAmount = 240f),
        ChalkBit(id = 25, x = 2180f, y = 340f)
      ),
      thumbtacks = listOf(
        ThumbtackCluster(x = 1500f, y = 445f, count = 3)
      ),
      scribblers = listOf(
        ScribblerEnemy(id = 301, x = 1240f, y = 430f, speed = 125f),
        ScribblerEnemy(id = 302, x = 2520f, y = 380f, speed = 130f)
      ),
      calloutScript = listOf(
        ScriptedCallout(triggerX = 350f, type = CalloutMoveType.GRAVITY),
        ScriptedCallout(triggerX = 1050f, type = CalloutMoveType.BLITZ),
        ScriptedCallout(triggerX = 1880f, type = CalloutMoveType.WIPEOUT)
      )
    )
  }

  /**
   * Level 4: Final Exam Gauntlet.
   */
  private fun createLevel4(): LevelDefinition {
    return LevelDefinition(
      levelNumber = 4,
      title = "Final Exam Gauntlet",
      subtitle = "Combine Bounce, Turbo Gold, and Tap-Cut shockwaves across the night board!",
      runSpeed = 205f,
      spawnPosition = Vec2(x = 110f, y = 410f),
      finishLineX = 2850f,
      levelEndX = 3100f,
      maxInkPerLevel = 1100f,
      inkRechargeRate = 135f,
      parInkUsage = 1050f,
      maxStrokeLength = 800f,
      platforms = listOf(
        Platform(startX = -80f, endX = 460f, topY = 410f, label = "START"),
        Platform(startX = 820f, endX = 1040f, topY = 380f),
        Platform(startX = 1420f, endX = 1640f, topY = 340f),
        Platform(startX = 2060f, endX = 2260f, topY = 400f),
        Platform(startX = 2720f, endX = 3220f, topY = 370f, label = "FINISH")
      ),
      chalkBits = listOf(
        ChalkBit(id = 30, x = 640f, y = 355f),
        ChalkBit(id = 31, x = 1230f, y = 315f),
        ChalkBit(id = 32, x = 1850f, y = 310f, isStar = true, inkAmount = 250f),
        ChalkBit(id = 33, x = 2480f, y = 340f)
      ),
      thumbtacks = listOf(
        ThumbtackCluster(x = 1230f, y = 425f, count = 3),
        ThumbtackCluster(x = 2480f, y = 435f, count = 3)
      ),
      scribblers = listOf(
        ScribblerEnemy(id = 401, x = 980f, y = 380f, speed = 130f),
        ScribblerEnemy(id = 402, x = 1580f, y = 340f, speed = 135f),
        ScribblerEnemy(id = 403, x = 2200f, y = 400f, speed = 140f)
      ),
      calloutScript = listOf(
        ScriptedCallout(triggerX = 330f, type = CalloutMoveType.JUMP),
        ScriptedCallout(triggerX = 1440f, type = CalloutMoveType.GRAVITY),
        ScriptedCallout(triggerX = 2100f, type = CalloutMoveType.BLITZ)
      )
    )
  }

  /**
   * Procedural level generator for Level 5+ so players can keep advancing indefinitely.
   */
  private fun createProceduralLevel(levelNumber: Int): LevelDefinition {
    val platforms = mutableListOf<Platform>()
    val bits = mutableListOf<ChalkBit>()
    val scribblers = mutableListOf<ScribblerEnemy>()
    val callouts = mutableListOf<ScriptedCallout>()

    var cursorX = -80f
    var currentY = 410f
    val startEnd = 480f
    platforms.add(Platform(startX = cursorX, endX = startEnd, topY = currentY, label = "START"))
    cursorX = startEnd

    val segmentsCount = (3 + (levelNumber % 3)).coerceAtMost(6)
    for (i in 0 until segmentsCount) {
      val gapWidth = 310f + ((levelNumber * 37 + i * 83) % 210)
      val midGapX = cursorX + gapWidth * 0.5f
      bits.add(
        ChalkBit(
          id = levelNumber * 100 + i,
          x = midGapX,
          y = (currentY - 52f).coerceAtLeast(180f),
          isStar = (i == segmentsCount - 1)
        )
      )
      if (i % 2 == 0) {
        callouts.add(
          ScriptedCallout(
            triggerX = cursorX - 90f,
            type = CalloutMoveType.entries[(levelNumber + i) % CalloutMoveType.entries.size]
          )
        )
      }
      cursorX += gapWidth
      val deltaY = (((levelNumber * 29 + i * 53) % 110) - 55).toFloat()
      currentY = (currentY + deltaY).coerceIn(290f, 445f)
      val platWidth = 240f + ((i * 67) % 140)
      platforms.add(Platform(startX = cursorX, endX = cursorX + platWidth, topY = currentY))
      scribblers.add(
        ScribblerEnemy(
          id = levelNumber * 1000 + i,
          x = cursorX + platWidth * 0.7f,
          y = currentY,
          speed = 125f + (levelNumber % 5) * 6f
        )
      )
      cursorX += platWidth
    }

    val finalGap = 420f + ((levelNumber * 41) % 140)
    cursorX += finalGap
    val finishX = cursorX + 180f
    val endX = cursorX + 480f
    platforms.add(
      Platform(
        startX = cursorX,
        endX = endX + 100f,
        topY = currentY.coerceIn(320f, 430f),
        label = "FINISH"
      )
    )

    val speed = (195f + (levelNumber - 4) * 5f).coerceAtMost(245f)
    return LevelDefinition(
      levelNumber = levelNumber,
      title = "Chalk Trial #$levelNumber",
      subtitle = "Master Bounce, Turbo Gold, and Tap-Cut shockwaves!",
      runSpeed = speed,
      spawnPosition = Vec2(x = 110f, y = 410f),
      finishLineX = finishX,
      levelEndX = endX,
      maxInkPerLevel = 1150f,
      inkRechargeRate = 140f,
      parInkUsage = 1080f,
      maxStrokeLength = 820f,
      platforms = platforms,
      chalkBits = bits,
      scribblers = scribblers,
      calloutScript = callouts
    )
  }
}
