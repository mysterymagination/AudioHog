# AudioHog
A simple tool for debugging audio focus issues on Android.  
Android tends to give audio focus to whoever requested it last, so audio focus interruptions can be difficult to prevent.  As such, it is important for multimedia apps to handle loss/gain of audio focus gracefully.  To that end, AudioHog takes and abandons focus over configurable audio streams on command, for configurable durations.  It also builds a notification so that interfering audio can be played/paused without leaving the debug app.
