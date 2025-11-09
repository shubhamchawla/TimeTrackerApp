# Minute Mark Chimes Configuration Guide

## Overview
The Minute Mark Chimes feature allows you to set **different sounds for each minute mark** (10th, 20th, 30th, 40th, 50th, and top of hour).

## How It Works

### Example Scenario
Let's say you configure the following:
- **:10 minute** → sound1.wav
- **:20 minute** → sound2.wav
- **:30 minute** → sound3.mp3
- **:40 minute** → Alarm Sound
- **:50 minute** → Ringtone
- **:00 (Top of hour)** → Default Notification

### When You Select "Every 10th Minute"
The app will play **sound1.wav** at:
- 9:10, 9:20, 9:30, 9:40, 9:50, 10:00, 10:10, 10:20, etc.

But wait! Each minute mark uses its **own configured sound**:
- At 9:10 → plays **sound1.wav** (configured for :10)
- At 9:20 → plays **sound2.wav** (configured for :20)
- At 9:30 → plays **sound3.mp3** (configured for :30)
- At 9:40 → plays **Alarm Sound** (configured for :40)
- At 9:50 → plays **Ringtone** (configured for :50)
- At 10:00 → plays **Default Notification** (configured for :00)

## Configuration Steps

### Step 1: Configure Sounds
1. Open the app
2. Click **"Configure Minute Mark Chimes"** button
3. For each minute mark, select your preferred sound:
   - :10 minute → Choose from available sounds
   - :20 minute → Choose from available sounds
   - :30 minute → Choose from available sounds
   - :40 minute → Choose from available sounds
   - :50 minute → Choose from available sounds
   - :00 (Top of hour) → Choose from available sounds
4. Click **"Save"**

### Step 2: Start the Service
1. Select an interval from the dropdown:
   - "Every 10th minute" - plays at :10, :20, :30, :40, :50, :00
   - "Every 20th minute" - plays at :20, :40, :00
   - "Every 30th minute" - plays at :30, :00
   - "Every 40th minute" - plays at :40, :00 (then :20 next hour)
   - "Every 50th minute" - plays at :50, :00 (then :40 next hour)
   - "Top of hour" - plays only at :00
2. Click **"Start Service"**

## Technical Details

### Data Persistence
- Settings are saved to **SharedPreferences** with key "MinuteMarkChimes"
- Each minute mark stores:
  - `{minute}_isSystemSound` - boolean indicating if it's a system sound
  - `{minute}_resourceId` - resource ID of the sound
  - `{minute}_name` - display name of the sound

### Sound Selection Logic
When an alarm triggers:
1. The receiver checks the current minute (e.g., 25)
2. For "Every 10th minute", it rounds to the nearest mark (20)
3. It looks up the configured sound for that minute mark
4. If a custom sound is configured, it uses that
5. If no custom sound is configured, it uses the default sound from the main spinner

### Scheduling Algorithm
For "Every 10th minute":
- Current time: 9:17
- Next trigger: 9:20 (next 10-minute mark)
- After 9:20, schedules for 9:30
- After 9:30, schedules for 9:40
- And so on...

## Use Cases

### Different Sounds for Different Times
- **:00** - Loud alarm (top of hour)
- **:30** - Gentle chime (half hour)
- **:10, :20, :40, :50** - Soft notification

### Productivity Timer
- **:00** - "Break time" sound
- **:25** - "Focus time" sound
- **:50** - "Wrap up" sound

### Meditation Timer
- **:00** - Bell sound (start)
- **:10** - Soft chime
- **:20** - Medium chime
- **:30** - Bell sound (halfway)
- **:40** - Medium chime
- **:50** - Soft chime

## Tips

1. **Test Your Sounds**: Use the "Test" button next to the sound spinner to preview sounds before configuring
2. **Mix System and Custom Sounds**: You can use both system sounds (Notification, Alarm, Ringtone) and custom sounds from the raw folder
3. **Disable Configuration While Running**: The configuration buttons are disabled while the service is running to prevent conflicts
4. **Settings Persist**: Your minute mark configurations are saved even after closing the app

## Troubleshooting

**Q: The wrong sound is playing**
- A: Make sure you've saved your minute mark configuration and restarted the service

**Q: All minute marks play the same sound**
- A: Check that you've configured different sounds for each minute mark in the "Configure Minute Mark Chimes" dialog

**Q: Settings are lost after restart**
- A: This shouldn't happen as settings are saved to SharedPreferences. If it does, please check app permissions.
