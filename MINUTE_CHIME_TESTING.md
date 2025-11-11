# Minute Mark Chime Testing Guide

## Quick Test Steps

### 1. Configure Minute Mark Chimes
1. Open the Sound Repeater app
2. Click "🕐 Configure Minute Mark Chimes" button
3. Set sounds for the 10-minute marks you want to test:
   - **10th Minute**: Select any sound (e.g., "Default Notification")
   - **20th Minute**: Select any sound (e.g., "Alarm Sound")
   - **30th Minute**: Select any sound
   - **40th Minute**: Select any sound
   - **50th Minute**: Select any sound
   - **Top of Hour (0)**: Select any sound
4. Click "Save"

### 2. Start the Service
1. The "Repeat Interval" section should be hidden
2. Click "▶ Start Service" button
3. You should see:
   - Status: "Service running - Playing once (minute mark chimes active)"
   - Notification: "Sound Repeater Active - Playing sound once (minute mark chimes active)"

### 3. Monitor Logs
Open Android Studio Logcat and filter by "SoundReceiver" or "SoundService" to see:

**Expected logs at each 10-minute mark:**
```
========== MINUTE MARK CHIME TRIGGERED ==========
Current time: 14:10
Current minute: 10
Minute mark detected: :10
Custom sound found - isSystemSound: true, resourceId: 0
✓ Playing minute mark chime for :10 (system sound)
Scheduling next minute mark chime at 14:20
✓ Minute mark chime scheduled (exact) at 14:20
```

### 4. Verify Behavior
- [ ] Service starts successfully
- [ ] Notification shows "minute mark chimes active"
- [ ] Service stays running (doesn't stop after first sound)
- [ ] Sound plays at :10 minute mark
- [ ] Sound plays at :20 minute mark
- [ ] Sound plays at :30 minute mark
- [ ] Sound plays at :40 minute mark
- [ ] Sound plays at :50 minute mark
- [ ] Sound plays at :00 (top of hour)
- [ ] Logs show detailed information for each trigger
- [ ] Toast notification appears: "🔔 Minute Mark Chime :XX!"

## Fast Testing (Don't Wait 10 Minutes)

To test quickly without waiting for 10-minute intervals:

### Option 1: Change System Time
1. Go to Android Settings → System → Date & Time
2. Turn off "Automatic date & time"
3. Manually set time to 1-2 minutes before a 10-minute mark (e.g., 14:08)
4. Start the service
5. Wait 2 minutes for the chime to trigger at 14:10
6. Repeat for other minute marks

### Option 2: Check Initial Scheduling
1. Start the service
2. Check logcat immediately for:
   ```
   Scheduling initial minute mark chime at HH:MM
   ✓ Initial minute mark chime scheduled (exact) at HH:MM
   ```
3. This confirms the alarm is properly scheduled

## Troubleshooting

### No sound at 10-minute mark?
- Check if minute mark chimes are configured (not set to "No Sound")
- Verify service is still running (check notification)
- Check logcat for error messages
- Ensure exact alarm permission is granted (Android 12+)

### Service stops after first sound?
- This was the bug that's now fixed
- Ensure you're using the updated code
- Check logcat for "No repeat but minute mark chimes are configured - keeping service running"

### Logs show wrong minute mark?
- The detection uses a tolerance window (±4 minutes)
- Minute 10-14 → detected as :10
- Minute 20-24 → detected as :20
- etc.

## Expected Logcat Output Pattern

Every 10 minutes you should see this pattern:
```
D/SoundReceiver: ========== MINUTE MARK CHIME TRIGGERED ==========
D/SoundReceiver: Current time: HH:MM
D/SoundReceiver: Current minute: MM
D/SoundReceiver: Minute mark detected: :MM
D/SoundReceiver: Custom sound found - isSystemSound: true/false, resourceId: X
D/SoundReceiver: ✓ Playing minute mark chime for :MM
D/SoundReceiver: Scheduling next minute mark chime at HH:MM
D/SoundReceiver: ✓ Minute mark chime scheduled (exact) at HH:MM
```

## Success Criteria
✅ Service starts with "No repeat" default
✅ Repeat interval section is hidden
✅ Service stays running when minute mark chimes are configured
✅ Sounds play at every 10-minute mark (0, 10, 20, 30, 40, 50)
✅ Detailed logs are created for each chime event
✅ Service continues running indefinitely
✅ Toast notifications appear at each chime
