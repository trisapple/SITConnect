# Quick Start Guide - User Management Feature

## Testing the Admin User Management Screen

### Step 1: Create an Admin User

You need to manually set admin privileges for a test user in Firebase Console:

1. **Login to Firebase Console**: https://console.firebase.google.com
2. **Select your project**: SITConnect
3. **Go to Firestore Database**
4. **Navigate to**: `users` collection
5. **Find or create a user document** with the following structure:

```
users/
  {uid}/  (replace with actual user UID from Firebase Authentication)
    name: "Admin User"
    email: "admin@example.com"
    roles: {
      student: false,
      lecturer: false,
      admin: true
    }
```

### Step 2: Find User UID

1. Go to **Firebase Authentication** in console
2. Find your test user
3. Copy the **User UID** 
4. Use this UID in the Firestore document path above

### Step 3: Test the Feature

#### Test as Non-Admin User:
1. Build and run the app
2. Login with a regular user account
3. Open hamburger menu (☰)
4. **Expected**: "User Management" option is NOT visible
5. The menu should only show: Home, Profile, Logout

#### Test as Admin User:
1. Login with the admin user account you created
2. Open hamburger menu (☰)
3. **Expected**: "User Management" option IS visible
4. Tap "User Management"
5. **Expected**: See list of all users from Firestore
6. Each user card shows:
   - Name
   - Email
   - Role badges (Student, Lecturer, Admin)
   - "Edit Roles" button
   - Your own card is highlighted with "(You)" label

#### Test Role Editing:
1. Tap "Edit Roles" on any user card
2. Dialog opens with checkboxes for:
   - ☐ Student
   - ☐ Lecturer
   - ☐ Admin
3. Check/uncheck roles
4. Tap "Save"
5. **Expected**: 
   - Dialog closes
   - User list refreshes
   - Role badges update immediately

### Step 4: Verify Data in Firestore

After editing roles, check Firestore Console:
1. Go to Firestore Database
2. Navigate to `users/{uid}/roles`
3. Verify the roles were updated correctly

## Quick Firebase Setup Commands

If you need to set up a test admin user via Firebase CLI:

```bash
# Install Firebase CLI (if not installed)
npm install -g firebase-tools

# Login
firebase login

# Deploy Firestore rules (optional - add security rules)
firebase deploy --only firestore:rules
```

## Example Test User Data

Create these users in Firestore for comprehensive testing:

### Admin User
```
users/admin-uid-123/
  name: "Admin User"
  email: "admin@test.com"
  roles: { student: false, lecturer: false, admin: true }
```

### Lecturer User
```
users/lecturer-uid-456/
  name: "John Lecturer"
  email: "lecturer@test.com"
  roles: { student: false, lecturer: true, admin: false }
```

### Student User
```
users/student-uid-789/
  name: "Jane Student"
  email: "student@test.com"
  roles: { student: true, lecturer: false, admin: false }
```

### Multi-Role User
```
users/multi-uid-000/
  name: "Multi Role User"
  email: "multi@test.com"
  roles: { student: true, lecturer: true, admin: false }
```

## Expected UI Behavior

### Hamburger Menu (Non-Admin):
```
┌─────────────────────┐
│ SIT Connect         │
├─────────────────────┤
│ 🏠 Home             │
│ 👤 Profile          │
│ [Logout]            │
└─────────────────────┘
```

### Hamburger Menu (Admin):
```
┌─────────────────────┐
│ SIT Connect         │
├─────────────────────┤
│ 🏠 Home             │
│ ⚙️ User Management  │
│ 👤 Profile          │
│ [Logout]            │
└─────────────────────┘
```

### User Management Screen:
```
┌───────────────────────────────┐
│ User Management               │
│ Manage user roles and perms   │
├───────────────────────────────┤
│ ┌───────────────────────────┐ │
│ │ Admin User                │ │
│ │ admin@test.com       (You)│ │
│ │ [Admin]           [Edit]  │ │
│ └───────────────────────────┘ │
│                               │
│ ┌───────────────────────────┐ │
│ │ John Lecturer             │ │
│ │ lecturer@test.com         │ │
│ │ [Lecturer]        [Edit]  │ │
│ └───────────────────────────┘ │
│                               │
│ ┌───────────────────────────┐ │
│ │ Jane Student              │ │
│ │ student@test.com          │ │
│ │ [Student]         [Edit]  │ │
│ └───────────────────────────┘ │
└───────────────────────────────┘
```

## Common Issues & Solutions

### Issue: User Management not showing for admin
**Solution**: 
- Verify admin role is set to `true` in Firestore
- Check the exact path: `users/{uid}/roles/admin`
- Make sure you're logged in with the correct account
- Try logging out and back in

### Issue: Can't see any users in the list
**Solution**:
- Check Firestore permissions
- Verify users exist in Firestore `users` collection
- Check Android Logcat for errors

### Issue: Role updates not saving
**Solution**:
- Check Firestore write permissions
- Verify internet connection
- Check Android Logcat for Firestore errors

### Issue: App crashes when opening User Management
**Solution**:
- Check all files are properly saved
- Rebuild the app: `./gradlew clean build`
- Check for compilation errors

## Security Note

⚠️ **Important**: This implementation provides UI-level access control only. 

For production, you MUST add Firestore Security Rules to prevent:
- Non-admins from reading all users
- Non-admins from modifying roles
- Users from modifying their own admin status

See `USER_MANAGEMENT_IMPLEMENTATION.md` for example security rules.

## Build and Run

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install and run on connected device
./gradlew installDebug
```

## Files to Review

If you want to understand or modify the implementation:

1. **UserManagementScreen.kt** - Main UI component
2. **AdminViewModel.kt** - Admin operations logic
3. **Navigation.kt** - Navigation and menu setup
4. **UserViewModel.kt** - User data fetching with roles

## Success Criteria

✅ Non-admin users don't see User Management in menu
✅ Admin users see User Management in menu
✅ Admin can view all users
✅ Admin can edit user roles
✅ Role badges display correctly
✅ Current user is highlighted
✅ Changes persist to Firestore
✅ No crashes or errors

Enjoy managing your users! 🎉

