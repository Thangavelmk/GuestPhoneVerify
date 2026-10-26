# Guest Phone Verify — Android Studio starter project

A small Android app for verifying that a guest can access a mobile number. It does **not** ask for a guest name and stores only the last successfully verified phone number locally.

## Important distinction
Phone OTP verification confirms access to a phone number. It does not prove the person's identity or confirm a hotel reservation. To confirm a booking, the verified number must be compared with the hotel's booking records.

## Setup before running
This project uses Firebase Phone Authentication for real OTP delivery and verification. SMS/WhatsApp OTP cannot be safely verified using only local storage; this project uses Firebase's supported phone verification flow (SMS). WhatsApp OTP is not included in this starter.

1. Install Android Studio and open this `GuestPhoneVerify` folder.
2. In the Firebase Console, create a project and add an Android app with package name `com.example.guestphoneverify`.
3. Enable **Authentication → Sign-in method → Phone**.
4. Download `google-services.json` and place it in the `app/` folder.
5. Add your app's SHA-1 and SHA-256 fingerprints in Firebase project settings as required by Firebase Phone Auth.
6. Sync Gradle and run on a real device with internet. Firebase test phone numbers can be configured in the console to test without sending real SMS.

## Notes
- Android Studio/Gradle may prompt to install a compatible Android SDK (compile SDK 35) and use JDK 17.
- Real SMS delivery is subject to Firebase limits, project configuration, and provider policies/costs.
- No guest name, booking ID, room number, or booking data is collected.
- The app uses Firebase Authentication to verify OTP and Android SharedPreferences to save the last verified phone number locally.
