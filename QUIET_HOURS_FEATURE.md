# Quiet Hours Feature

## Overview
Added a "Quiet Hours" feature that allows users to set sleeping hours when sounds will not play. This is perfect for preventing disturbances during nighttime or any designated quiet period.

## Features

### User Configuration
- **Enable/Disable Toggle**: Users can easily turn quiet hours on or off
- **Custom Time Range**: Set any start and end time for quiet hours
- **Default Settings**: Pre-configured with 2:00 AM - 9:00 AM as default sleeping hours
- **12-Hour Time Format**: User-friendly time picker with AM/PM display
- **Overnight Support**: Handles time ranges that span midnight (e.g., 10 PM to 6 AM)

### How It Works
1. **Configuration Button**: New "🌙 Configure Quiet Hours" button in the main activity
2. **Time Selection**: Material Design time pickers for start and end times
3. **Persistent Settings**: Settings are saved using SharedPreferences
4. **Smart Checking**: Before playing any sound, the app checks if current time falls within quiet hours
5. **Alarm Continuity**: Alarms continue to reschedule even during quiet hours (they just don't play sounds)
6. **Resumption Alert**: The first notification after quiet hours ends will play as an **Alarm** type sound (instead of standard notification) to ensure you are alerted that quiet hours have ended.

### Technical Implementation

#### Files Modified
1. **MainActivity.kt**
   - Added `quietHoursButton` UI element
   - Added `showQuietHoursDialog()` method for configuration
   - Time formatting and picker integration

2. **SoundReceiver.kt**
   - Added `isInQuietHours()` method to check current time against settings
   - Modified `onReceive()` to skip sound playback during quiet hours
   - Ensures alarms still reschedule properly even when skipped

3. **activity_main.xml**
   - Added quiet hours configuration button with moon emoji (🌙)
   - Styled with pink color (#EC4899) to distinguish from other buttons

4. **dialog_quiet_hours.xml** (New File)
   - Material Design dialog layout
   - Enable/disable switch
   - Start and end time selection buttons
   - Responsive UI that shows/hides time controls based on toggle

#### Quiet Hours Logic
The `isInQuietHours()` method handles two scenarios:
- **Normal Range**: Start time < End time (e.g., 9 AM to 5 PM)
- **Overnight Range**: Start time > End time (e.g., 10 PM to 6 AM)

Time comparison is done in minutes since midnight for accuracy.

### User Experience
- Button is always enabled, even when service is running, so users can adjust quiet hours anytime
- Clear visual feedback with formatted time display
- Toast notifications confirm settings changes
- Intuitive material design interface

### Example Use Cases
- **Night Sleep**: 10:00 PM - 7:00 AM
- **Afternoon Nap**: 1:00 PM - 3:00 PM  
- **Work Focus**: 9:00 AM - 12:00 PM
- **Custom Schedule**: Any time range that suits your needs

## Testing
To test the feature:
1. Open the app
2. Tap "🌙 Configure Quiet Hours"
3. Enable the toggle
4. Set your desired start and end times
5. Save the settings
6. Start the sound service
7. Verify sounds don't play during the configured quiet hours
8. Check that sounds resume after quiet hours end
