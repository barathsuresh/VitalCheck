# VitalCheck

A context monitoring Android app for CSE 535 Project 1.

**Name:** Barath Suresh
**ASU ID:** 1237353860

## Links

- GitHub repository (private): [VitalCheck](https://github.com/barathsuresh/VitalCheck)
- Demo video (unlisted YouTube): [VitalCheck demo](https://youtu.be/5vVTxEvOyhE)

## What the app does

VitalCheck measures heart rate and respiratory rate using the phone's own sensors,
collects symptom ratings from the user, and saves all of it to a local database.
Nothing is sent off the device.

There are three screens.

### Home

Two buttons. "Record Health Data" starts the measurement flow. "Delete All Recorded
Data" clears the database after a confirmation dialog, and is disabled when there is
nothing to delete.

Under the buttons the screen shows how many records are stored and lists each one
with all its values. The list comes from a Room Flow, so it updates by itself when a
row is added or removed.

### Vital signs

**Heart rate** uses the camera. The user covers the back camera and flash with a
fingertip, and the app records a 45 second video with the torch switched on. Light
from the flash passes through the finger and into the camera. Each heartbeat pushes
more blood into the finger, which absorbs a little more light, so the brightness of
the video frames drops slightly with every beat.

`HeartRateAnalyzer.kt` turns that into a bpm value:

1. It reads the video's real frame rate from the file metadata.
2. It picks a frame step so that it samples at about 10 Hz, whatever the camera
   actually recorded at.
3. For each sampled frame it averages R+G+B over a grid of about 64x64 pixels.
4. It subtracts a centred moving average about 3 seconds wide. That window is much
   longer than one heartbeat, so it removes slow changes from lighting and finger
   pressure but leaves the pulse.
5. It counts peaks in what is left, skipping any two peaks closer together than
   180 bpm would allow.
6. It divides the peak count by the actual elapsed seconds to get bpm.

Only the first 10 seconds or so of the video is analysed. Decoding a frame with
`MediaMetadataRetriever` takes tens of milliseconds, so going through all 1350 frames
would take several minutes. Ten seconds at 10 Hz is still about 100 samples, which is
enough for a resting pulse.

**Respiratory rate** uses the accelerometer. The user lies down and puts the phone
flat on their chest for 45 seconds. As the chest rises and falls the phone tilts, and
that shows up as changes in the gravity vector across the three axes.

The app collects samples as fast as the sensor will deliver them and stores a
timestamp with each one, then re-spaces them to a fixed 200 ms interval before
calculating. This part matters. The calculation compares each pair of consecutive
samples against a fixed threshold of 0.15, so the spacing between samples decides the
answer. If the samples are too close together, normal sensor noise crosses the
threshold constantly. If they are too far apart, breathing never crosses it. Android's
`SENSOR_DELAY_*` values are only hints and this phone delivered samples about every
66 ms no matter which one I asked for, so the spacing has to be fixed in code rather
than requested from the sensor.

The screen is kept on during both measurements. The phone sits untouched for 45
seconds, and if the screen locks many devices freeze the app's main thread, which
stops the capture.

### Symptoms

A dropdown with ten symptoms and a 0 to 5 star rating for each. Anything the user does
not rate stays at 0. "UPLOAD SYMPTOMS" saves one row with the 12 required values, the
10 symptom ratings plus heart rate and respiratory rate, then goes back to Home where
the new record shows up in the list.

## How it is built

| Part | Used |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose, one Activity with three destinations |
| Architecture | MVVM, an AndroidViewModel per screen |
| Database | Room |
| Camera | CameraX |
| Motion sensor | SensorManager, TYPE_ACCELEROMETER |
| Async | Kotlin coroutines |

```
com.barathsuresh.vitalcheck
├── MainActivity.kt
├── navigation/VitalsNavigation.kt
├── data/
│   ├── Symptoms.kt
│   ├── local/HealthRecordEntity.kt
│   ├── local/HealthRecordDao.kt
│   ├── local/AppDatabase.kt
│   └── repository/HealthRepository.kt
├── sensors/
│   ├── HeartRateCapture.kt
│   ├── HeartRateAnalyzer.kt
│   ├── HeartRateCalculator.kt          (provided helper, kept for reference)
│   ├── RespiratoryRateCapture.kt
│   └── RespiratoryRateCalculator.kt    (provided helper, used)
└── ui/
    ├── components/StarRating.kt
    └── screens/{home,vitals,symptoms}/
```

### Database

Table `records` in `vitalcheck.db`.

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | primary key, auto generated |
| `timestamp` | INTEGER | epoch millis |
| `heartRate` | INTEGER | bpm |
| `respiratoryRate` | INTEGER | breaths per minute |
| `nausea` ... `feelingTired` | INTEGER | 10 columns, 0 to 5 each |

I used ten separate symptom columns instead of a second table. The assignment asks
for one row with twelve values, and this way a row can be read on its own without a
join. The downside is that adding another symptom later would need a schema
migration.

### About the provided heart rate helper

`HeartRateCalculator.kt` is in the repo unchanged, but the app does not use its
result. It steps through the video 15 frames at a time, stops after 425 frames, and
divides the final number by a hardcoded 4 seconds. Those constants only work out if
the video is around 106 fps. My phone records at 30 fps, so it ends up sampling the
pulse at about 2 Hz, which is barely above the minimum needed for a 1.2 Hz heartbeat,
and then applies a 5 point moving average covering 2.5 seconds, which averages the
heartbeat out. Its last step is `count * 60 / 4`, so every possible answer is a
multiple of 15 bpm.

On my device it gave 45 bpm. The version that reads the real frame rate gave 83 bpm
for the same person at the same time.

## Building and running

- Android Studio
- minSdk 29, targetSdk 37, compileSdk 37
- Needs a real phone, since it uses the camera flash and the accelerometer
- Allow the Camera permission when asked

After the 45 second recording, the heart rate takes another 10 to 30 seconds to
process.

## Questions

### Q1. What specifications would you give to Health-Dev?

Health-Dev is a tool that writes the code for you. You describe what your system
senses and what it should compute, and it generates the sensor and phone code from
that description. So the answer is not what I would program. It is what I would tell
it.

It asks for three kinds of information.

**1. The sensors.**

For each signal I would say what kind of sensor it is, what hardware it runs on, and
how often it should be read. VitalCheck has two signals. One is a light reading from
the rear camera with the flash on, taken from a 45 second video. The other is an
accelerometer reading taken with the phone on the chest, also for 45 seconds.

Next I would name the algorithms to run on each signal. Health-Dev keeps a library of
ready-made algorithms and checks whether the name I give matches one it already has.
For heart rate it does. The paper lists a PPG pulse width calculator, which is exactly
the camera method this app uses. For breathing there is no match, so the paper says I
would have to write that one myself and add it to the library.

Then I would say what order things run in, by connecting each algorithm's output to
the next one's input:

```
camera frames -> average brightness -> remove drift -> count peaks -> heart rate
accelerometer -> combine the three axes -> count threshold crossings -> breathing rate
```

**2. The network.**

Health-Dev was built for systems where separate sensors talk to a phone over
Bluetooth or ZigBee. This section is for node addresses, routing and radio settings.

VitalCheck has none of that. Both sensors are inside the same phone that would
normally be the base station. So I would describe a single node with no routing and
the radio switched off. It fits, but only just.

**3. The phone screen.**

Most of this app lives here. I would list:

- The buttons and what each one does. Record Health Data, Measure Heart Rate, Measure
  Respiratory Rate, Upload Symptoms and Delete All Recorded Data. The measure buttons
  map onto Health-Dev's own start and stop sensing commands.
- The text to display. Heart rate in bpm, breathing rate in breaths per minute, and
  how many records are saved.
- Optionally a graph of the brightness signal, so the pulse can be seen being picked
  up.
- Which algorithms run on the phone and in what order, since in this app all the
  processing happens on the phone rather than on a separate sensor.
- Commands to change how often a sensor is read, which is how I would express the
  breathing rate re-spacing.

**Where Health-Dev would not be enough.**

Four things it could not do for me.

- Its hardware library covers small sensor boards like TelosB, Shimmer and Arduino.
  There is no entry for a phone's own camera as a sensor, so it has nothing to build
  the heart rate front end from.
- The paper says the phone screen generator only handles algorithms with one input and
  cannot express complicated sequences. This app records, analyses, moves to another
  screen, collects symptoms and then saves. That is more than it supports.
- It generates sensing and processing code but not storage. The database would still
  be written by hand.
- The paper's own conclusion says it cannot do adaptive thresholds. That is exactly the
  problem I ran into with the breathing calculation, where a single fixed number only
  works under one set of conditions.

So Health-Dev would handle the sensor reading and the signal processing, which is the
hardest part for someone who cannot program. The database, the screen flow and the
camera side would still be manual work.

### Q2. How would you use bHealthy to give feedback and model the user?

At the moment VitalCheck only measures and saves. It never tells the user anything.
bHealthy shows how to close that loop. It has five parts, and each one maps onto
something this app could do.

**Work out how the user is doing.** bHealthy scores wellbeing out of 10 by combining
sensor readings with how the person has been using the apps. VitalCheck already has
what it needs: heart rate, breathing rate, and ten symptom ratings. It could turn
those into one score per session. A high breathing rate on its own means something
very different from a high breathing rate when the user has also reported
breathlessness, and the symptom rating is what tells those two apart.

**Suggest something useful.** bHealthy recommends activities that are known to help
with a particular state. Here that could mean suggesting a breathing exercise when the
breathing rate is up and the user reported breathlessness, or rest and a short walk
when the heart rate is up and they reported feeling tired.

**The new app I would build: a breathing trainer.** This is based on how bHealthy's
BrainHealth app works. The user lies down with the phone on their chest, exactly as
they already do for the breathing measurement. The app shows a pace to follow while
the accelerometer measures what they are actually doing. BrainHealth uses particles on
screen that pull together when the user is doing well and scatter when they are not,
and the same idea works here. It reuses the sensor code that already exists and turns a
one-off measurement into a loop: measure, give feedback, measure again.

**Show progress over time.** bHealthy produces a report by looking at a period of
stored data. The database already saves a timestamp with every record, so showing
trends over weeks is just a matter of reading it back.

**Building a picture of the user.** This is the most useful part. bHealthy points out
that its sensor layer creates a shared database that all the other apps read from, and
that this shared database is what lets them work together. The `records` table is
already that database.

Once there are enough rows it stops being a list of readings and becomes a description
of one specific person. Their normal resting heart rate. Their usual breathing rate.
Which symptoms tend to appear together.

That improves the sensing in two ways.

First, the fixed numbers could become personal ones. The breathing calculation uses a
single threshold for everybody, and I found out by testing that it only works under
one particular set of conditions. With a history for each user, that threshold could
come from how deeply that person actually breathes. That is the adaptive thresholding
Health-Dev says it cannot generate, and it would remove the most fragile number in my
code.

Second, the symptom ratings are labels the user writes themselves. Every row pairs
sensor readings with how that person actually felt at the time. That makes the saved
data a training set. The app could learn that a slightly high heart rate is normal for
this particular user when they have a mild fever, instead of comparing them against an
average of everybody.

### Q3. Is mobile computing mostly app development?

My view did change, and it changed because of where the time actually went.

I expected the hard part to be Android itself. The screens, the navigation, the
database. That turned out to be the easy part. Room, Compose and Navigation all worked
the way the documentation said they would, and once I understood the patterns the three
screens came together quickly. If mobile computing were mostly app development, I would
have been nearly finished at that point.

Almost everything that actually cost me time was underneath the app.

The breathing rate is the clearest example. The calculation compares two accelerometer
readings that sit next to each other, so whether it works depends entirely on how far
apart those readings are in time. I assumed that asking Android for a particular speed
would give me that speed. It does not. Those settings are requests, not promises, and
my phone delivered readings far more often than I asked for. The fix was not an API
call. It was realising that the spacing between readings was part of the signal
processing, and correcting it myself before doing any maths.

Heart rate was the same problem, worse. The provided code was built around numbers that
only make sense if the camera records several times faster than mine does. At my
phone's speed it was looking at the pulse too rarely to see it, and then smoothing over
a window wide enough to erase it. What gave it away was not an error message. It was the
shape of the answers it produced, which could only ever land on certain values. Fixing
it meant reading the video's real speed while the app runs and working everything out
from that. That is a sampling problem, not a programming one.

Even the tools turned into a systems problem. I could not use Android Studio's database
inspector, because the security policy on my phone has no entry for debuggable apps, so
no debugger could attach at all. What I did instead was make the app display its own
saved records on screen. That is an architecture decision forced by a platform rule.

So I no longer think mobile computing is mostly app development. The app is how the
work reaches the user. The subject itself is everything underneath.

It is signal processing, because a camera and an accelerometer give you noisy readings
that have to be sampled and interpreted properly. It is systems work, because the
platform decides what your code is allowed to do and when it is allowed to run. It is
working within limits, because a 45 second measurement has to survive the screen
turning off and the system trying to suspend the app. And it is about people, because
the measurement is only as good as someone's ability to hold a finger still on a lens,
which is why the camera preview mattered far more than it looked like it would.

App development is the visible part. It is not the difficult part.

## Generative AI Acknowledgment

Portions of the code in this project were generated with assistance from Claude, an AI
tool developed by Anthropic.

Reference: Anthropic. (2026). Claude [Opus 5]. https://claude.ai

Estimated percentage of code influenced by Generative AI: 40%

Each affected file has a citation comment at the top. Where the assistance only
covered part of a file, the comment says which part.

## References

1. A. Banerjee, S. Verma, P. Bagade and S. K. S. Gupta, "Health-Dev: Model Based
   Development of Pervasive Health Monitoring Systems", 2012 Ninth International
   Conference on Wearable and Implantable Body Sensor Networks, pp. 85-90.
2. J. Milazzo, P. Bagade, A. Banerjee and S. K. S. Gupta, "bHealthy: A Physiological
   Feedback-based Mobile Wellness Application Suite", Wireless Health '13, Baltimore,
   MD, USA.
3. Android Developers, "Motion Sensors", Google LLC.
   https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion
4. Android Developers, "SensorManager", Google LLC.
   https://developer.android.com/reference/android/hardware/SensorManager
5. Android Developers, "CameraX Overview", Google LLC.
   https://developer.android.com/media/camera/camerax
