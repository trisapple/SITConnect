# 📋 User Creation Feature - Deployment Checklist

## Pre-Deployment Checklist

Before deploying the user creation feature, ensure:

### Firebase Setup
- [ ] Firebase project created
- [ ] Firebase Authentication enabled (Email/Password)
- [ ] Firestore Database created
- [ ] Firebase CLI installed: `npm install -g firebase-tools`
- [ ] Logged in to Firebase: `firebase login`
- [ ] Correct project selected: `firebase use <project-id>`

### Admin User Setup
- [ ] At least one admin user exists in Firestore
- [ ] Admin user has `roles.admin: true` in Firestore document
- [ ] Path verified: `users/{uid}/roles/admin = true`

### App Build
- [ ] App builds successfully: `./gradlew assembleDebug`
- [ ] No compilation errors
- [ ] Dependencies synced

---

## Deployment Checklist

### Step 1: Deploy Cloud Function ⚠️ CRITICAL

**Option A - Using Script** (Recommended):
- [ ] Navigate to project: `cd /Users/tristan/AndroidStudioProjects/SITConnect`
- [ ] Run script: `./deploy-user-creation.sh`
- [ ] Wait for "✅ Deployment Successful!" message

**Option B - Manual**:
- [ ] Navigate to project: `cd /Users/tristan/AndroidStudioProjects/SITConnect`
- [ ] Install dependencies: `cd functions && npm install && cd ..`
- [ ] Deploy function: `firebase deploy --only functions:createUser`
- [ ] Verify success message in console

### Step 2: Verify Deployment

- [ ] Check Firebase Console → Functions
- [ ] `createUser` function is listed
- [ ] Status is "Active" (green checkmark)
- [ ] No error messages in function logs

### Step 3: Build and Install App

- [ ] Build app: `./gradlew assembleDebug`
- [ ] Build successful (no errors)
- [ ] Install on device/emulator
- [ ] App launches without crashes

---

## Testing Checklist

### Test 1: Admin Access
- [ ] Login as admin user
- [ ] Hamburger menu opens
- [ ] "User Management" option is visible
- [ ] Tap "User Management"
- [ ] User Management screen loads
- [ ] Can see existing users in list
- [ ] Blue "+" FAB visible at bottom-right

### Test 2: Create User Dialog
- [ ] Tap "+" FAB
- [ ] "Create New User" dialog opens
- [ ] All fields are present:
  - [ ] Name field
  - [ ] Email field
  - [ ] Password field (masked)
  - [ ] Student checkbox
  - [ ] Lecturer checkbox
  - [ ] Admin checkbox
- [ ] "Create User" and "Cancel" buttons visible

### Test 3: Form Validation
- [ ] Leave name blank → Shows error
- [ ] Leave email blank → Shows error
- [ ] Enter invalid email (no @) → Shows error
- [ ] Enter short password (< 6 chars) → Shows error message
- [ ] Valid input → No errors shown

### Test 4: Create Student User
- [ ] Fill in form:
  - Name: "Test Student"
  - Email: "teststudent@example.com"
  - Password: "student123"
  - Roles: ✓ Student
- [ ] Tap "Create User"
- [ ] Loading indicator appears
- [ ] Success message: "User created successfully!"
- [ ] Dialog closes
- [ ] New user appears in list
- [ ] User has "Student" badge

### Test 5: Verify in Firebase Console
- [ ] Open Firebase Console → Authentication
- [ ] New user "teststudent@example.com" is listed
- [ ] UID is shown
- [ ] Open Firebase Console → Firestore
- [ ] Navigate to `users` collection
- [ ] Find document with new user's UID
- [ ] Document contains:
  - [ ] email: "teststudent@example.com"
  - [ ] name: "Test Student"
  - [ ] roles.student: true
  - [ ] roles.lecturer: false
  - [ ] roles.admin: false
  - [ ] createdAt: (timestamp)

### Test 6: Login with New User
- [ ] Logout from admin account
- [ ] Login screen appears
- [ ] Enter new user credentials:
  - Email: "teststudent@example.com"
  - Password: "student123"
- [ ] Tap "Login"
- [ ] Login successful
- [ ] Home screen loads
- [ ] User email displayed correctly

### Test 7: Non-Admin Access
- [ ] Logged in as non-admin user (from Test 6)
- [ ] Open hamburger menu
- [ ] "User Management" option is NOT visible
- [ ] Only see: Home, Profile, Logout

### Test 8: Create Lecturer User
- [ ] Logout and login as admin
- [ ] Navigate to User Management
- [ ] Tap "+" FAB
- [ ] Create user:
  - Name: "Dr. Jane Lecturer"
  - Email: "jane@university.edu"
  - Password: "lecturer123"
  - Roles: ✓ Lecturer
- [ ] Success message appears
- [ ] User appears with "Lecturer" badge

### Test 9: Create Admin User
- [ ] Create user:
  - Name: "Super Admin"
  - Email: "superadmin@example.com"
  - Password: "admin123"
  - Roles: ✓ Admin
- [ ] Success message appears
- [ ] User appears with "Admin" badge
- [ ] Logout and login as new admin
- [ ] "User Management" menu is visible

### Test 10: Create Multi-Role User
- [ ] Create user with multiple roles:
  - Name: "Multi Role User"
  - Email: "multi@example.com"
  - Password: "multi123"
  - Roles: ✓ Student, ✓ Lecturer
- [ ] Success message appears
- [ ] User appears with both badges

### Test 11: Error Handling
- [ ] Try to create user with existing email
- [ ] Error message appears: "Email already exists" (or similar)
- [ ] Dialog remains open
- [ ] Can try again with different email

---

## Performance Checklist

### App Performance
- [ ] App launches quickly
- [ ] User Management screen loads smoothly
- [ ] No lag when opening create dialog
- [ ] Form input is responsive
- [ ] User list scrolls smoothly
- [ ] No crashes or freezes

### Cloud Function Performance
- [ ] User creation completes within 5 seconds
- [ ] No timeout errors
- [ ] Success message appears promptly
- [ ] User list updates immediately

---

## Security Checklist

### Client-Side Security
- [ ] Only admins can see User Management menu
- [ ] Non-admins see "Access Denied" if they try to access
- [ ] Password field is masked in dialog
- [ ] Form validates all inputs

### Server-Side Security
- [ ] Cloud Function verifies authentication
- [ ] Cloud Function verifies admin role
- [ ] Non-admins cannot call the function
- [ ] Invalid data is rejected by function
- [ ] Passwords are handled securely by Firebase Auth

### Firestore Security (Recommended)
- [ ] Firestore security rules prevent direct user creation
- [ ] Rules require admin role for reading all users
- [ ] Rules prevent users from modifying their own roles
- [ ] (See USER_CREATION_FEATURE.md for example rules)

---

## Documentation Checklist

### Files Created
- [ ] `USER_CREATION_FEATURE.md` exists
- [ ] `QUICK_START_USER_CREATION.md` exists
- [ ] `USER_CREATION_README.md` exists
- [ ] `deploy-user-creation.sh` exists and is executable
- [ ] All documentation is accurate and complete

### Code Documentation
- [ ] AdminViewModel has clear comments
- [ ] UserManagementScreen composables are well-named
- [ ] Cloud Function has explanatory comments
- [ ] Error messages are user-friendly

---

## Final Verification

### Build Status
- [ ] Clean build succeeds: `./gradlew clean build`
- [ ] No compilation errors
- [ ] No critical warnings
- [ ] APK can be generated

### Functionality
- [ ] All 11 test cases pass
- [ ] No crashes or errors
- [ ] All features work as expected
- [ ] UI is responsive and polished

### Production Readiness
- [ ] Cloud Function is deployed
- [ ] Function is active in Firebase Console
- [ ] Admin user is configured
- [ ] App is tested on real device/emulator
- [ ] Error handling works correctly
- [ ] Security measures are in place

---

## Post-Deployment

### Monitor
- [ ] Check Firebase Console → Functions → Logs
- [ ] Monitor for any errors
- [ ] Check function execution count
- [ ] Monitor Firestore usage

### User Feedback
- [ ] Verify admin users can create accounts
- [ ] Confirm created users can login
- [ ] Check that roles are assigned correctly
- [ ] Ensure UI is intuitive

### Optional Improvements
- [ ] Consider adding user deletion
- [ ] Consider adding password reset
- [ ] Consider adding email verification
- [ ] Consider adding bulk import
- [ ] Consider adding audit logs

---

## Rollback Plan (If Needed)

If something goes wrong:

### 1. Disable Function
```bash
firebase functions:delete createUser
```

### 2. Revert App Changes
```bash
git revert <commit-hash>
./gradlew assembleDebug
```

### 3. Check Logs
```bash
firebase functions:log
```

---

## Success Criteria

✅ All boxes checked above  
✅ No errors in Firebase logs  
✅ Users can be created successfully  
✅ Created users can login  
✅ Roles are assigned correctly  
✅ Non-admins cannot access feature  
✅ UI is responsive and polished  

---

## 🎉 Deployment Complete!

Once all items are checked, your user creation feature is:
- ✅ Fully deployed
- ✅ Tested and verified
- ✅ Production ready
- ✅ Secure and reliable

**Congratulations!** 🎊

---

**Need help?** Check:
- `USER_CREATION_FEATURE.md` - Technical documentation
- `QUICK_START_USER_CREATION.md` - Quick guide
- Firebase Console → Functions → Logs

**Questions?** Run:
```bash
firebase functions:log
```

