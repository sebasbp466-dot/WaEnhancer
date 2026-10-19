#!/usr/bin/env bash
set -e
NEW=com.wamodx.app
J=app/src/main/java/com/wmods/wppenhacer
sed -i "s/applicationId = \"com.wmods.wppenhacer\"/applicationId = \"$NEW\"/" app/build.gradle*
sed -i "s/== \"com.wmods.wppenhacer\"/== \"$NEW\"/" $J/App.kt
sed -i "s/\"com.wmods.wppenhacer\"/\"$NEW\"/; s/\"com.wmods.wppenhacer.w4b\"/\"$NEW.w4b\"/" $J/utils/RootDiagnostics.kt
sed -i "s/\"com\.wmods\.wppenhacer\.\(MESSAGE_SENT\|MESSAGE_RECEIVED\|EVENT\)\"/\"$NEW.\1\"/" $J/xposed/features/general/Tasker.kt
sed -i 's/Wa Enhancer/WA ModX/g' app/src/main/res/values*/strings.xml
sed -i 's/"WAE Enhancer"/"WA ModX"/' $J/xposed/utils/Utils.kt
sed -i 's#Dev4Mod/WaEnhancer/releases#sebasbp466-dot/WaEnhancer/releases#' $J/UpdateChecker.kt $J/ui/fragments/HomeFragment.kt
sed -i 's#github.com/Dev4Mod/WaEnhancer#github.com/sebasbp466-dot/WaEnhancer#' $J/activities/AboutActivity.kt
