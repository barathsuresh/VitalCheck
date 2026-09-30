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

Health-Dev writes the sensor and phone code for you from a description, so what I would
be producing is a description rather than a program. It asks for three things.

For the sensors, I would describe the two signals I need. One is a light reading from
the rear camera with the flash on. The other is accelerometer data with the phone
resting on the chest. Both run for 45 seconds. Then I would name the algorithms to run
on each one. Health-Dev keeps a library of ready-made algorithms and matches on the name
I give it. Heart rate is covered, because the paper lists a PPG pulse width calculator,
which is the same camera method this app uses. Breathing is not, so I would have to write
that one myself and add it to the library. Finally I would connect the algorithms up so
it knows what feeds into what.

The network section barely applies. It is meant for separate sensor boards talking to a
phone over Bluetooth, with addresses and routing. Both my sensors are inside the phone,
so I would describe a single node with the radio off.

Most of the app sits in the phone section. The buttons for recording, measuring and
deleting, the text showing the two results, and which algorithms run on the phone, since
all the processing happens there rather than on a separate sensor.

There are a few things it could not do for me. Its hardware library covers small sensor
boards, not a phone's own camera, so there is nothing for it to build the heart rate side
from. It generates sensing and processing code but not storage, so the database would
still be mine to write. And the paper admits it cannot do adaptive thresholds, which is
exactly the problem I ran into with the breathing calculation, where one fixed number
only works under one set of conditions.

So it would cover the sensing and the signal processing, which is genuinely the hardest
part if you cannot program. I would still be writing the database and the screens.

### Q2. How would you use bHealthy to give feedback and model the user?

VitalCheck measures, saves, and stops. It never tells the user anything. bHealthy is
built around closing that loop.

The first thing I would add is a score. bHealthy rates wellbeing out of 10 from sensor
data combined with how the person has been using the apps. I already collect heart rate,
breathing rate and ten symptom ratings, so I could turn those into one number per
session. The symptom ratings are what make it work. A high breathing rate on its own
means something very different from a high breathing rate when the user has also said
they feel short of breath.

From that score the app could suggest something. A breathing exercise if the rate is up
and they reported breathlessness, or rest if their heart rate is up and they said they
felt tired.

The app I would actually build is a breathing trainer, based on bHealthy's BrainHealth.
You lie down with the phone on your chest, the same as the existing measurement, and it
shows you a pace to follow while the accelerometer checks what you are really doing.
BrainHealth uses particles on screen that pull together when you are doing well and
scatter when you are not, and the same idea works here. It reuses sensor code I already
have, and it turns a one-off reading into measure, feedback, measure again.

For building a model of the user, bHealthy makes the point that its sensor layer creates
a shared database that the other apps all read from. My `records` table is already that.
With enough rows it stops being a list of readings and becomes a description of one
person, their normal heart rate and their usual breathing rate.

That gives you two things. The fixed threshold in the breathing calculation could come
from how deeply that particular person breathes, rather than being the same number for
everyone. And the symptom ratings are labels the user wrote themselves, so every row
pairs a sensor reading with how they actually felt. That makes the stored data something
you could train on.

### Q3. Is mobile computing mostly app development?

It did change, mostly because of where my time went.

I assumed the hard part would be Android itself, the screens and the navigation and the
database. That turned out to be the predictable part. Room, Compose and Navigation all
behaved the way the documentation said, and once I had the patterns the three screens
came together quickly. If this were mostly app development I would have been close to
finished there.

Nearly everything that cost me real time was underneath the app.

The breathing rate is the clearest case. The calculation compares two accelerometer
readings sitting next to each other, so whether it works at all depends on how far apart
they are in time. I assumed asking Android for a particular sampling speed would give me
that speed. It does not. Those settings are requests, not promises, and my phone gave
readings far more often than I asked for. The fix was not an API call. It was working out
that the spacing between readings was part of the signal processing and correcting it
myself before doing any maths.

Heart rate was the same problem, worse. The provided code assumes the camera records
several times faster than mine does. At my phone's speed it looked at the pulse too
rarely to see it, then smoothed over a window wide enough to erase what was left. There
was no error message. What gave it away was noticing its answers could only ever land on
certain values.

Even the tooling turned into a systems problem. I could not use Android Studio's database
inspector because the security policy on my phone has no entry for debuggable apps, so
nothing could attach. I ended up making the app display its own saved records instead,
which is an architecture decision forced on me by a platform rule.

So no, I do not think it is mostly app development any more. The app is how the work
reaches someone. The subject is what sits under it. Sampling and signal processing,
because the sensors give you noisy readings that have to be interpreted properly.
Systems, because the platform decides what your code is allowed to do and when. Working
inside limits, because a 45 second measurement has to survive the screen turning off. And
people, because the reading is only as good as someone's ability to hold a finger still
on a lens, which is why the camera preview ended up mattering more than I expected.

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
