FROM eclipse-temurin:17-jdk AS build
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
 && curl -fsSL -o /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
 && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/tools.zip
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platform-tools" "platforms;android-35" "build-tools;34.0.0"
RUN curl -fL --retry 10 --retry-delay 5 --retry-all-errors -C - -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && rm /tmp/gradle.zip
ENV PATH=$PATH:/opt/gradle-8.9/bin
ENV GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=180000 -Dorg.gradle.internal.http.socketTimeout=180000"
WORKDIR /project
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle gradle assembleDebug --no-daemon

FROM scratch AS apk
COPY --from=build /project/app/build/outputs/apk/debug/app-debug.apk /woodlands-debug.apk

FROM alpine:3.20 AS export
COPY --from=build /project/app/build/outputs/apk/debug/app-debug.apk /woodlands-debug.apk
CMD ["cp", "/woodlands-debug.apk", "/out/woodlands-debug.apk"]