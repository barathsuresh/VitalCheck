# VitalCheck

A context monitoring Android app for CSE 535 Project 1.

**Name:** Barath Suresh
**ASU ID:** 1237353860

## Links

- GitHub repository (private): [VitalCheck](https://github.com/barathsuresh/VitalCheck)
- Demo video (unlisted YouTube): <!-- TODO -->

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

Health-Dev takes a high level AADL description of a system and generates the sensor
and phone code from it. So the job is to describe what the app senses and computes,
not to write the code. Its specification module takes three kinds of information.

**Sensor specification.** First the sensor properties: type, platform, sampling
frequency and sensitivity for each signal. VitalCheck has two signals. One is a
photoplethysmogram (PPG) from the rear camera with the flash on, sampled at the video
frame rate of about 30 fps for 45 seconds. The other is a three axis accelerometer
reading taken with the phone on the chest for 45 seconds and reported every 200 ms.

Then the algorithms, declared as subcomponents with an input port and an output port.
Health-Dev checks the algorithm name against its BSNBench codebase and reuses the
implementation if it finds a match. For heart rate that works, because the paper
lists a PPG pulse width calculator as one of the algorithms already in the codebase.
For respiratory rate there is no match, so the paper says I would have to write the
algorithm myself as a function in the platform's language and add it to the codebase.
In my case that is the magnitude of the three axes followed by a threshold crossing
count.

Then the connections, which set the execution order by wiring one algorithm's output
to the next one's input:

```
camera frames -> mean brightness -> detrend -> peak detect -> heart rate
accelerometer -> sqrt(x^2+y^2+z^2) -> threshold crossings -> respiratory rate
```

**Network specification.** Health-Dev expects a body sensor network, so this section
covers node ids, topology, routing tables, Bluetooth or ZigBee, and radio duty
cycling. VitalCheck has none of that, because both sensors are inside the same phone
that would normally be the base station. I would declare one node, no routing table,
and the radio off.

**Smartphone specification.** Most of VitalCheck sits here:

- Buttons and what they do. "Record Health Data", "Measure Heart Rate" and "Measure
  Respiratory Rate" map onto Health-Dev's start and stop sensing commands, plus
  "UPLOAD SYMPTOMS" and "Delete All Recorded Data".
- Text views for the bpm value, the breaths per minute value, and the record count.
- Optionally a graph of the brightness signal, to show the pulse being picked up.
- The algorithms that run on the phone and their order, since in this app all the
  processing happens on the phone rather than on a separate sensor node.
- Sampling frequency change commands, which is how I would express the 200 ms
  respiratory spacing.

**Where it would not be enough.** Four things Health-Dev could not give me:

- Its platform codebase covers sensor motes like TelosB, Shimmer, BSN v3, iMote2 and
  Arduino. There is no entry for the phone's own camera as a sensing device, so there
  is nothing for it to generate the PPG front end from.
- The paper says the phone UI generator only supports algorithms with one input and
  does not support complex execution sequences. The flow in this app (record, analyse,
  move to another screen, collect symptoms, save) is more than that.
- It generates sensing, processing and communication code but not storage. The Room
  database, the DAO and the schema would still be written by hand.
- The paper's own conclusion says Health-Dev cannot implement adaptive thresholding.
  That is exactly the problem I hit, because the respiratory calculation's fixed 0.15
  threshold only works at one sampling interval and an adaptive threshold is what
  would fix it properly.

So Health-Dev would handle the acquisition and signal processing, which is the part
that is hardest without programming experience, but the database, the screen flow and
the camera front end would still be manual work.

### Q2. How would you use bHealthy to give feedback and model the user?

Right now VitalCheck only measures and stores. It never tells the user anything.
bHealthy's architecture shows how to close that loop, and its five components map
onto this app fairly directly.

**Assessment.** bHealthy scores wellbeing from 1 to 10 by combining physiological
signals with how the user is using the apps. VitalCheck already collects what is
needed: heart rate, respiratory rate, and ten symptom ratings. An assessment step
would combine them into one score per session. A high respiratory rate together with
a high "shortness of breath" rating means something quite different from the same
rate with no symptoms reported, and the self report is what tells them apart.

**Activity suggestion.** bHealthy suggests training apps that have been shown to help
with a particular state. Here that would mean suggesting a breathing exercise when
the respiratory rate is up and the user reported breathlessness, or rest and a short
walk when the heart rate is up and they reported tiredness.

**The new app: a guided breathing trainer.** This is the part I would build, based on
how BrainHealth works. The user lies down with the phone on their chest, the same
position as the existing respiratory measurement, and the app shows a pacing cue
while the accelerometer measures what they are actually doing. BrainHealth uses
particles that pull together when the user is doing well and scatter when they are
not, and the same idea works here driven by how closely the measured breathing
follows the target. It reuses the sensing code that already exists and turns a single
measurement into a loop: measure, give feedback, measure again.

**Wellness report.** bHealthy's report generator queries the apps for activity and
health data over a period and produces a score. The Room database already stores
timestamped rows, so trends over weeks are just a query.

**Building a model of the user.** bHealthy points out that its sensor interface
creates a shared physiological database that the training apps read from, and that
this shared database is what lets separate apps work together. The `records` table is
already that database. Once there are enough rows it stops being a log and becomes a
description of this specific user: their resting heart rate, their usual breathing
rate, which symptoms tend to show up together.

That helps context sensing in two ways.

First, thresholds can be personal instead of fixed. The respiratory calculation
hardcodes 0.15 as the change that counts as a breath, and I had to work out by
testing that this only means anything at one sampling interval. With a baseline for
each user, the threshold could come from that user's own recorded breathing
amplitude. That is the adaptive thresholding Health-Dev says it cannot generate, and
it would remove the most fragile constant in my code.

Second, the symptom ratings are labels that the user supplies themselves. Every row
pairs sensor readings with how the person actually felt at that time, which makes the
stored data a training set for a personalised model. The app could learn that a heart
rate of 90 with a fever rating of 3 is normal for this particular user, rather than
comparing against a general population average.

### Q3. Is mobile computing mostly app development?

My view did change, mainly because of where the time actually went.

I expected the hard part to be the Android framework, the screens and navigation and
the database. That turned out to be the predictable part. Room, Compose and
Navigation behaved the way the documentation said they would, and once I had the
patterns the three screens came together quickly. If mobile computing were mostly app
development I would have been close to done at that point.

Almost everything that cost me real time was below the app layer.

The respiratory rate is the clearest example. The calculation compares consecutive
accelerometer samples against a fixed threshold, so whether it works depends entirely
on how far apart those samples are in time. I assumed asking for `SENSOR_DELAY_NORMAL`
would give me the 200 ms the documentation implies. It did not. The delay constants
are hints, and my phone delivered samples roughly every 66 ms regardless. The fix was
not an API call. It was realising that the sampling interval was a parameter of the
signal processing and re-spacing the samples before doing any calculation.

Heart rate was the same problem but worse. The provided helper is built around
constants that only make sense at about 106 fps. At the 30 fps my phone records, it
sampled the pulse at roughly 2 Hz, which is barely above the minimum for a resting
heart rate, and then applied a moving average wide enough to erase the beat. What
gave it away was not an error but the shape of the output. Its final division means
every answer it can produce is a multiple of 15 bpm, and it returned exactly 45.
Fixing it meant reading the video's real frame rate at runtime and working out the
sampling step from that, which is a sampling problem rather than a coding one.

Even the tooling turned into a systems problem. I could not use Android Studio's
database inspector because my device's SELinux policy has no context entry for
debuggable apps, so no debugger could attach at all. What I did instead was make the
app show its own stored data on screen, which is an architectural answer to a
platform limitation.

So I do not think mobile computing is mostly app development any more. The app is how
the work gets delivered. The actual subject is what sits underneath. It involves
signal processing, because a camera and an accelerometer produce noisy signals that
have to be sampled and interpreted correctly. It involves systems work, because the
platform enforces threading rules, lifecycle rules and security policies that decide
what the code is allowed to do. It involves resource constraints, because a 45 second
capture has to survive the screen timing out and the OS trying to suspend the
process. And it involves how people actually use the device, because the measurement
is only as good as the user's ability to hold a finger still over a lens, which is
why the camera preview turned out to matter more than it looked like it would.

App development is the visible part of it. It is not the difficult part.

## Generative AI Acknowledgment

Portions of the code in this project were generated with assistance from Claude, an AI
tool developed by Anthropic.

Reference: Anthropic. (2026). Claude [Large language model]. https://claude.ai

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
