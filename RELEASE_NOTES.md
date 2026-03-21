# CoolBox Mobile Release Notes

## Version: V1.2-Pre41 (Build 137)
**Date**: 2026-03-21

### 🚀 New Features & Enhancements
- **Expired Items Pinning**: Expired items are now permanently pinned to the top of the inventory list, regardless of the active sorting mode (Name, Location, Expiry) or order (Ascending/Descending).
- **Recurring Safety Alert**: The launch alert dialog now re-triggers every time the app is opened or resumed from the background, ensuring critical expiry warnings are never missed.
- **Search Support for Remarks**: You can now search for items by keywords in the **Remark** (备注) field, alongside Name and Location.

### 🎨 UI & UX Improvements
- **Visual Color Update**: Expired items background color updated to a high-contrast dark red (**`#A20000`**).
- **Bolder Typography**: 
  - Substantially increased the font weight of the **App Name**, **Food Name**, and **Total Quantity** to **Black** for maximum clarity.
  - Significantly increased the font weight and size of the **Expiry Alert Dialog** text.
- **Responsive Font Scaling**: All UI elements, including the alert dialog, now correctly respect the user-defined **Global Font Scale**.
- **Version Label**: Added the current version string (**V1.2-Pre41**) to the top bar for easy reference.
- **Clean Flat Design**: Removed icon shadows based on user feedback to maintain a crisp, modern aesthetic.

### 🔧 Improvements & Bug Fixes
- Fixed the race condition in the launch alert trigger to ensure reliable display after data synchronization.
- Improved the consistency of "Near Expiry" logic (Last 25% of shelf life OR within 3 days).
- Updated the build configuration to increment version codes.
