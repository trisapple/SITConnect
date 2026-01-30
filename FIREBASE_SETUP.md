# Firebase Setup Instructions

## Steps to Connect Firebase to Your App

1. **Create a Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Click "Add project" or select an existing project
   - Follow the setup wizard

2. **Register Your Android App**
   - In the Firebase Console, click on "Add app" and select Android
   - Enter the package name: `com.example.sitconnect`
   - (Optional) Add app nickname and SHA-1 certificate
   - Click "Register app"

3. **Download google-services.json**
   - After registering your app, download the `google-services.json` file
   - Replace the placeholder file at `app/google-services.json` with your downloaded file

4. **Enable Authentication**
   - In Firebase Console, go to "Build" → "Authentication"
   - Click "Get started"
   - Go to "Sign-in method" tab
   - Enable "Email/Password" authentication
   - Save changes

5. **Sync and Build**
   - In Android Studio, click "Sync Project with Gradle Files"
   - Build and run your app

## Features Implemented

- **Login**: Users can sign in with email and password
- **Sign Up**: New users can create an account
- **Logout**: Users can sign out
- **Authentication State**: UI updates based on login status
- **Error Handling**: Displays error messages for failed authentication

## Security Notes

- Make sure to add `google-services.json` to your `.gitignore` if sharing code publicly
- Consider implementing password strength requirements
- Add email verification for production apps
- Implement password reset functionality as needed

## Test the App

1. Launch the app
2. Enter an email and password
3. Click "Sign Up" to create a new account
4. The app will display "Logged in as: [email]"
5. Click "Logout" to sign out
6. Use "Login" to sign in with existing credentials
