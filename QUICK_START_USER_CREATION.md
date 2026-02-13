# Quick Start: Creating Users in SIT Connect

## 🚀 Deployment Steps (Required First)

Before you can create users in the app, deploy the Cloud Function:

```bash
# 1. Navigate to project directory
cd /Users/tristan/AndroidStudioProjects/SITConnect

# 2. Install dependencies (if first time)
cd functions
npm install
cd ..

# 3. Login to Firebase (if not already logged in)
firebase login

# 4. Deploy the createUser function
firebase deploy --only functions:createUser

# 5. Wait for deployment to complete
# You should see: ✔  functions[createUser(us-central1)]: Successful create operation.
```

**Important**: You must deploy the Cloud Function before the feature will work!

## 📱 Using the Feature

### Step 1: Login as Admin
- Login with an account that has `admin: true` in Firestore roles

### Step 2: Open User Management
- Tap hamburger menu (☰) at top-left
- Tap "User Management"

### Step 3: Create a User
- Tap the **+** button (Floating Action Button at bottom-right)
- Fill in the form:
  - **Name**: Full name of the user
  - **Email**: Valid email address
  - **Password**: At least 6 characters
  - **Roles**: Check at least one role
    - ☐ Student
    - ☐ Lecturer  
    - ☐ Admin
- Tap **"Create User"**

### Step 4: Verify
- Success message appears at bottom
- New user appears in the list immediately
- User can now login with email/password

## ✅ Quick Test

Create a test user:
```
Name: Test Student
Email: teststudent@example.com
Password: test1234
Roles: ✓ Student
```

Then logout and login with:
- Email: `teststudent@example.com`
- Password: `test1234`

## 🎯 Form Validation

### Name Field
- ❌ Empty → Shows error
- ✅ Any text → Valid

### Email Field
- ❌ Empty → Shows error
- ❌ No @ symbol → Shows error
- ✅ Contains @ → Valid

### Password Field
- ❌ Less than 6 characters → Shows error message
- ✅ 6 or more characters → Valid

### Roles
- ❌ None selected → Still allowed (user will have no roles)
- ✅ One or more → User gets those roles

## 🎨 Visual Elements

### Floating Action Button (FAB)
- **Location**: Bottom-right corner of screen
- **Appearance**: Blue circle with white "+" icon
- **Action**: Opens create user dialog

### Create User Dialog
```
┌─────────────────────────────────┐
│ Create New User               ✕ │
├─────────────────────────────────┤
│ Enter user details:             │
│                                 │
│ ┌─────────────────────────────┐│
│ │ Name                        ││
│ └─────────────────────────────┘│
│                                 │
│ ┌─────────────────────────────┐│
│ │ Email                       ││
│ └─────────────────────────────┘│
│                                 │
│ ┌─────────────────────────────┐│
│ │ Password (hidden)           ││
│ └─────────────────────────────┘│
│                                 │
│ Select roles:                   │
│ ☑ Student                       │
│ ☐ Lecturer                      │
│ ☐ Admin                         │
│                                 │
│        [Cancel]  [Create User]  │
└─────────────────────────────────┘
```

## 🔧 Troubleshooting

### Error: "Function not found"
**Solution**: Deploy the Cloud Function
```bash
firebase deploy --only functions:createUser
```

### Error: "Only administrators can create users"
**Solution**: 
1. Check your Firestore database
2. Navigate to: `users/{your-uid}/roles`
3. Set `admin: true`
4. Logout and login again

### Error: "Email already exists"
**Solution**: The email is already registered. Use a different email.

### Error: "Password should be at least 6 characters"
**Solution**: Use a longer password (6+ characters)

### Dialog doesn't open
**Solution**:
1. Check you're on User Management screen
2. Look for "+" button at bottom-right
3. Rebuild app: `./gradlew clean build`

### Success message but user not created
**Solution**:
1. Check Firebase Console → Functions → Logs
2. Look for errors in function execution
3. Verify Firebase Authentication is enabled

## 📊 View Created Users

### In the App:
- All users appear in the User Management screen list
- Each user card shows: Name, Email, Role badges

### In Firebase Console:

**Authentication:**
1. Open Firebase Console
2. Go to Authentication
3. See all created users in Users tab

**Firestore:**
1. Open Firebase Console
2. Go to Firestore Database
3. Navigate to `users` collection
4. See user documents with details

## 🎯 Example Use Cases

### Create a Student
```
Name: Alice Johnson
Email: alice@university.edu
Password: student123
Roles: ✓ Student
```

### Create a Lecturer
```
Name: Dr. Bob Smith
Email: bob.smith@university.edu
Password: lecturer123
Roles: ✓ Lecturer
```

### Create a Multi-Role User
```
Name: Charlie Admin
Email: charlie@university.edu
Password: admin123
Roles: ✓ Student, ✓ Lecturer, ✓ Admin
```

### Create an Admin Only
```
Name: Super Admin
Email: superadmin@university.edu
Password: securePass123
Roles: ✓ Admin
```

## 📋 Checklist

Before creating users, ensure:
- [ ] Cloud Function deployed (`firebase deploy --only functions`)
- [ ] You're logged in as admin
- [ ] On User Management screen
- [ ] Can see the "+" FAB button
- [ ] Firebase Authentication enabled in console
- [ ] Firestore database created

## 🎉 Success Indicators

When everything works:
- ✅ "+" button visible on User Management screen
- ✅ Dialog opens when tapping "+"
- ✅ Form validation shows errors for invalid input
- ✅ "User created successfully!" message appears
- ✅ New user immediately visible in list
- ✅ New user can login with credentials
- ✅ New user has assigned roles

## 🔐 Security Notes

- Only admins can create users (enforced server-side)
- Passwords are encrypted by Firebase Authentication
- Passwords are never stored in Firestore
- All user creation goes through secure Cloud Function
- Non-admins cannot see or access User Management

## 📞 Need Help?

Check the detailed documentation:
- `USER_CREATION_FEATURE.md` - Full implementation details
- `USER_MANAGEMENT_IMPLEMENTATION.md` - User management system
- `QUICK_START_USER_MANAGEMENT.md` - Admin setup guide

View Firebase logs:
```bash
firebase functions:log
```

Or view in Firebase Console:
Functions → createUser → Logs

---

**Ready to create users? Follow the deployment steps above first!** 🚀

