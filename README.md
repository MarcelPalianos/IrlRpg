# Match3 Mobile (Kotlin / Android)

Kotlin-only Android starter project for a basic Match-3 game.

## What is Included

- Android app module in `app/`
- `Match3Engine` for game logic:
  - swap validation
  - horizontal/vertical match detection
  - clear + collapse + refill cascades
  - score tracking
- `GameView` custom touch view:
  - tap one cell, then tap an adjacent cell to swap
  - selected cell highlight
  - score rendered on screen
- `MainActivity` wiring everything together

## Run in Android Studio

1. Open this folder as a project in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on an emulator or Android device.

## Gameplay

- Tap one gem to select it.
- Tap an adjacent gem to attempt a swap.
- Invalid moves (no match created) are reverted.
- Each cleared gem gives `10` points.
