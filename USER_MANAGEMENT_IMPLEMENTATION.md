# User Management Screen Implementation

## Overview
Implemented an admin-only User Management screen that allows administrators to view all users and manage their roles. The screen is only accessible to users with admin privileges stored in Firestore at `users/{uid}/roles` where `admin: true`.

## Features
- **Admin-Only Access**: Screen is only visible and accessible to users with admin role
- **User List**: Displays all users from Firestore with their name, email, and roles
- **Role Management**: Admins can edit user roles (Student, Lecturer, Admin)
- **Real-time Updates**: User list refreshes after role changes
- **Access Control**: Non-admin users see an "Access Denied" message if they try to access the screen
- **Visual Feedback**: Current user is highlighted in the list, loading states shown during operations

## Files Created

### 1. AdminViewModel.kt
**Location**: `app/src/main/java/com/example/sitconnect/AdminViewModel.kt`

A new ViewModel for admin operations:
- `fetchAllUsers()`: Fetches all users from Firestore `users` collection
- `updateUserRoles(uid, roles)`: Updates a user's roles in Firestore
- `UsersListState`: State management for user list (Idle, Loading, Success, Error)
- `UserUpdateState`: State management for role update operations
- `UserListItem`: Data class representing a user in the list

### 2. UserManagementScreen.kt
**Location**: `app/src/main/java/com/example/sitconnect/UserManagementScreen.kt`

The admin user management UI with the following composables:
- `UserManagementScreen`: Main screen that checks admin status and displays user list
- `UserManagementCard`: Card component for each user showing name, email, roles, and edit button
- `EditRolesDialog`: Dialog for editing a user's roles with checkboxes
- `RoleBadge`: Visual badge component for displaying roles

Key features:
- Access control: Checks if current user is admin before showing content
- User list with role badges (Student, Lecturer, Admin)
- Edit roles dialog with checkboxes for each role
- Visual indication of current logged-in user
- Loading and error states handling

## Files Modified

### 3. UserViewModel.kt
**Updated**: Added roles support to UserData model

Changes:
- Added `UserRoles` data class with `student`, `lecturer`, and `admin` boolean fields
- Updated `UserData` to include `roles: UserRoles?` field
- Modified `fetchUserData()` to parse and include roles from Firestore

### 4. Navigation.kt
**Updated**: Added User Management screen route and navigation

Changes:
- Added `Screen.UserManagement` route to sealed class
- Added `userViewModel` parameter to `SITConnectNavigation`
- Added `LaunchedEffect` to fetch user data and check admin status
- Added User Management navigation item in drawer (only visible if `isAdmin == true`)
- Added User Management composable route in both drawer and non-drawer NavHost
- Updated TopAppBar title to include "User Management"
- Added `Settings` icon import for User Management menu item

## Data Structure

### Firestore Structure
```
users/
  {uid}/
    name: "User's Name"
    email: "user@example.com"
    roles: {
      student: true,
      lecturer: false,
      admin: false
    }
```

### UserRoles Data Class
```kotlin
data class UserRoles(
    val student: Boolean = false,
    val lecturer: Boolean = false,
    val admin: Boolean = false
)
```

## How It Works

### 1. Admin Check
When the app starts or navigates:
1. `AuthViewModel` provides the current authenticated user
2. `UserViewModel.fetchUserData()` fetches user data from Firestore including roles
3. Navigation component checks if `userData.roles?.admin == true`
4. If admin, "User Management" appears in the hamburger menu

### 2. Accessing User Management
1. Admin opens hamburger menu
2. "User Management" option is visible (hidden for non-admins)
3. Tapping navigates to User Management screen

### 3. User Management Screen Flow
1. Screen checks if current user is admin
2. If not admin: Shows "Access Denied" message
3. If admin: Fetches all users from Firestore
4. Displays users in scrollable list with cards
5. Each card shows: name, email, role badges, and "Edit Roles" button

### 4. Editing Roles
1. Admin taps "Edit Roles" on a user card
2. Dialog opens with checkboxes for Student, Lecturer, Admin roles
3. Admin selects/deselects roles and taps "Save"
4. `AdminViewModel.updateUserRoles()` updates Firestore
5. User list automatically refreshes to show updated roles

## Navigation Flow

```
Login → Home (with drawer)
         ├── Home
         ├── User Management (admin only)
         ├── Profile
         └── Logout
```

## Security Notes

⚠️ **Important**: This implementation provides UI-level access control only. For production:

1. **Firestore Security Rules**: Add server-side rules to prevent non-admins from:
   - Reading all users from the `users` collection
   - Writing to other users' `roles` fields

Example Firestore Security Rules:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      // Users can read their own document
      allow read: if request.auth != null && request.auth.uid == userId;
      
      // Admins can read all user documents
      allow read: if request.auth != null && 
                     get(/databases/$(database)/documents/users/$(request.auth.uid)).data.roles.admin == true;
      
      // Only admins can update roles
      allow update: if request.auth != null && 
                       get(/databases/$(database)/documents/users/$(request.auth.uid)).data.roles.admin == true &&
                       request.resource.data.diff(resource.data).affectedKeys().hasOnly(['roles']);
    }
  }
}
```

## Testing

### Prerequisites
1. Create a test user in Firebase Authentication
2. Manually set their admin role in Firestore:
   ```
   users/{test-user-uid}/roles/admin = true
   ```

### Test Cases
1. **Non-admin user**: 
   - Login → User Management not visible in menu
   - Direct navigation → "Access Denied" shown

2. **Admin user**:
   - Login → User Management visible in menu
   - Tap User Management → See list of all users
   - Tap "Edit Roles" → Dialog opens with checkboxes
   - Change roles → Save → List updates

3. **Role badges**:
   - Verify correct role badges display for each user
   - Verify "You" indicator shows for current user

## UI Components

### Colors and Styling
- **Admin badge**: Red color (`MaterialTheme.colorScheme.error`)
- **Lecturer badge**: Tertiary color (`MaterialTheme.colorScheme.tertiary`)
- **Student badge**: Primary color (`MaterialTheme.colorScheme.primary`)
- **Current user card**: Primary container background
- **Regular user card**: Surface variant background
- **Logout button**: Dark red (#8B0000) with white text

### Icons
- **User Management**: Settings icon (gear)
- **Access Denied**: Person icon (red)

## Future Enhancements

Potential improvements:
1. Search/filter users by name or email
2. Sort users by name, role, or date created
3. Batch role updates for multiple users
4. User deletion functionality (with Firebase Admin SDK)
5. Activity log showing who changed what roles
6. Export user list to CSV
7. Pagination for large user lists
8. Email notifications when roles change

## Dependencies

No new dependencies were added. The implementation uses existing libraries:
- Firebase Firestore (already included)
- Jetpack Compose Material3 (already included)
- Kotlin Coroutines (already included)
- AndroidX Navigation Compose (already included)

## Build Status

✅ Build successful
✅ No compilation errors
✅ All files integrated properly

