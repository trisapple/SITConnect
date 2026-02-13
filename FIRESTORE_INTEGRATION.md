# Firestore Integration for User Profile

## Overview
Added Firestore functionality to display the user's name in the ProfileScreen. The name is fetched from Firestore at the path `users/{uid}/name`.

## Changes Made

### 1. Dependencies Added

**File: `gradle/libs.versions.toml`**
- Added `firebase-firestore` library reference

**File: `app/build.gradle.kts`**
- Added `implementation(libs.firebase.firestore)` to dependencies

### 2. New Files Created

**File: `app/src/main/java/com/example/sitconnect/UserViewModel.kt`**
- Created a new ViewModel to manage Firestore operations
- Implements state management for user data fetching
- Defines `UserData` data class with `name` and `email` fields
- Defines `UserDataState` sealed class with states: Idle, Loading, Success, Error
- `fetchUserData(uid: String)` function fetches user data from Firestore path `users/{uid}`

### 3. Updated Files

**File: `app/src/main/java/com/example/sitconnect/ProfileScreen.kt`**
- Added `UserViewModel` parameter to ProfileScreen composable
- Added `LaunchedEffect` to automatically fetch user data when the screen loads
- Updated UI to show loading state while fetching data
- Displays user's name from Firestore in the profile card
- Shows error messages if data fetching fails
- Falls back to showing email and user ID if Firestore data is not available

## How It Works

1. **User Authentication**: When a user logs in, Firebase Authentication provides their `uid`
2. **Data Fetching**: The ProfileScreen uses `LaunchedEffect` to trigger data fetching when mounted
3. **Firestore Query**: UserViewModel queries Firestore at `users/{uid}` to get the user document
4. **Name Display**: The user's name is extracted from the document and displayed in the UI
5. **State Management**: The UI reactively updates based on the UserDataState (Loading, Success, Error, Idle)

## Data Structure in Firestore

The expected Firestore document structure is:
```
users/
  {uid}/
    name: "User's Name"
    email: "user@example.com"
```

## Features

- **Automatic Fetching**: User data is fetched automatically when the Profile screen loads
- **Loading Indicator**: Shows a circular progress indicator while fetching data
- **Error Handling**: Displays error messages if data fetching fails
- **Graceful Fallback**: Shows available data (email, uid) even if Firestore fetch fails
- **Reactive UI**: UI updates automatically based on data state changes

## Testing

To test this feature:
1. Ensure Firebase Firestore is enabled in your Firebase Console
2. Create a user document at `users/{uid}` with a `name` field
3. Log in to the app
4. Navigate to the Profile screen from the hamburger menu
5. The user's name should be displayed in the profile card

## Notes

- The Firebase Functions (`functions/index.js`) already creates user documents with a default name "Test" when new users sign up
- You can modify the Firebase Function to set custom names during user registration
- The ProfileScreen handles all possible states (Loading, Success, Error, Idle) gracefully
- The IDE may show temporary red errors in UserViewModel.kt due to caching, but the build succeeds and the code works correctly

