# Draw to Survive — Standalone Web Port with Matter.js Physics

Port the complete Draw to Survive (Chalk Runner Evolved) experience—including authentic sketchbook UI, 4 curated levels + infinite procedural generator, 4 chalk tools, Red Scribbler enemies, thumbtack hazards, dynamic particle systems, and Web Audio sound effects—into a single self-contained `index.html` file in the project root driven by the Matter.js 2D rigid-body physics engine.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed via interactive clarification:
> - **Physics Engine**: Matter.js rigid-body physics loaded from a dependable CDN (`https://cdnjs.cloudflare.com/ajax/libs/matter-js/0.19.0/matter.min.js`) with an automatic offline fallback to built-in SAT/Verlet physics if the CDN is unreachable.
> - **Display & Controls**: Responsive full-viewport chalkboard canvas supporting mobile touch drag and desktop pointer interactions, complemented by keyboard hotkeys (`1`-`4` for chalk tools, `Space` for pause/resume, `R` for retry).
> - **Deployment Target**: Standalone `index.html` at the repository root ready for 1-click GitHub Pages hosting.

---

## 1. Overview & Core Concept

- **What It Does**: A complete, browser-playable 2D line-drawing physics platformer. The stickman runner automatically charges across a charcoal sketchbook world. The player draws solid chalk platforms, ramps, and springboards in real time to bridge lava gaps, vault hazards, bounce over obstacles, and ram enemies.
- **Target Audience**: Anyone opening the game link in any desktop or mobile browser (Chrome, Safari, Firefox, Edge, Android Chrome, iOS Safari) without requiring an APK download or build step.
- **Key Value**: Direct browser parity with the native Android applet, fully self-contained in `index.html` with zero build tools, bundlers, or server dependencies.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Start Screen**: Charcoal blackboard with Cabin Sketch typography, animated embers rising from the bottom lava pit, level selector, high-score memory, and a chalk-hatched **PLAY** button.
2. **Gameplay & Drawing**: Smooth finger/mouse drawing with ink depletion meter, dynamic Chaikin path smoothing, chalk shavings, and instant conversion into solid Matter.js static bodies.
3. **Multi-Chalk Switching**:
   - **White Chalk (`1`)**: Solid standard surface; steep strokes act as vault hurdles.
   - **Bounce Blue (`2`)**: High-restitution trampoline pads with vertical spring launch.
   - **Turbo Gold (`3`)**: Permanent steel chalk granting speed multipliers and invulnerability through thumbtacks.
   - **Cutter (`4`)**: Tap-to-cut tool that dissolves drawn strokes, refunds 60% chalk, and emits an enemy-stunning shockwave.
4. **Callout Moves**: Dynamic speech bubble alerts (`JUMP!`, `BLITZ!`, `LOW GRAV!`, `ERASER!`) with burning fuse countdowns.
5. **Game Over & Level Complete**: Sketchbook overlay cards with star grading, ink efficiency metrics, and instant Retry / Next Level triggers.

### Visual Identity & Blackboard Aesthetic
- **Board Surface**: Deep slate charcoal (`#1f2c26` to `#2b3c34`) textured with SVG fractal noise grain and subtle chalk dust specks.
- **Chalk Palette**:
  - Primary Chalk: `#efebe0`
  - Bounce Pad: `#7ad3e6`
  - Turbo Gold: `#f5d98b`
  - Danger / Lava / Scribblers: `#e85d45` to `#f39cab`
- **Typography**: Google Fonts `Cabin Sketch` (headings & HUD counters) and `Patrick Hand` (body & instructions), with fallback to cursive handwriting.

---

## 3. Technical Architecture & Matter.js Physics Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                       index.html (Root)                         │
├────────────────────────────────┬────────────────────────────────┤
│         DOM & UI Layer         │      Matter.js Physics World   │
│  - Start / Pause / Game Over   │  - Engine & Runner (60 Hz)     │
│  - Tool Dock & Hotkey Handler  │  - Stickman Composite Body:    │
│  - Real-time Ink Gauge         │    • Circle Torso & Head       │
│  - Audio Synthesizer (WebAudio)│    • Frictionless Foot Wheel   │
│                                │    • Joint Constraints         │
├────────────────────────────────┼────────────────────────────────┤
│     Touch / Pointer Input      │     Static & Dynamic Bodies    │
│  - PointerDown / Move / Up     │  - Static Platform Rectangles  │
│  - Catmull-Rom / Chaikin Smooth│  - Drawn Stroke Segment Chains │
│  - Length & Ink Budget Clamp   │  - Red Scribbler Physics Units │
│  - Substep Collision & Cutting │  - Chalk Stub Sensor Triggers  │
├────────────────────────────────┴────────────────────────────────┤
│                   HTML5 Canvas Render Pipeline                  │
│   - Charcoal Background + Parallax Dust                         │
│   - Animated Multi-layer Lava Waves + Rising Embers             │
│   - Textured Chalk Stroke Renderer with Jitter Edges            │
│   - Kinematic Stickman Character with Running Limb IK           │
│   - Particle Emitters (Footstep Chalk, Sparks, Graphite Shavings)│
└─────────────────────────────────────────────────────────────────┘
```

### Matter.js Rigidity & Collision Mapping
- **Stickman Composite**: Built using `Matter.Bodies.circle` for the foot wheel and torso with a stiff vertical constraint, preventing snagging on polygon segment vertices.
- **Platforms**: Static rectangular bodies (`Matter.Bodies.rectangle(..., { isStatic: true })`).
- **Drawn Lines**: Each smoothed line segment is converted into a thin static chamfered rectangle (`Matter.Bodies.rectangle`) or compound body oriented along the segment vector, tagged with tool properties (restitution for Bounce, velocity boost on contact for Turbo).
- **Collision Events**: `Matter.Events.on(engine, 'collisionStart')` monitors foot contact for ground detection, spring launch impulse, hazard collision, and enemy stomping.
- **Offline Resilience**: Includes a compact fallback solver so that if the CDN is blocked or offline, the game continues without interruption.

---

## 4. Implementation Steps

1. **Root `index.html` Construction**:
   - Create `/index.html` containing the complete CSS blackboard styles, SVG filter definitions, and responsive canvas wrapper.
   - Embed Matter.js CDN script with fallback.
2. **Audio System**:
   - Implement zero-asset Web Audio API synthesizer for chalk scratching, jumping, bounce pad springs, collectible chimes, and game over stings.
3. **Levels & Catalog**:
   - Port all 4 curated levels (`Homeroom Sketch`, `Recess Bounce`, `Field Trip Canyon`, `Final Exam Gauntlet`) and the procedural level generator matching `LevelCatalog.kt`.
4. **Drawing & Physics Integration**:
   - Implement pointer event capture (mouse, touch, stylus) with world-to-screen transform and camera tracking.
   - Connect line completion directly to Matter.js static body generation.
5. **Testing & Verification**:
   - Verify layout responsiveness on mobile and desktop viewports.
   - Confirm collision responsiveness and zero tunneling across all 4 levels.
