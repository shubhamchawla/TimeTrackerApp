# Custom Sound Picker Feature for Minute Mark Chimes

## Overview
Users can now select custom sounds from their system (ringtones, notifications, alarms, music files) for minute mark chimes, in addition to the built-in sounds.

## Changes Made

### 1. UI Updates (`dialog_minute_mark_chime.xml`)
- Added "Browse..." buttons next to each minute mark spinner
- Buttons allow users to open the system sound picker

### 2. Data Model Updates (`MainActivity.kt`)
- Updated `SoundInfo` data class to include `customUri` field for storing custom sound URIs
- Added `soundPickerLauncher` activity result handler to receive selected sounds
- Added `currentMinuteMarkForPicker` to track which minute mark is being customized

### 3. Sound Selection (`MainActivity.kt`)
- Added `openSoundPicker()` function to launch the system ringtone picker
- Added `handleCustomSoundSelected()` function to process selected custom sounds
- Takes persistable URI permissions to ensure sounds remain accessible
- Extracts sound name from URI using RingtoneManager

### 4. Settings Persistence (`MainActivity.kt`)
- Updated `saveMinuteMarkChimeSettings()` to save custom URIs
- Updated `loadMinuteMarkChimeSettings()` to load custom URIs
- Updated `passMinuteMarkChimeSettings()` to pass custom URIs to the service

### 5. Sound Playback (`SoundReceiver.kt`)
- Added `playCustomUriSound()` function to play sounds from custom URIs
- Updated `handleHourlyMinuteMarkChime()` to check for and play custom URI sounds
- Updated `copyMinuteMarkChimeSettings()` to include custom URIs in intent extras

### 6. Service Integration (`SoundService.kt`)
- Updated `copyMinuteMarkChimeSettings()` to include custom URIs when scheduling alarms

## How It Works

1. User opens "Minute Mark Chimes" dialog
2. User clicks "Browse..." button next to any minute mark
3. System ringtone picker opens showing all available sounds
4. User selects a sound (ringtone, notification, alarm, or music file)
5. App takes persistable URI permission for the selected sound
6. Custom sound is saved to SharedPreferences with its URI
7. When the minute mark triggers, the app plays the custom sound from the URI

## Technical Details

### URI Permissions
- Uses `takePersistableUriPermission()` to maintain access to custom sounds
- Ensures sounds remain playable even after app restart

### Sound Priority
When playing minute mark chimes, the priority is:
1. Custom URI sound (if set)
2. System sound (notification/alarm/ringtone)
3. Built-in raw resource sound

### Resource ID Values
- `-1`: No sound (silent)
- `-2`: Custom URI sound
- `0-2`: System sounds (notification, alarm, ringtone)
- `>2`: Built-in raw resource sounds

## User Experience

Users can now:
- Select any sound from their device for minute mark chimes
- Mix and match between built-in sounds and custom sounds
- Use their favorite music or custom ringtones as chime sounds
- Change sounds at any time through the dialog

## Testing

To test the feature:
1. Open the app and tap "Minute Mark Chimes"
2. Click "Browse..." next to any minute mark (e.g., :10)
3. Select a custom sound from the picker
4. Save the settings
5. Start the service with "No repeat" interval
6. Wait for the minute mark to trigger
7. Verify the custom sound plays
