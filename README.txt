PvPMacro Gradle launcher files

Copy the gradlew file, gradlew.bat file, and gradle/ folder into the ROOT of your PvPMacro repository, next to build.gradle and settings.gradle.

On macOS/Linux, run: chmod +x gradlew
Then commit the files and push them to GitHub.

This lightweight launcher downloads Gradle 9.2.1 on first run. It does not include the standard gradle-wrapper.jar.
