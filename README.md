# Firebase Chat Application

A real-time chat application built with Android and Firebase that supports text messages, image sharing, and message deletion.

## Features

1. **Text Messaging**
   - Real-time text message delivery
   - User authentication with Google Sign-In
   - Message sender identification with profile pictures

2. **Image Sharing**
   - Send images from device gallery
   - Support for sending both text and image together
   - Loading indicator during image upload
   - Automatic image resizing and optimization

3. **Message Management**
   - Delete messages with long-press
   - Confirmation dialog before deletion
   - Real-time updates across all devices

## Bug Fix: Image Selection and RecyclerView Crash

### Problem
The application would crash when selecting an image from the gallery due to multiple issues:
1. RecyclerView adapter not properly handling image loading states
2. Missing permission check and improper URI handling
3. Race conditions in the RecyclerView when updating with new images

### Solution
The following fixes were implemented:

1. **RecyclerView Improvements**
   - Disabled item animations to prevent crashes during updates
   ```java
   mMessageRecyclerView.setItemAnimator(null);
   ```
   - Added proper null checks in the adapter's onBindViewHolder
   - Implemented proper visibility handling for text and image views
   - Added loading state handling in the RecyclerView

2. **Storage Permission and Image Selection**
   - Added `READ_EXTERNAL_STORAGE` permission in AndroidManifest.xml
   ```xml
   <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
   ```
   - Modified the image selection intent to use `ACTION_OPEN_DOCUMENT`
   ```java
   Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
   intent.addCategory(Intent.CATEGORY_OPENABLE);
   intent.setType("image/*");
   ```

3. **Enhanced Error Handling**
   - Added proper null checks for URI
   - Implemented error logging
   - Added user feedback through Toast messages
   - Added proper error handling in RecyclerView adapter

4. **Improved Image Upload Process**
   - Created a temporary message with loading state
   - Implemented proper progress tracking
   - Added success/failure handlers
   - Properly handled RecyclerView updates during image upload

5. **RecyclerView State Management**
   - Added proper state handling for loading images
   - Implemented smooth scrolling to new messages
   - Added proper handling of view recycling
   ```java
   mLinearLayoutManager.setStackFromEnd(true);
   mLinearLayoutManager.setItemPrefetchEnabled(false);
   ```

## Technical Implementation

### Message Deletion (Task 1)
- Implemented using Firebase Realtime Database
- Long-press listener on message items
- Confirmation dialog before deletion
- Real-time updates across all clients

### Text and Image Together (Task 2)
- Modified message structure to support both text and image
- Implemented proper UI handling for combined messages
- Added loading states during image upload
- Optimized image loading using Glide library

## Dependencies

- Firebase Authentication
- Firebase Realtime Database
- Firebase Storage
- Glide (for image loading)
- Firebase UI Database
- RecyclerView 1.4.0

## Setup

1. Clone the repository
2. Add your Firebase configuration file (`google-services.json`)
3. Build and run the application

## Requirements

- Android Studio
- Minimum SDK: API 21 (Android 5.0)
- Google Play Services
- Firebase Account 