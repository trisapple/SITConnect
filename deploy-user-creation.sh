#!/bin/bash

# SIT Connect - Deploy User Creation Function
# This script deploys the createUser Cloud Function to Firebase

echo "================================================"
echo "   SIT Connect - Deploy User Creation Feature"
echo "================================================"
echo ""

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null
then
    echo "❌ Firebase CLI is not installed!"
    echo ""
    echo "Install it with:"
    echo "  npm install -g firebase-tools"
    echo ""
    exit 1
fi

echo "✅ Firebase CLI found"
echo ""

# Navigate to project directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "📂 Project directory: $SCRIPT_DIR"
echo ""

# Check if functions directory exists
if [ ! -d "functions" ]; then
    echo "❌ Functions directory not found!"
    exit 1
fi

echo "✅ Functions directory found"
echo ""

# Install dependencies if needed
echo "📦 Checking dependencies..."
cd functions
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
else
    echo "✅ Dependencies already installed"
fi
cd ..
echo ""

# Check if user is logged in
echo "🔐 Checking Firebase authentication..."
firebase projects:list > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "❌ Not logged in to Firebase!"
    echo ""
    echo "Please login first:"
    echo "  firebase login"
    echo ""
    exit 1
fi

echo "✅ Logged in to Firebase"
echo ""

# Deploy the function
echo "🚀 Deploying createUser function..."
echo ""
firebase deploy --only functions:createUser

# Check deployment status
if [ $? -eq 0 ]; then
    echo ""
    echo "================================================"
    echo "✅ Deployment Successful!"
    echo "================================================"
    echo ""
    echo "The createUser function is now live and ready to use."
    echo ""
    echo "Next steps:"
    echo "1. Build and run the Android app"
    echo "2. Login as admin"
    echo "3. Navigate to User Management"
    echo "4. Tap the + button to create users"
    echo ""
    echo "Need help? Check:"
    echo "  - USER_CREATION_FEATURE.md"
    echo "  - QUICK_START_USER_CREATION.md"
    echo ""
else
    echo ""
    echo "================================================"
    echo "❌ Deployment Failed!"
    echo "================================================"
    echo ""
    echo "Please check the error messages above."
    echo ""
    echo "Common issues:"
    echo "  - Not logged in: firebase login"
    echo "  - Wrong project: firebase use <project-id>"
    echo "  - Network issues: Check internet connection"
    echo ""
    exit 1
fi

