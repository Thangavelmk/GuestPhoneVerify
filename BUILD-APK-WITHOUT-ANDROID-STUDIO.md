# Build APK without installing Android Studio

This project includes a GitHub Actions workflow that builds a debug APK in the cloud.

## Steps
1. Sign in to GitHub and create a new **private** repository.
2. Upload the contents of this folder (including the hidden `.github` folder) to the repository. Keep the folder contents at the repository root; do not upload the outer `GuestPhoneVerify` folder as an extra nested level.
3. In GitHub, open **Actions** and enable workflows if prompted.
4. Select **Build Guest Phone Verify APK** and choose **Run workflow** (or push to the `main` branch).
5. Open the completed workflow run and download the `GuestPhoneVerify-debug-apk` artifact.
6. Extract the downloaded artifact ZIP; it contains the APK. Transfer the APK to your Android phone and install it.

## Real OTP requirement
A build without `app/google-services.json` is only a build test. To use real SMS OTP, create/configure a Firebase project, enable Phone Authentication, and add your Firebase `google-services.json` to `app/` before running the workflow. The app verifies access to the phone number; it does not check hotel reservation records.
