# SIT Connect - Student Features Implementation

This document describes the student features implemented in the SIT Connect mobile application.

## Features Overview

### 1. 📅 Trimester Calendar
**Location:** `features/calendar/`

View academic calendar events including:
- Exam dates
- Recess weeks
- Public holidays
- Trimester start/end dates

**Features:**
- **Auto-detects current trimester** based on device date (Jan-Apr: T1, May-Aug: T2, Sep-Dec: T3)
- Filter by trimester (1, 2, or 3)
- Filter by event type (Exam, Recess, Holiday, etc.)
- Color-coded event cards
- Date range display for multi-day events

### 2. 🏥 MC Submission
**Location:** `features/mcsubmission/`

Submit Medical Certificates for leave of absence.

**Features:**
- Form to submit new MC with:
  - Start and end dates (using device date picker)
  - Reason for leave
  - MC file name (simulated upload)
- View submission history
- Status tracking (Pending, Approved, Rejected)
- Remarks from reviewers
- **Uses current device date** as default

### 3. 📝 Assignment Portal
**Location:** `features/assignments/`

Upload assignments for module submissions.

**Features:**
- View all assignments with **dynamic due dates relative to current date**:
  - Some past due (overdue)
  - Some due soon (2-3 days)
  - Some upcoming (1-2 weeks)
- Status indicators (Due soon, Overdue, Submitted, Graded)
- Submit/resubmit assignments (PDF/ZIP)
- View grades and feedback
- Module and credit info display

### 4. ✅ Attendance Taking
**Location:** `features/attendance/`

Mark attendance using **real GPS location** + QR Code verification.

**Features:**
- **Uses actual device GPS** via FusedLocationProvider
- Location permission handling with user prompts
- View today's sessions (Labs, Tutorials, Lectures) with **dynamic time slots based on current time**
- Mark attendance with:
  - QR Code + GPS verification
  - GPS-only option (backup)
- Location distance validation (within venue radius)
- Real-time location display with refresh button
- Attendance summary by module
- Percentage tracking with visual indicators

**Permissions Required:**
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

### 5. 📊 Gradebook
**Location:** `features/gradebook/`

View GPA and current module grades.

**Features:**
- GPA Summary card showing:
  - Current trimester GPA
  - Cumulative GPA
  - Credits earned/attempted
- Module grade cards with:
  - Current grade letter
  - Expandable assessment breakdown
  - Individual assessment scores
  - Weighted percentage calculation

### 6. 🏢 Facility Booking
**Location:** `features/facilitybooking/`

Book Discussion Rooms and Sports facilities.

**Features:**
- Browse facilities by type:
  - Discussion Rooms
  - Sports Hall (Badminton, Basketball, Table Tennis)
  - Computer Labs
  - Study Rooms
- **Dynamic time slots** - past hours are unavailable based on current time
- View availability (some slots randomly booked for realism)
- Book with date, time slot, and purpose
- View and cancel your bookings
- Capacity and amenities info

### 7. 💬 Messaging
**Location:** `features/messaging/`

Join group discussions in chatrooms.

**Features:**
- Chat room types:
  - Module groups
  - Study groups
  - Club chats
  - General discussion
- **Real-time timestamps** using device time
- Message bubbles with sender names
- Member count display
- Last message preview with relative time

## Navigation

All features are accessible from:
1. **Home Screen** - Quick access grid with feature cards
2. **Navigation Drawer** - Side menu with all features listed

## Data Storage

All features use Firebase Firestore for data persistence:
- `calendar_events` - Academic calendar events
- `mc_submissions` - MC submission records
- `assignments` - Assignment definitions
- `assignment_submissions` - Student submissions
- `attendance_sessions` - Attendance sessions
- `attendance_records` - Student attendance records
- `student_grades` - Grade records
- `facilities` - Available facilities
- `facility_bookings` - Booking records
- `chat_rooms` - Chat room definitions
- `chat_rooms/{id}/messages` - Chat messages

## Sample Data

Each feature includes sample/demo data that displays when no Firestore data exists:
- **All dates are dynamic** - based on current device date/time
- Sample data provides realistic scenarios for testing

## Dependencies Added

```toml
# Location Services
play-services-location = "21.1.0"
accompanist-permissions = "0.32.0"
```

## Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
```

## File Structure

```
features/
├── assignments/
│   ├── domain/model/Assignment.kt
│   └── presentation/
│       ├── AssignmentsScreen.kt
│       └── AssignmentsViewModel.kt
├── attendance/
│   ├── domain/model/Attendance.kt
│   └── presentation/
│       ├── AttendanceScreen.kt
│       └── AttendanceViewModel.kt
├── calendar/
│   ├── domain/model/CalendarEvent.kt
│   └── presentation/
│       ├── CalendarScreen.kt
│       └── CalendarViewModel.kt
├── facilitybooking/
│   ├── domain/model/Facility.kt
│   └── presentation/
│       ├── FacilityBookingScreen.kt
│       └── FacilityBookingViewModel.kt
├── gradebook/
│   ├── domain/model/Grade.kt
│   └── presentation/
│       ├── GradebookScreen.kt
│       └── GradebookViewModel.kt
├── mcsubmission/
│   ├── domain/model/MCSubmission.kt
│   └── presentation/
│       ├── MCSubmissionScreen.kt
│       └── MCSubmissionViewModel.kt
└── messaging/
    ├── domain/model/ChatRoom.kt
    └── presentation/
        ├── MessagingScreen.kt
        └── MessagingViewModel.kt
```

## Building and Running

The app can be built using:
```bash
./gradlew assembleDebug
```

Or run directly from Android Studio.

