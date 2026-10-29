package com.example.game.level

import com.example.game.model.BreakableRockWall
import com.example.game.model.CalloutMoveType
import com.example.game.model.ChalkBit
import com.example.game.model.DifficultyMode
import com.example.game.model.DynamicHazard
import com.example.game.model.GuideStroke
import com.example.game.model.HazardType
import com.example.game.model.LeapingLavaMonster
import com.example.game.model.LedgeStyle
import com.example.game.model.LevelBlueprint
import com.example.game.model.LevelConfig
import com.example.game.model.LevelDefinition
import com.example.game.model.MapBiome
import com.example.game.model.PendulumStyle
import com.example.game.model.Platform
import com.example.game.model.ScriptedCallout
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Complete 50-Level Progression, Modular Obstacle Recycling & Combination System,
 * and Multi-Map / Difficulty Architecture for Draw to Survive.
 */
object LevelCatalog {

  const val TOTAL_LEVELS = 50

  private val WORLD_THEMES = listOf(
    "Tier 1: The Basics (Volcano)",
    "Tier 2: Aerial & Vertical Threats",
    "Tier 3: Precision & Tracking",
    "Tier 4: Timing & Physics Mastery",
    "Tier 5: Nightmare Endurance & Chaos"
  )

  /**
   * PART 3: Clean, Data-Driven `LEVEL_DEFINITIONS` Progression Matrix (Levels 1 to 50).
   * Explicitly maps out how the 8 modular obstacles and 4 `INCOMING!` events are introduced,
   * recycled, and combined across all 5 Tiers.
   */
  val LEVEL_DEFINITIONS: List<LevelBlueprint> = buildLevelBlueprints()

  private fun buildLevelBlueprints(): List<LevelBlueprint> {
    val allEvents = listOf(
      CalloutMoveType.JUMP,
      CalloutMoveType.BLITZ,
      CalloutMoveType.GRAVITY,
      CalloutMoveType.WIPEOUT
    )
    val allObstacles = listOf(
      HazardType.PENDULUM,
      HazardType.LEDGE,
      HazardType.LAVA_GEYSER,
      HazardType.FALLING_SPIKE,
      HazardType.LASER_GATE,
      HazardType.SAW_TRACK,
      HazardType.NO_DRAW_ZONE,
      HazardType.WIND_UPDRAFT
    )

    return (1..TOTAL_LEVELS).map { lvl ->
      when (lvl) {
        // ==================== TIER 1: THE BASICS (LEVELS 1–10) ====================
        1 -> LevelBlueprint(
          level = 1,
          tier = 1,
          title = "Homeroom Sketch",
          subtitle = "Wide cyan ledges & low gaps. Draw chalk bridges across the lava!",
          obstaclesAllowed = listOf(HazardType.LEDGE),
          spawnRate = 0.65f,
          incomingEventsAllowed = emptyList(),
          ledgeStyle = LedgeStyle.CYAN_STATIC
        )
        2 -> LevelBlueprint(
          level = 2,
          tier = 1,
          title = "Stepping Ledges",
          subtitle = "Use wide cyan ledges as mid-air stepping stones across lava.",
          obstaclesAllowed = listOf(HazardType.LEDGE),
          spawnRate = 0.80f,
          incomingEventsAllowed = emptyList(),
          ledgeStyle = LedgeStyle.CYAN_STATIC
        )
        3 -> LevelBlueprint(
          level = 3,
          tier = 1,
          title = "Pendulum Intro",
          subtitle = "Slow swinging spiked pendulum with high pivot + cyan ledges.",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.LEDGE),
          spawnRate = 0.85f,
          incomingEventsAllowed = emptyList(),
          pendulumStyle = PendulumStyle.SINGLE,
          ledgeStyle = LedgeStyle.CYAN_STATIC
        )
        4 -> LevelBlueprint(
          level = 4,
          tier = 1,
          title = "Tethered Crossing",
          subtitle = "Curve your chalk bridge safely underneath swinging pendulums.",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.LEDGE),
          spawnRate = 0.92f,
          incomingEventsAllowed = emptyList(),
          pendulumStyle = PendulumStyle.SINGLE,
          ledgeStyle = LedgeStyle.CYAN_STATIC
        )
        5 -> LevelBlueprint(
          level = 5,
          tier = 1,
          title = "Geyser Eruption",
          subtitle = "Watch the lava bubbles! Draw high arched bridges over Lava Geysers.",
          obstaclesAllowed = listOf(HazardType.LAVA_GEYSER, HazardType.LEDGE),
          spawnRate = 0.95f,
          incomingEventsAllowed = emptyList(),
          ledgeStyle = LedgeStyle.CYAN_STATIC
        )
        6 -> LevelBlueprint(
          level = 6,
          tier = 1,
          title = "Thermal Bridges",
          subtitle = "Time your ramps or arch high above erupting vertical fireball pillars.",
          obstaclesAllowed = listOf(HazardType.LAVA_GEYSER, HazardType.LEDGE),
          spawnRate = 1.0f,
          incomingEventsAllowed = emptyList(),
          ledgeStyle = LedgeStyle.CYAN_STATIC
        )
        7 -> LevelBlueprint(
          level = 7,
          tier = 1,
          title = "Under & Over",
          subtitle = "Thread the needle: draw under the Pendulum and over the Lava Geyser!",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.LAVA_GEYSER),
          spawnRate = 1.05f,
          incomingEventsAllowed = emptyList()
        )
        8 -> LevelBlueprint(
          level = 8,
          tier = 1,
          title = "Magma Pendulums",
          subtitle = "Tighter S-curve sketching between overhead pendulums and bottom geysers.",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.LAVA_GEYSER),
          spawnRate = 1.10f,
          incomingEventsAllowed = emptyList()
        )
        9 -> LevelBlueprint(
          level = 9,
          tier = 1,
          title = "Incoming Surge",
          subtitle = "⚡ INCOMING! JUMP and LOW GRAV callout events join swinging pendulums.",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.LEDGE),
          spawnRate = 1.12f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.GRAVITY)
        )
        10 -> LevelBlueprint(
          level = 10,
          tier = 1,
          title = "Magma Crossing",
          subtitle = "MINI-BOSS: Fast alternating waves of Ledges, Pendulums, and Lava Geysers!",
          obstaclesAllowed = listOf(HazardType.LEDGE, HazardType.PENDULUM, HazardType.LAVA_GEYSER),
          spawnRate = 1.25f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.GRAVITY),
          isBossOrMilestone = true
        )

        // ==================== TIER 2: AERIAL & VERTICAL THREATS (LEVELS 11–20) ====================
        11 -> LevelBlueprint(
          level = 11,
          tier = 2,
          title = "Falling Stalactites",
          subtitle = "Red laser lines mark Falling Spikes! Draw an overhead Chalk roof shield.",
          obstaclesAllowed = listOf(HazardType.FALLING_SPIKE, HazardType.LEDGE),
          spawnRate = 1.15f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP)
        )
        12 -> LevelBlueprint(
          level = 12,
          tier = 2,
          title = "Sky Shielding",
          subtitle = "Sketch both a bridge below and a protective Chalk canopy overhead.",
          obstaclesAllowed = listOf(HazardType.FALLING_SPIKE, HazardType.LEDGE),
          spawnRate = 1.20f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP)
        )
        13 -> LevelBlueprint(
          level = 13,
          tier = 2,
          title = "Tunnel Vision",
          subtitle = "Falling Spikes above + Lava Geysers below: draw a safe chalk tunnel!",
          obstaclesAllowed = listOf(HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER),
          spawnRate = 1.22f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.GRAVITY)
        )
        14 -> LevelBlueprint(
          level = 14,
          tier = 2,
          title = "Brimstone Canopy",
          subtitle = "Shield against ceiling meteors while arching above fireball pillars.",
          obstaclesAllowed = listOf(HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER),
          spawnRate = 1.26f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.GRAVITY)
        )
        15 -> LevelBlueprint(
          level = 15,
          tier = 2,
          title = "Crumbling Orange",
          subtitle = "Orange crumbling ledges + high vertical gaps. Use Bounce ink to launch up!",
          obstaclesAllowed = listOf(HazardType.LEDGE, HazardType.FALLING_SPIKE),
          spawnRate = 1.28f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.GRAVITY),
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING
        )
        16 -> LevelBlueprint(
          level = 16,
          tier = 2,
          title = "Trampoline Vault",
          subtitle = "Chain Blue Bounce pads between temporary crumbling orange ledges.",
          obstaclesAllowed = listOf(HazardType.LEDGE, HazardType.LAVA_GEYSER),
          spawnRate = 1.32f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.GRAVITY),
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING
        )
        17 -> LevelBlueprint(
          level = 17,
          tier = 2,
          title = "Razor Tack Ridge",
          subtitle = "Twin synchronized Pendulums + LOW GRAV & ERASER incoming events!",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.LEDGE),
          spawnRate = 1.36f,
          incomingEventsAllowed = listOf(CalloutMoveType.GRAVITY, CalloutMoveType.WIPEOUT),
          pendulumStyle = PendulumStyle.DOUBLE_SYNC,
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING,
          isBossOrMilestone = true
        )
        18 -> LevelBlueprint(
          level = 18,
          tier = 2,
          title = "High Ledge Climb",
          subtitle = "Steep upward Ledge staircase + low-hanging Pendulums + BLITZ!",
          obstaclesAllowed = listOf(HazardType.LEDGE, HazardType.PENDULUM),
          spawnRate = 1.40f,
          incomingEventsAllowed = listOf(CalloutMoveType.BLITZ, CalloutMoveType.JUMP),
          pendulumStyle = PendulumStyle.FAST_SHORT,
          ledgeStyle = LedgeStyle.CYAN_BOUNCY,
          isBossOrMilestone = true
        )
        19 -> LevelBlueprint(
          level = 19,
          tier = 2,
          title = "Gold Ink Express",
          subtitle = "High collectible density + fast Pendulum arcs + JUMP & BLITZ!",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.LEDGE, HazardType.FALLING_SPIKE),
          spawnRate = 1.44f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.BLITZ),
          pendulumStyle = PendulumStyle.FAST_SHORT,
          isBossOrMilestone = true
        )
        20 -> LevelBlueprint(
          level = 20,
          tier = 2,
          title = "Cavern Guardian",
          subtitle = "BOSS: Pendulums, Falling Spikes, and Lava Geysers with all 4 INCOMING! events.",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER, HazardType.LEDGE),
          spawnRate = 1.50f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.DOUBLE_SYNC,
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING,
          isBossOrMilestone = true
        )

        // ==================== TIER 3: PRECISION & TRACKING (LEVELS 21–30) ====================
        21 -> LevelBlueprint(
          level = 21,
          tier = 3,
          title = "Blueprint Ascent",
          subtitle = "High-altitude Ledge climbs + dual Pendulums + LOW GRAV.",
          obstaclesAllowed = listOf(HazardType.LEDGE, HazardType.PENDULUM),
          spawnRate = 1.45f,
          incomingEventsAllowed = listOf(CalloutMoveType.GRAVITY, CalloutMoveType.JUMP),
          pendulumStyle = PendulumStyle.DOUBLE_SYNC,
          ledgeStyle = LedgeStyle.CYAN_BOUNCY,
          isBossOrMilestone = true
        )
        22 -> LevelBlueprint(
          level = 22,
          tier = 3,
          title = "Buzzsaw Patrol",
          subtitle = "Spinning red Saw Tracks patrol horizontally along dotted rails.",
          obstaclesAllowed = listOf(HazardType.SAW_TRACK, HazardType.LEDGE),
          spawnRate = 1.48f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.BLITZ)
        )
        23 -> LevelBlueprint(
          level = 23,
          tier = 3,
          title = "Drafting Saws",
          subtitle = "Arc your chalk lines above or below patrolling buzzsaw tracks.",
          obstaclesAllowed = listOf(HazardType.SAW_TRACK, HazardType.LEDGE),
          spawnRate = 1.52f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.GRAVITY)
        )
        24 -> LevelBlueprint(
          level = 24,
          tier = 3,
          title = "Static Jammer",
          subtitle = "Red No-Draw Zones block Chalk! Place a Bounce pad BEFORE the red zone.",
          obstaclesAllowed = listOf(HazardType.NO_DRAW_ZONE, HazardType.LEDGE),
          spawnRate = 1.56f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.BLITZ),
          ledgeStyle = LedgeStyle.CYAN_BOUNCY
        )
        25 -> LevelBlueprint(
          level = 25,
          tier = 3,
          title = "Clockwork Hazard",
          subtitle = "No-Draw Zones, Swinging Pendulums, and Ledges test mid-air Bounce arcs.",
          obstaclesAllowed = listOf(HazardType.NO_DRAW_ZONE, HazardType.PENDULUM, HazardType.LEDGE),
          spawnRate = 1.60f,
          incomingEventsAllowed = listOf(CalloutMoveType.GRAVITY, CalloutMoveType.WIPEOUT)
        )
        26 -> LevelBlueprint(
          level = 26,
          tier = 3,
          title = "Intersecting Blades",
          subtitle = "Bounce over No-Draw Zones while Saw Tracks and Ledges patrol below.",
          obstaclesAllowed = listOf(HazardType.NO_DRAW_ZONE, HazardType.LEDGE, HazardType.SAW_TRACK),
          spawnRate = 1.58f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.GRAVITY),
          ledgeStyle = LedgeStyle.CYAN_BOUNCY
        )
        27 -> LevelBlueprint(
          level = 27,
          tier = 3,
          title = "Dead Air Leap",
          subtitle = "Launch over wide No-Draw static fields using Blue Bounce ink.",
          obstaclesAllowed = listOf(HazardType.NO_DRAW_ZONE, HazardType.LEDGE, HazardType.SAW_TRACK),
          spawnRate = 1.62f,
          incomingEventsAllowed = listOf(CalloutMoveType.JUMP, CalloutMoveType.BLITZ)
        )
        28 -> LevelBlueprint(
          level = 28,
          tier = 3,
          title = "Forbidden Canopy",
          subtitle = "No-Draw Zones + Falling Spikes + Lava Geysers demand pinpoint ramps.",
          obstaclesAllowed = listOf(HazardType.NO_DRAW_ZONE, HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER),
          spawnRate = 1.66f,
          incomingEventsAllowed = allEvents
        )
        29 -> LevelBlueprint(
          level = 29,
          tier = 3,
          title = "Red Zone Eruption",
          subtitle = "Bounce high over No-Draw Zones while dodging geysers and ceiling spikes.",
          obstaclesAllowed = listOf(HazardType.NO_DRAW_ZONE, HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER),
          spawnRate = 1.70f,
          incomingEventsAllowed = allEvents
        )
        30 -> LevelBlueprint(
          level = 30,
          tier = 3,
          title = "Sawblade Sanctuary",
          subtitle = "BOSS: High-speed gauntlet combining Saw Tracks, No-Draw Zones, and Pendulums!",
          obstaclesAllowed = listOf(HazardType.SAW_TRACK, HazardType.NO_DRAW_ZONE, HazardType.PENDULUM, HazardType.LEDGE),
          spawnRate = 1.76f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.DOUBLE_SYNC,
          isBossOrMilestone = true
        )

        // ==================== TIER 4: TIMING & PHYSICS MASTERY (LEVELS 31–40) ====================
        31 -> LevelBlueprint(
          level = 31,
          tier = 4,
          title = "Laser Gate Protocol",
          subtitle = "Vertical Laser Gates toggle every 1.5s! Time the window or dash with Turbo ink.",
          obstaclesAllowed = listOf(HazardType.LASER_GATE, HazardType.LEDGE),
          spawnRate = 1.68f,
          incomingEventsAllowed = listOf(CalloutMoveType.BLITZ, CalloutMoveType.JUMP)
        )
        32 -> LevelBlueprint(
          level = 32,
          tier = 4,
          title = "Photon Dash",
          subtitle = "Lay Gold Turbo tracks to blast past cycling red Laser Gates.",
          obstaclesAllowed = listOf(HazardType.LASER_GATE, HazardType.LEDGE),
          spawnRate = 1.72f,
          incomingEventsAllowed = listOf(CalloutMoveType.BLITZ, CalloutMoveType.WIPEOUT)
        )
        33 -> LevelBlueprint(
          level = 33,
          tier = 4,
          title = "Foundry Slicers",
          subtitle = "Laser Gates + Saw Tracks + crumbling Orange Ledges.",
          obstaclesAllowed = listOf(HazardType.LASER_GATE, HazardType.SAW_TRACK, HazardType.LEDGE),
          spawnRate = 1.76f,
          incomingEventsAllowed = allEvents,
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING
        )
        34 -> LevelBlueprint(
          level = 34,
          tier = 4,
          title = "Crucible Grid",
          subtitle = "Crumbling orange footholds between vertical lasers and moving buzzsaws.",
          obstaclesAllowed = listOf(HazardType.LASER_GATE, HazardType.SAW_TRACK, HazardType.LEDGE),
          spawnRate = 1.80f,
          incomingEventsAllowed = allEvents,
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING
        )
        35 -> LevelBlueprint(
          level = 35,
          tier = 4,
          title = "Updraft Gale",
          subtitle = "Wind Updraft columns push you into overhead Pendulums & Spikes! Draw a ceiling.",
          obstaclesAllowed = listOf(HazardType.WIND_UPDRAFT, HazardType.PENDULUM, HazardType.FALLING_SPIKE),
          spawnRate = 1.82f,
          incomingEventsAllowed = listOf(CalloutMoveType.GRAVITY, CalloutMoveType.JUMP)
        )
        36 -> LevelBlueprint(
          level = 36,
          tier = 4,
          title = "Thermal Ceiling",
          subtitle = "Cap the Wind Updrafts with a Chalk roof so you don't float into spikes!",
          obstaclesAllowed = listOf(HazardType.WIND_UPDRAFT, HazardType.PENDULUM, HazardType.FALLING_SPIKE),
          spawnRate = 1.85f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.FAST_SHORT
        )
        37 -> LevelBlueprint(
          level = 37,
          tier = 4,
          title = "Cyclone Redline",
          subtitle = "Triple Combo: Wind Updrafts + No-Draw Zones + Lava Geysers.",
          obstaclesAllowed = listOf(HazardType.WIND_UPDRAFT, HazardType.NO_DRAW_ZONE, HazardType.LAVA_GEYSER),
          spawnRate = 1.88f,
          incomingEventsAllowed = allEvents
        )
        38 -> LevelBlueprint(
          level = 38,
          tier = 4,
          title = "Gale Force Eruption",
          subtitle = "Use Wind Updraft lift to glide across No-Draw Zones and Lava Geysers.",
          obstaclesAllowed = listOf(HazardType.WIND_UPDRAFT, HazardType.NO_DRAW_ZONE, HazardType.LAVA_GEYSER),
          spawnRate = 1.92f,
          incomingEventsAllowed = allEvents
        )
        39 -> LevelBlueprint(
          level = 39,
          tier = 4,
          title = "Pressure Valve",
          subtitle = "High-velocity Wind Updrafts paired with No-Draw static fields and geysers.",
          obstaclesAllowed = listOf(HazardType.WIND_UPDRAFT, HazardType.NO_DRAW_ZONE, HazardType.LAVA_GEYSER, HazardType.LASER_GATE),
          spawnRate = 1.95f,
          incomingEventsAllowed = allEvents
        )
        40 -> LevelBlueprint(
          level = 40,
          tier = 4,
          title = "Reactor Core",
          subtitle = "BOSS: Nonstop Laser Gates, Wind Updrafts, and Twin Pendulum gauntlet!",
          obstaclesAllowed = listOf(HazardType.LASER_GATE, HazardType.WIND_UPDRAFT, HazardType.PENDULUM, HazardType.LEDGE),
          spawnRate = 2.02f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.DOUBLE_SYNC,
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING,
          isBossOrMilestone = true
        )

        // ==================== TIER 5: NIGHTMARE ENDURANCE & CHAOS (LEVELS 41–50) ====================
        41 -> LevelBlueprint(
          level = 41,
          tier = 5,
          title = "Quad Hazard Remix I",
          subtitle = "Pendulums, Laser Gates, Lava Geysers & Crumbling Ledges + rapid ERASER!",
          obstaclesAllowed = listOf(HazardType.PENDULUM, HazardType.LASER_GATE, HazardType.LAVA_GEYSER, HazardType.LEDGE),
          spawnRate = 2.05f,
          incomingEventsAllowed = listOf(CalloutMoveType.WIPEOUT, CalloutMoveType.BLITZ, CalloutMoveType.JUMP),
          pendulumStyle = PendulumStyle.DOUBLE_SYNC,
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING
        )
        42 -> LevelBlueprint(
          level = 42,
          tier = 5,
          title = "Quad Hazard Remix II",
          subtitle = "Saw Tracks, No-Draw Zones, Falling Spikes & Wind Updrafts.",
          obstaclesAllowed = listOf(HazardType.SAW_TRACK, HazardType.NO_DRAW_ZONE, HazardType.FALLING_SPIKE, HazardType.WIND_UPDRAFT),
          spawnRate = 2.08f,
          incomingEventsAllowed = listOf(CalloutMoveType.WIPEOUT, CalloutMoveType.BLITZ, CalloutMoveType.GRAVITY)
        )
        43 -> LevelBlueprint(
          level = 43,
          tier = 5,
          title = "Quad Hazard Remix III",
          subtitle = "Laser Gates, Wind Updrafts, Lava Geysers & Saw Tracks at blistering speed.",
          obstaclesAllowed = listOf(HazardType.LASER_GATE, HazardType.WIND_UPDRAFT, HazardType.LAVA_GEYSER, HazardType.SAW_TRACK),
          spawnRate = 2.12f,
          incomingEventsAllowed = allEvents
        )
        44 -> LevelBlueprint(
          level = 44,
          tier = 5,
          title = "Quad Hazard Remix IV",
          subtitle = "No-Draw Zones, Twin Pendulums, Falling Spikes & Laser Gates.",
          obstaclesAllowed = listOf(HazardType.NO_DRAW_ZONE, HazardType.PENDULUM, HazardType.FALLING_SPIKE, HazardType.LASER_GATE),
          spawnRate = 2.15f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.DOUBLE_SYNC
        )
        45 -> LevelBlueprint(
          level = 45,
          tier = 5,
          title = "Precision Corridor I",
          subtitle = "Low ceiling Falling Spikes + bottom Lava Geysers + middle Saw Tracks & Pendulums.",
          obstaclesAllowed = listOf(HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER, HazardType.SAW_TRACK, HazardType.PENDULUM),
          spawnRate = 2.18f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.FAST_SHORT
        )
        46 -> LevelBlueprint(
          level = 46,
          tier = 5,
          title = "Precision Corridor II",
          subtitle = "Tight S-curve threading between ceiling stalactites, saws, and magma geysers.",
          obstaclesAllowed = listOf(HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER, HazardType.SAW_TRACK, HazardType.PENDULUM),
          spawnRate = 2.22f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.DOUBLE_SYNC
        )
        47 -> LevelBlueprint(
          level = 47,
          tier = 5,
          title = "Precision Corridor III",
          subtitle = "Thread narrow tunnels through saws, geysers, and twin spiked pendulums.",
          obstaclesAllowed = listOf(HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER, HazardType.SAW_TRACK, HazardType.PENDULUM, HazardType.LASER_GATE),
          spawnRate = 2.26f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.DOUBLE_SYNC
        )
        48 -> LevelBlueprint(
          level = 48,
          tier = 5,
          title = "Precision Corridor IV",
          subtitle = "Extreme corridor gauntlet before the final ascent.",
          obstaclesAllowed = listOf(HazardType.FALLING_SPIKE, HazardType.LAVA_GEYSER, HazardType.SAW_TRACK, HazardType.PENDULUM, HazardType.NO_DRAW_ZONE),
          spawnRate = 2.30f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.FAST_SHORT
        )
        49 -> LevelBlueprint(
          level = 49,
          tier = 5,
          title = "The Final Ascent",
          subtitle = "MARATHON (2x Density): Cycles through every obstacle combo from Levels 1–48!",
          obstaclesAllowed = allObstacles,
          spawnRate = 2.38f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.DOUBLE_SYNC,
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING,
          lengthMultiplier = 2.0f,
          isBossOrMilestone = true
        )
        else -> LevelBlueprint(
          level = 50,
          tier = 5,
          title = "Inferno Overlord",
          subtitle = "GRAND FINALE: All 8 obstacles in waves, rapid INCOMING!, and x20 MAGNET finale!",
          obstaclesAllowed = allObstacles,
          spawnRate = 2.50f,
          incomingEventsAllowed = allEvents,
          pendulumStyle = PendulumStyle.DOUBLE_SYNC,
          ledgeStyle = LedgeStyle.ORANGE_CRUMBLING,
          isBossOrMilestone = true,
          hasMagnetFinale = true
        )
      }
    }
  }

  fun getBlueprint(level: Int): LevelBlueprint {
    val clamped = level.coerceIn(1, TOTAL_LEVELS)
    return LEVEL_DEFINITIONS[clamped - 1]
  }

  /**
   * Dynamically calculates all scaling parameters for [level] (`1` to `50`), optionally
   * modulated by [difficulty] and [mapBiome].
   */
  fun getLevelConfig(
    level: Int,
    difficulty: DifficultyMode = DifficultyMode.NORMAL,
    mapBiome: MapBiome = MapBiome.MAP_1_VOLCANO
  ): LevelConfig {
    val clampedLevel = level.coerceIn(1, TOTAL_LEVELS)
    val blueprint = getBlueprint(clampedLevel)
    val t = (clampedLevel - 1f) / (TOTAL_LEVELS - 1f) // 0.0 at L1 .. 1.0 at L50

    val worldIdx = (clampedLevel - 1) / 10
    val worldTitle = WORLD_THEMES[worldIdx.coerceIn(0, WORLD_THEMES.lastIndex)]

    // 1. Level Length: 800px in Level 1, +150px per level
    val levelLength = 800f + (clampedLevel - 1) * 150f

    // 2. Character Run Speed (baseSpeed): 3.0 in Level 1 -> 8.5 in Level 50
    val baseSpeed = (3.0f + t * (8.5f - 3.0f)) * difficulty.speedMult
    val runSpeed = (175f + t * (340f - 175f)) * difficulty.speedMult * mapBiome.slipSpeedMult

    // 3. Gap Width Range (minGap, maxGap): 80..140 in Level 1 -> 180..380 in Level 50
    val minGap = 80f + t * (180f - 80f)
    val maxGap = 140f + t * (380f - 140f)

    // 4. Platform Elevation Variance (deltaY): 0px in Levels 1..3 -> ±160px by Level 50
    val maxDeltaY = if (clampedLevel <= 3) {
      0f
    } else {
      ((clampedLevel - 3f) / 47f) * 160f
    }

    // 5. Max Ink Capacity & Regen Rate: tighter relative to gap size at higher levels
    val maxInkPerLevel = 1000f - t * 320f // 1000f at L1 -> 680f at L50
    val inkRechargeRate = (120f - t * 52f) / difficulty.inkDrainMult.coerceAtLeast(0.5f)
    val parInkUsage = maxInkPerLevel * 1.6f
    val maxStrokeLength = max(1800f, levelLength * blueprint.lengthMultiplier * 1.25f)

    // 6. Line Lifespan (Decay Timer): 5.0s in Level 1 -> 1.8s by Level 50
    val lineLifespan = 5.0f - t * (5.0f - 1.8f)

    return LevelConfig(
      level = clampedLevel,
      worldIndex = worldIdx + 1,
      worldTitle = worldTitle,
      levelTitle = blueprint.title,
      levelSubtitle = blueprint.subtitle,
      levelLength = levelLength,
      baseSpeed = baseSpeed,
      runSpeed = runSpeed,
      minGap = minGap,
      maxGap = maxGap,
      maxDeltaY = maxDeltaY,
      maxInkPerLevel = maxInkPerLevel,
      inkRechargeRate = inkRechargeRate,
      parInkUsage = parInkUsage,
      maxStrokeLength = maxStrokeLength,
      lineLifespan = lineLifespan,
      obstaclesAllowed = blueprint.obstaclesAllowed,
      incomingEventsAllowed = blueprint.incomingEventsAllowed,
      spawnRate = blueprint.spawnRate,
      difficultyMode = difficulty,
      mapBiome = mapBiome
    )
  }

  fun getAllLevelConfigs(
    difficulty: DifficultyMode = DifficultyMode.NORMAL,
    mapBiome: MapBiome = MapBiome.MAP_1_VOLCANO
  ): List<LevelConfig> {
    return (1..TOTAL_LEVELS).map { getLevelConfig(it, difficulty, mapBiome) }
  }

  /**
   * Dynamically generates modular obstacles from the 8-obstacle pool (`PENDULUM`, `LEDGE`,
   * `LAVA_GEYSER`, `FALLING_SPIKE`, `LASER_GATE`, `SAW_TRACK`, `NO_DRAW_ZONE`, `WIND_UPDRAFT`)
   * based on `LEVEL_DEFINITIONS[level - 1]`, scaling both frequency and difficulty as levels progress.
   */
  fun getHazards(
    level: Int,
    config: LevelConfig = getLevelConfig(level),
    difficulty: DifficultyMode = config.difficultyMode,
    mapBiome: MapBiome = config.mapBiome
  ): List<DynamicHazard> {
    val clampedLevel = level.coerceIn(1, TOTAL_LEVELS)
    if (clampedLevel <= 1) return emptyList()

    val blueprint = getBlueprint(clampedLevel)
    val allowed = blueprint.obstaclesAllowed
    if (allowed.isEmpty()) return emptyList()

    val t = (clampedLevel - 1f) / (TOTAL_LEVELS - 1f)
    val startPlatformEnd = 520f
    val effectiveLength = config.levelLength * blueprint.lengthMultiplier
    val finishX = startPlatformEnd + effectiveLength

    // Frequency scaling: spacing tightens from 680px (L2) down to 285px (L50)
    val baseHazardSpacing = (680f - t * 395f) / (blueprint.spawnRate * 0.45f + 0.55f).coerceIn(0.75f, 1.35f)
    val speedMult = difficulty.hazardSpeedMult

    // Difficulty scaling for Swinging Pendulums
    val pendulumAngularSpeed = (1.85f + t * 2.50f) * speedMult
    val pendulumMaxAngle = 0.52f + t * 0.50f
    val pendulumBobRadius = 18f + t * 8f

    // Difficulty scaling for Ledges (Cyan static/bouncy or Orange crumbling)
    val platformWidth = 150f - t * 58f
    val platformCycleSeconds = (4.2f - t * 2.3f) / speedMult.coerceAtLeast(0.6f)
    val platformVisibleRatio = (0.72f - t * 0.30f).coerceIn(0.35f, 0.85f)

    val hazards = mutableListOf<DynamicHazard>()
    var cursorX = startPlatformEnd + 240f
    var hazardIndex = 0

    while (cursorX < finishX - 180f) {
      // Deterministically cycle through the level's allowed obstacle pool
      val chosenType = if (clampedLevel == 25 && hazardIndex < 2) {
        // Ensure both PENDULUM and LEDGE appear early in Level 25 for balanced combo coverage
        if (hazardIndex == 0) HazardType.PENDULUM else HazardType.LEDGE
      } else if (clampedLevel == 50 && hazardIndex < 2) {
        if (hazardIndex == 0) HazardType.PENDULUM else HazardType.LEDGE
      } else {
        allowed[(hazardIndex + clampedLevel) % allowed.size]
      }

      val elevationShift = if (config.maxDeltaY <= 0.5f) {
        0f
      } else {
        sin((clampedLevel * 1.1f + hazardIndex * 1.45f).toDouble()).toFloat() * (config.maxDeltaY * 0.65f)
      }

      val phaseOffset = ((clampedLevel * 0.65f) + (hazardIndex * 1.15f)) % 6.28f
      val baseId = clampedLevel * 1000 + hazardIndex * 10

      when (chosenType) {
        HazardType.PENDULUM -> {
          val pStyle = blueprint.pendulumStyle
          val armLen = if (pStyle == PendulumStyle.FAST_SHORT) {
            132f
          } else {
            (160f + (hazardIndex % 3) * 14f).coerceIn(150f, 195f)
          }
          val pivotY = (WorldConstants.DEFAULT_GROUND_Y - armLen - 52f + elevationShift * 0.45f)
            .coerceIn(58f, 185f)
          val effSpeed = if (pStyle == PendulumStyle.FAST_SHORT) pendulumAngularSpeed * 1.25f else pendulumAngularSpeed

          hazards.add(
            DynamicHazard(
              id = baseId,
              type = HazardType.PENDULUM,
              x = cursorX,
              y = pivotY,
              armLength = armLen,
              bobRadius = pendulumBobRadius,
              maxAngleRadians = pendulumMaxAngle,
              angularSpeed = effSpeed,
              pendulumStyle = pStyle,
              phaseOffset = phaseOffset
            )
          )

          // If DOUBLE_SYNC, spawn a synchronized partner pendulum right beside it
          if (pStyle == PendulumStyle.DOUBLE_SYNC && cursorX + 96f < finishX - 160f) {
            hazards.add(
              DynamicHazard(
                id = baseId + 1,
                type = HazardType.PENDULUM,
                x = cursorX + 92f,
                y = pivotY,
                armLength = armLen,
                bobRadius = pendulumBobRadius,
                maxAngleRadians = pendulumMaxAngle,
                angularSpeed = effSpeed,
                pendulumStyle = pStyle,
                phaseOffset = phaseOffset + 0.55f
              )
            )
          }
        }

        HazardType.LEDGE -> {
          val stairBoost = if (clampedLevel == 18 || clampedLevel == 21) {
            -((hazardIndex % 4) * 28f)
          } else {
            0f
          }
          val topY = (WorldConstants.DEFAULT_GROUND_Y - 8f + elevationShift * 0.65f + stairBoost)
            .coerceIn(180f, 410f)
          val halfW = platformWidth * 0.5f
          val style = if (clampedLevel <= 14 && blueprint.ledgeStyle == LedgeStyle.CYAN_STATIC && hazardIndex % 2 == 1) {
            LedgeStyle.ORANGE_CRUMBLING
          } else {
            blueprint.ledgeStyle
          }

          hazards.add(
            DynamicHazard(
              id = baseId,
              type = HazardType.LEDGE,
              x = cursorX,
              y = topY,
              startX = cursorX - halfW,
              endX = cursorX + halfW,
              topY = topY,
              bottomY = topY + 16f,
              cycleSeconds = platformCycleSeconds,
              visibleRatio = platformVisibleRatio,
              ledgeStyle = style,
              movesInBiome = mapBiome.hasMovingPlatforms,
              phaseOffset = phaseOffset
            )
          )
        }

        HazardType.LAVA_GEYSER -> {
          val geyserTopY = (WorldConstants.DEFAULT_GROUND_Y - 95f + elevationShift * 0.35f)
            .coerceIn(165f, 290f)
          hazards.add(
            DynamicHazard(
              id = baseId,
              type = HazardType.LAVA_GEYSER,
              x = cursorX,
              y = WorldConstants.LAVA_SURFACE_Y,
              startX = cursorX - 18f,
              endX = cursorX + 18f,
              topY = geyserTopY,
              bottomY = WorldConstants.LAVA_SURFACE_Y,
              geyserTargetTopY = geyserTopY,
              geyserWidth = 34f,
              cycleSeconds = (2.5f / speedMult).coerceIn(1.3f, 3.2f),
              phaseOffset = phaseOffset
            )
          )
        }

        HazardType.FALLING_SPIKE -> {
          val ceilingY = (54f + (hazardIndex % 3) * 14f).coerceIn(48f, 92f)
          hazards.add(
            DynamicHazard(
              id = baseId,
              type = HazardType.FALLING_SPIKE,
              x = cursorX,
              y = ceilingY,
              startX = cursorX - 16f,
              endX = cursorX + 16f,
              topY = ceilingY,
              bottomY = ceilingY + 44f,
              triggerLeadX = 175f,
              spikeDropSpeed = 420f * speedMult,
              spikeHeight = 44f,
              phaseOffset = phaseOffset
            )
          )
        }

        HazardType.LASER_GATE -> {
          val gateTopY = 88f
          val gateBottomY = (WorldConstants.DEFAULT_GROUND_Y + 95f).coerceAtMost(WorldConstants.LAVA_SURFACE_Y - 18f)
          hazards.add(
            DynamicHazard(
              id = baseId,
              type = HazardType.LASER_GATE,
              x = cursorX,
              y = gateTopY,
              startX = cursorX - 12f,
              endX = cursorX + 12f,
              topY = gateTopY,
              bottomY = gateBottomY,
              laserHalfPeriodSeconds = (1.5f / speedMult).coerceIn(0.8f, 2.0f),
              phaseOffset = phaseOffset
            )
          )
        }

        HazardType.SAW_TRACK -> {
          val isVertical = (hazardIndex % 3 == 2) && clampedLevel >= 28
          val centerY = (WorldConstants.DEFAULT_GROUND_Y - 22f + elevationShift * 0.45f).coerceIn(190f, 395f)
          val tStartX = if (isVertical) cursorX else cursorX - 85f
          val tEndX = if (isVertical) cursorX else cursorX + 85f
          val tStartY = if (isVertical) (centerY - 75f).coerceAtLeast(130f) else centerY
          val tEndY = if (isVertical) (centerY + 75f).coerceAtMost(475f) else centerY

          hazards.add(
            DynamicHazard(
              id = baseId,
              type = HazardType.SAW_TRACK,
              x = cursorX,
              y = centerY,
              bobRadius = 22f,
              trackStartX = tStartX,
              trackStartY = tStartY,
              trackEndX = tEndX,
              trackEndY = tEndY,
              trackSpeed = (2.1f + t * 1.4f) * speedMult,
              phaseOffset = phaseOffset
            )
          )
        }

        HazardType.NO_DRAW_ZONE -> {
          val zoneHalfW = (62f + t * 26f).coerceIn(60f, 92f)
          val zoneTopY = (WorldConstants.DEFAULT_GROUND_Y - 95f + elevationShift * 0.35f).coerceIn(155f, 285f)
          val zoneBottomY = (WorldConstants.LAVA_SURFACE_Y - 12f)
          hazards.add(
            DynamicHazard(
              id = baseId,
              type = HazardType.NO_DRAW_ZONE,
              x = cursorX,
              y = zoneTopY,
              startX = cursorX - zoneHalfW,
              endX = cursorX + zoneHalfW,
              topY = zoneTopY,
              bottomY = zoneBottomY,
              phaseOffset = phaseOffset
            )
          )
        }

        HazardType.WIND_UPDRAFT -> {
          val draftHalfW = 48f
          val draftTopY = 85f
          val draftBottomY = WorldConstants.LAVA_SURFACE_Y - 8f
          hazards.add(
            DynamicHazard(
              id = baseId,
              type = HazardType.WIND_UPDRAFT,
              x = cursorX,
              y = draftTopY,
              startX = cursorX - draftHalfW,
              endX = cursorX + draftHalfW,
              topY = draftTopY,
              bottomY = draftBottomY,
              updraftAcceleration = 1520f * speedMult.coerceIn(0.85f, 1.35f),
              phaseOffset = phaseOffset
            )
          )
          // Pair wind updraft with an overhead spike or pendulum threat so ceiling drawing is rewarded
          if (hazardIndex % 2 == 0) {
            hazards.add(
              DynamicHazard(
                id = baseId + 2,
                type = HazardType.FALLING_SPIKE,
                x = cursorX + 18f,
                y = 56f,
                startX = cursorX + 2f,
                endX = cursorX + 34f,
                topY = 56f,
                bottomY = 100f,
                triggerLeadX = 190f,
                spikeDropSpeed = 410f * speedMult,
                spikeHeight = 44f,
                phaseOffset = phaseOffset
              )
            )
          }
        }
      }

      val jitterRatio = (((clampedLevel * 19 + hazardIndex * 43) % 41) - 20) / 100f
      val stepX = (baseHazardSpacing * (1f + jitterRatio * 0.30f)).coerceAtLeast(235f)
      cursorX += stepX
      hazardIndex++
    }

    return hazards
  }

  /**
   * Generates Breakable Rock Walls and Leaping Lava Monsters for Ignis Titan Edition combat.
   */
  fun getCombatHazards(
    level: Int,
    config: LevelConfig = getLevelConfig(level),
    difficulty: DifficultyMode = config.difficultyMode
  ): Pair<List<BreakableRockWall>, List<LeapingLavaMonster>> {
    val clampedLevel = level.coerceIn(1, TOTAL_LEVELS)
    if (clampedLevel <= 1) return emptyList<BreakableRockWall>() to emptyList<LeapingLavaMonster>()

    val blueprint = getBlueprint(clampedLevel)
    val startPlatformEnd = 520f
    val effectiveLength = config.levelLength * blueprint.lengthMultiplier
    val finishX = startPlatformEnd + effectiveLength

    val walls = mutableListOf<BreakableRockWall>()
    val monsters = mutableListOf<LeapingLavaMonster>()

    val spacing = (820f - clampedLevel * 6.5f).coerceIn(420f, 820f)
    var cursorX = startPlatformEnd + 460f
    var idx = 0

    while (cursorX < finishX - 240f) {
      if (idx % 2 == 0 && clampedLevel >= 4) {
        val wallBottomY = (WorldConstants.DEFAULT_GROUND_Y - 6f).coerceIn(240f, 380f)
        val wallTopY = wallBottomY - 68f
        walls.add(
          BreakableRockWall(
            id = clampedLevel * 100 + idx,
            x = cursorX,
            topY = wallTopY,
            bottomY = wallBottomY,
            width = 30f
          )
        )
      } else {
        val peakY = (WorldConstants.DEFAULT_GROUND_Y - 48f).coerceIn(210f, 340f)
        monsters.add(
          LeapingLavaMonster(
            id = clampedLevel * 100 + idx,
            x = cursorX,
            baseY = WorldConstants.LAVA_SURFACE_Y,
            peakY = peakY,
            cycleSeconds = (2.6f / difficulty.hazardSpeedMult).coerceIn(1.4f, 3.2f),
            phaseOffset = (idx * 1.3f + clampedLevel * 0.5f) % 6.28f,
            radius = 18f
          )
        )
      }
      cursorX += spacing
      idx++
    }
    return walls to monsters
  }

  fun getLevel(
    levelNumber: Int,
    difficulty: DifficultyMode = DifficultyMode.NORMAL,
    mapBiome: MapBiome = MapBiome.MAP_1_VOLCANO
  ): LevelDefinition {
    val clamped = levelNumber.coerceIn(1, TOTAL_LEVELS)
    return if (clamped == 1) {
      createLevel1(difficulty, mapBiome)
    } else {
      createScaledLevel(clamped, difficulty, mapBiome)
    }
  }

  /**
   * Level 1 (Homeroom Sketch):
   * - Preserves exact spawn/finish geometry and floating Chalk pickups for smooth onboarding.
   */
  private fun createLevel1(
    difficulty: DifficultyMode,
    mapBiome: MapBiome
  ): LevelDefinition {
    val cfg = getLevelConfig(1, difficulty, mapBiome)
    val blueprint = getBlueprint(1)
    val groundY = WorldConstants.DEFAULT_GROUND_Y
    val startEndX = 520f
    val finishX = startEndX + cfg.levelLength // 1320f
    return LevelDefinition(
      levelNumber = 1,
      title = cfg.levelTitle,
      subtitle = cfg.levelSubtitle,
      runSpeed = cfg.runSpeed,
      spawnPosition = Vec2(x = 110f, y = groundY),
      finishLineX = finishX,
      levelEndX = finishX + 260f,
      maxInkPerLevel = cfg.maxInkPerLevel,
      inkRechargeRate = cfg.inkRechargeRate,
      parInkUsage = cfg.parInkUsage,
      maxStrokeLength = cfg.maxStrokeLength,
      lineLifespan = cfg.lineLifespan,
      baseSpeed = cfg.baseSpeed,
      levelLength = cfg.levelLength,
      minGap = cfg.minGap,
      maxGap = cfg.maxGap,
      maxDeltaY = cfg.maxDeltaY,
      worldTitle = cfg.worldTitle,
      difficultyMode = difficulty,
      mapBiome = mapBiome,
      obstaclesAllowed = blueprint.obstaclesAllowed,
      platforms = listOf(
        Platform(
          startX = 20f,
          endX = startEndX,
          topY = groundY,
          label = "START"
        ),
        Platform(
          startX = finishX - 30f,
          endX = finishX + 260f,
          topY = groundY,
          label = "FINISH"
        )
      ),
      guideStrokes = listOf(
        GuideStroke(
          points = listOf(
            Vec2(500f, groundY - 4f),
            Vec2(640f, groundY - 28f),
            Vec2(780f, groundY - 28f),
            Vec2(920f, groundY - 42f),
            Vec2(1060f, groundY - 28f),
            Vec2(1200f, groundY - 24f),
            Vec2(finishX + 20f, groundY - 4f)
          ),
          caption = "DRAW LINE TO CHALK FOR +CHALK!"
        )
      ),
      chalkBits = listOf(
        ChalkBit(id = 1, x = 620f, y = groundY - 28f, inkAmount = 240f),
        ChalkBit(id = 2, x = 740f, y = groundY - 28f, inkAmount = 240f),
        ChalkBit(id = 3, x = 860f, y = groundY - 36f, inkAmount = 240f),
        ChalkBit(id = 4, x = 980f, y = groundY - 42f, isStar = true, inkAmount = 340f),
        ChalkBit(id = 5, x = 1100f, y = groundY - 32f, inkAmount = 240f),
        ChalkBit(id = 6, x = 1220f, y = groundY - 26f, inkAmount = 240f)
      ),
      hazards = getHazards(1, cfg, difficulty, mapBiome),
      calloutScript = listOf(
        ScriptedCallout(triggerX = 760f, type = CalloutMoveType.BLITZ)
      )
    )
  }

  /**
   * Generates Levels 2..50 deterministically using `LEVEL_DEFINITIONS`, `getLevelConfig`, and `getHazards`.
   */
  private fun createScaledLevel(
    levelNumber: Int,
    difficulty: DifficultyMode,
    mapBiome: MapBiome
  ): LevelDefinition {
    val cfg = getLevelConfig(levelNumber, difficulty, mapBiome)
    val blueprint = getBlueprint(levelNumber)
    val bits = mutableListOf<ChalkBit>()
    val callouts = mutableListOf<ScriptedCallout>()
    val guides = mutableListOf<GuideStroke>()

    val spawnX = 110f
    var currentY = WorldConstants.DEFAULT_GROUND_Y
    val startPlatformEnd = 520f
    val effectiveLength = cfg.levelLength * blueprint.lengthMultiplier
    val finishX = startPlatformEnd + effectiveLength
    val finishPlatformEnd = finishX + 260f

    var cursorX = startPlatformEnd
    var segmentIndex = 0
    val guidePts = mutableListOf(Vec2(startPlatformEnd - 20f, currentY - 4f))

    // High collectible density on Level 19 ("Gold Ink Express")
    val gapScale = if (levelNumber == 19) 0.65f else 1.0f
    val calloutInterval = max(2, (5f / difficulty.incomingFreq).toInt())

    while (cursorX < finishX - cfg.minGap * 0.6f) {
      val hashRatio = ((levelNumber * 37 + segmentIndex * 73) % 101) / 100f
      val stepX = (cfg.minGap + (cfg.maxGap - cfg.minGap) * hashRatio) * gapScale
      cursorX += stepX
      if (cursorX >= finishX - 50f) break

      val targetY = if (cfg.maxDeltaY <= 0.5f) {
        WorldConstants.DEFAULT_GROUND_Y - 24f
      } else {
        val wave = sin((levelNumber * 1.3f + segmentIndex * 1.15f).toDouble()).toFloat()
        val rawDelta = wave * (cfg.maxDeltaY * 0.65f)
        (WorldConstants.DEFAULT_GROUND_Y - 22f + rawDelta).coerceIn(180f, 405f)
      }
      currentY = targetY

      val isGoldStar = (levelNumber == 19 && segmentIndex % 2 == 0) || (segmentIndex % 4 == 2)
      bits.add(
        ChalkBit(
          id = levelNumber * 100 + segmentIndex,
          x = cursorX,
          y = currentY,
          inkAmount = if (isGoldStar) 340f else 240f,
          isStar = isGoldStar
        )
      )

      if (levelNumber <= 3 && guidePts.size < 6) {
        guidePts.add(Vec2(cursorX, currentY))
      }

      if (blueprint.incomingEventsAllowed.isNotEmpty() && segmentIndex > 0 && segmentIndex % calloutInterval == 0) {
        val allowedEvents = blueprint.incomingEventsAllowed
        val moveType = allowedEvents[(levelNumber + segmentIndex) % allowedEvents.size]
        callouts.add(
          ScriptedCallout(
            triggerX = (cursorX - 90f).coerceAtLeast(spawnX + 180f),
            type = moveType
          )
        )
      }

      segmentIndex++
    }

    val finishTopY = if (cfg.maxDeltaY <= 0.5f) {
      WorldConstants.DEFAULT_GROUND_Y
    } else {
      val finalDelta = sin((levelNumber * 2.7).toDouble()).toFloat() * (cfg.maxDeltaY * 0.35f)
      (WorldConstants.DEFAULT_GROUND_Y + finalDelta).coerceIn(265f, 365f)
    }

    // Level 50 Grand Finale ("Inferno Overlord"): Massive x20 MAGNET star/pill finale before the finish flag!
    if (blueprint.hasMagnetFinale) {
      val finaleStartX = (finishX - 660f).coerceAtLeast(startPlatformEnd + 300f)
      for (i in 0 until 20) {
        val ratio = i / 19f
        val fx = finaleStartX + ratio * 600f
        val archY = (finishTopY - 36f - sin(ratio * Math.PI.toFloat()) * 95f).coerceIn(150f, 430f)
        bits.add(
          ChalkBit(
            id = 50000 + i,
            x = fx,
            y = archY,
            inkAmount = 320f,
            isStar = (i % 2 == 0)
          )
        )
      }
    }

    if (levelNumber <= 3 && guidePts.size >= 2) {
      guidePts.add(Vec2(finishX + 15f, finishTopY - 4f))
      guides.add(
        GuideStroke(
          points = guidePts,
          caption = "DRAW LINE TO CHALK FOR +CHALK!"
        )
      )
    }

    val platforms = listOf(
      Platform(
        startX = 20f,
        endX = startPlatformEnd,
        topY = WorldConstants.DEFAULT_GROUND_Y,
        label = "START"
      ),
      Platform(
        startX = finishX - 30f,
        endX = finishPlatformEnd,
        topY = finishTopY,
        label = "FINISH"
      )
    )

    val (rockWalls, lavaMonsters) = getCombatHazards(levelNumber, cfg, difficulty)
    val bossHp = when {
      levelNumber == 50 -> 8
      levelNumber % 10 == 0 -> 6
      else -> 5
    }
    val scoreMult = when {
      levelNumber >= 50 -> 5.0f
      levelNumber >= 40 -> 3.5f
      levelNumber >= 25 -> 2.2f
      levelNumber >= 10 -> 1.5f
      else -> 1.0f + (levelNumber - 1) * 0.04f
    }

    return LevelDefinition(
      levelNumber = levelNumber,
      title = cfg.levelTitle,
      subtitle = cfg.levelSubtitle,
      runSpeed = cfg.runSpeed,
      spawnPosition = Vec2(x = spawnX, y = WorldConstants.DEFAULT_GROUND_Y),
      finishLineX = finishX,
      levelEndX = finishPlatformEnd - 40f,
      maxInkPerLevel = cfg.maxInkPerLevel,
      inkRechargeRate = cfg.inkRechargeRate,
      parInkUsage = cfg.parInkUsage,
      maxStrokeLength = cfg.maxStrokeLength,
      lineLifespan = cfg.lineLifespan,
      baseSpeed = cfg.baseSpeed,
      levelLength = effectiveLength,
      minGap = cfg.minGap,
      maxGap = cfg.maxGap,
      maxDeltaY = cfg.maxDeltaY,
      worldTitle = cfg.worldTitle,
      difficultyMode = difficulty,
      mapBiome = mapBiome,
      obstaclesAllowed = blueprint.obstaclesAllowed,
      platforms = platforms,
      guideStrokes = guides,
      chalkBits = bits,
      thumbtacks = emptyList(),
      scribblers = emptyList(),
      hazards = getHazards(levelNumber, cfg, difficulty, mapBiome),
      rockWalls = rockWalls,
      lavaMonsters = lavaMonsters,
      bossMaxHp = bossHp,
      scoreMultiplier = scoreMult,
      calloutScript = callouts
    )
  }
}
